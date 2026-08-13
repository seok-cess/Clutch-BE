package com.clutch.watch.service.service;

import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.RewardClaimCompletionResult;
import com.clutch.watch.redis.RewardClaimCompletionStatus;
import com.clutch.watch.redis.WatchSessionRedisRepository;
import com.clutch.watch.service.dto.WatchPointClaimResult;
import com.clutch.watch.service.dto.WatchPointClaimTransactionResult;
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
    private final WatchRewardClaimTransactionService transactionService;
    private final WatchRewardProperties properties;

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

    private WatchPointClaimResult claimWithinLock(
            long userId,
            String sessionKey,
            long rewardSequence
    ) {
        RewardClaimCompletionStatus preparation = watchSessionRedisRepository.prepareRewardClaim(
                userId,
                sessionKey,
                rewardSequence
        );

        WatchPointClaimTransactionResult transactionResult;
        if (preparation == RewardClaimCompletionStatus.SUCCESS) {
            transactionResult = transactionService.award(
                    userId,
                    sessionKey,
                    rewardSequence,
                    properties.pointsPerClaim()
            );
        } else if (preparation == RewardClaimCompletionStatus.INVALID_REWARD_SEQUENCE) {
            transactionResult = findExistingOrReject(userId, sessionKey, rewardSequence);
        } else {
            throw new WatchException(toError(preparation));
        }

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

        return new WatchPointClaimResult(
                transactionResult.rewardSequence(),
                transactionResult.awardedPoint(),
                transactionResult.totalPoint(),
                completion.nextRewardSequence()
        );
    }

    private WatchPointClaimTransactionResult findExistingOrReject(
            long userId,
            String sessionKey,
            long rewardSequence
    ) {
        try {
            return transactionService.findExisting(userId, sessionKey, rewardSequence);
        } catch (WatchException exception) {
            if (exception.getError() == WatchError.POINT_TRANSACTION_NOT_FOUND) {
                throw new WatchException(WatchError.REWARD_SEQUENCE_MISMATCH);
            }
            throw exception;
        }
    }

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
