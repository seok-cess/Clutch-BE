package com.clutch.watch.service;

import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.dto.WatchPointAwardResult;
import com.clutch.watch.dto.WatchPointClaimResult;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.reward.RewardClaimCompletionResult;
import com.clutch.watch.redis.reward.RewardClaimCompletionStatus;
import com.clutch.watch.redis.session.WatchSessionRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Redis 수령 자격과 DB 포인트 지급을 연결하여 한 회차의 수령을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class WatchPointClaimService {

    private final WatchSessionRedisRepository watchSessionRedisRepository;
    private final WatchPointAwardService pointAwardService;
    private final WatchRewardProperties properties;

    /**
     * 한 사용자의 세션 전환과 포인트 수령이 동시에 처리되지 않도록 lock을 획득한 뒤 포인트를 수령한다.
     * 성공 여부와 관계없이 현재 요청이 소유한 lock만 해제한다.
     *
     * @param userId 포인트를 수령할 사용자 ID
     * @param sessionKey 포인트 수령 대상 시청 세션 외부 식별자
     * @param rewardSequence 수령할 포인트 회차
     * @return 지급 포인트, 지급 후 총포인트와 다음 수령 회차
     * @throws WatchException 세션 전환 중이거나 수령 조건을 충족하지 못한 경우
     */
    public WatchPointClaimResult claim(long userId, String sessionKey, long rewardSequence) {
        String lockToken = UUID.randomUUID().toString();
        if (!watchSessionRedisRepository.tryAcquireSwitchLock(userId, lockToken)) {
            throw new WatchException(WatchError.WATCH_SESSION_SWITCHING);
        }

        try {
            return claimWithinLock(userId, sessionKey, rewardSequence);
        } finally {
            watchSessionRedisRepository.releaseSwitchLock(userId, lockToken);
        }
    }

    /**
     * Redis에서 수령 자격을 확인하고, DB 지급을 확정한 뒤 Redis를 다음 회차로 전환한다.
     * 이미 처리된 회차의 재요청이면 기존 DB 거래를 조회하여 동일한 결과를 반환한다.
     *
     * @param userId 포인트를 수령할 사용자 ID
     * @param sessionKey 포인트 수령 대상 시청 세션 외부 식별자
     * @param rewardSequence 수령할 포인트 회차
     * @return DB 지급 결과와 Redis의 다음 수령 회차
     */
    private WatchPointClaimResult claimWithinLock(
            long userId,
            String sessionKey,
            long rewardSequence
    ) {
        RewardClaimCompletionStatus eligibility = watchSessionRedisRepository.prepareRewardClaim(
                userId,
                sessionKey,
                rewardSequence
        );
        WatchPointAwardResult award = awardOrRestore(
                eligibility,
                userId,
                sessionKey,
                rewardSequence
        );
        RewardClaimCompletionResult completion = completeClaim(userId, sessionKey, rewardSequence);

        return new WatchPointClaimResult(
                award.rewardSequence(),
                award.awardedPoint(),
                award.totalPoint(),
                completion.nextRewardSequence()
        );
    }

    /**
     * Redis 자격 상태에 따라 새 포인트를 지급하거나 기존 지급 결과를 복원한다.
     *
     * @param eligibility Redis 수령 자격 상태
     * @param userId 포인트를 수령할 사용자 ID
     * @param sessionKey 시청 세션 외부 식별자
     * @param rewardSequence 수령할 포인트 회차
     * @return 새로 지급하거나 DB에서 복원한 지급 결과
     */
    private WatchPointAwardResult awardOrRestore(
            RewardClaimCompletionStatus eligibility,
            long userId,
            String sessionKey,
            long rewardSequence
    ) {
        return switch (eligibility) {
            case SUCCESS -> pointAwardService.award(
                    userId,
                    sessionKey,
                    rewardSequence,
                    properties.pointsPerClaim()
            );
            case INVALID_REWARD_SEQUENCE -> findExistingOrReject(
                    userId,
                    sessionKey,
                    rewardSequence
            );
            default -> throw new WatchException(toError(eligibility));
        };
    }

    /**
     * DB 지급이 끝난 회차를 Redis에 반영하고 다음 수령 회차를 시작한다.
     *
     * @param userId 포인트를 수령한 사용자 ID
     * @param sessionKey 시청 세션 외부 식별자
     * @param rewardSequence 완료한 포인트 회차
     * @return 다음 포인트 회차 정보
     */
    private RewardClaimCompletionResult completeClaim(
            long userId,
            String sessionKey,
            long rewardSequence
    ) {
        RewardClaimCompletionResult completion = watchSessionRedisRepository.completeRewardClaim(
                userId,
                sessionKey,
                rewardSequence,
                Instant.now().toEpochMilli()
        );
        if (completion.status() != RewardClaimCompletionStatus.SUCCESS
                && completion.status() != RewardClaimCompletionStatus.ALREADY_COMPLETED) {
            throw new WatchException(WatchError.REWARD_CLAIM_COMPLETION_FAILED);
        }
        return completion;
    }

    /**
     * Redis 회차가 이미 변경된 재요청인지 DB 거래 내역으로 확인한다.
     * 해당 회차의 거래도 없다면 정상 재요청이 아니므로 회차 불일치 오류로 변환한다.
     *
     * @param userId 포인트를 수령한 사용자 ID
     * @param sessionKey 포인트 수령 대상 시청 세션 외부 식별자
     * @param rewardSequence 확인할 포인트 회차
     * @return DB에 저장된 기존 지급 결과
     * @throws WatchException 기존 지급 거래가 없거나 세션 및 사용자 검증에 실패한 경우
     */
    private WatchPointAwardResult findExistingOrReject(
            long userId,
            String sessionKey,
            long rewardSequence
    ) {
        try {
            return pointAwardService.findExisting(userId, sessionKey, rewardSequence);
        } catch (WatchException exception) {
            if (exception.getError() == WatchError.POINT_TRANSACTION_NOT_FOUND) {
                throw new WatchException(WatchError.REWARD_SEQUENCE_MISMATCH);
            }
            throw exception;
        }
    }

    /**
     * Redis 수령 처리 실패 상태를 API에서 사용하는 시청 도메인 오류로 변환한다.
     * 성공 상태는 오류로 변환할 수 없으므로 호출 흐름 오류로 처리한다.
     *
     * @param status Redis 포인트 수령 처리 상태
     * @return 처리 상태에 대응하는 시청 도메인 오류
     * @throws WatchException 성공 상태를 오류로 변환하려는 경우
     */
    private WatchError toError(RewardClaimCompletionStatus status) {
        return switch (status) {
            case REPLACED -> WatchError.WATCH_SESSION_REPLACED;
            case EXPIRED -> WatchError.WATCH_SESSION_EXPIRED;
            case SESSION_NOT_FOUND -> WatchError.WATCH_SESSION_NOT_FOUND;
            case USER_MISMATCH -> WatchError.WATCH_SESSION_USER_MISMATCH;
            case INVALID_REWARD_SEQUENCE, ALREADY_COMPLETED -> WatchError.REWARD_SEQUENCE_MISMATCH;
            case NOT_CLAIMABLE -> WatchError.REWARD_NOT_CLAIMABLE;
            case SUCCESS -> throw new WatchException(WatchError.REWARD_CLAIM_RESULT_UNKNOWN);
        };
    }
}
