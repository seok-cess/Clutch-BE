package com.clutch.watch.service.service;

import com.clutch.user.domain.User;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.domain.WatchPointTransaction;
import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.domain.WatchSessionStatus;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.WatchSessionSnapshot;
import com.clutch.watch.repository.WatchPointTransactionRepository;
import com.clutch.watch.repository.WatchSessionRepository;
import com.clutch.watch.service.dto.WatchRewardResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Redis에서 확정한 시청시간을 DB 포인트와 거래 내역으로 정산한다.
 */
@Service
@RequiredArgsConstructor
public class WatchRewardService {

    private static final long MILLISECONDS_PER_MINUTE = 60_000L;
    private static final long LEGACY_SETTLEMENT_SEQUENCE = 1L;

    private final WatchSessionRepository watchSessionRepository;
    private final WatchPointTransactionRepository watchPointTransactionRepository;
    private final UserRepository userRepository;
    private final WatchRewardProperties properties;

    /**
     * Redis 시청 세션 snapshot을 기준으로 사용자 포인트를 한 번 정산한다.
     * 사용자 포인트 변경, 포인트 거래 저장, 세션 완료 처리는 하나의 DB 트랜잭션으로 처리한다.
     *
     * @param snapshot Redis에서 조회한 최종 시청 세션 상태
     * @return 신규 정산 여부와 시청시간 및 지급 포인트를 담은 결과
     * @throws WatchException snapshot 또는 시청 세션이 없거나, Redis와 DB 상태가 다르거나,
     *                        포인트 계산 범위를 초과하는 경우
     */
    @Transactional
    public WatchRewardResult settle(WatchSessionSnapshot snapshot) {
        if (snapshot == null) {
            throw new WatchException(WatchError.REWARD_SNAPSHOT_REQUIRED);
        }
        if (snapshot.eligibleMilliseconds() < 0) {
            throw new WatchException(WatchError.ELIGIBLE_TIME_NEGATIVE);
        }

        WatchSession watchSession = watchSessionRepository
                .findBySessionKey(snapshot.sessionKey())
                .orElseThrow(() -> new WatchException(WatchError.WATCH_SESSION_NOT_FOUND));

        validateSnapshotOwner(watchSession, snapshot);

        if (watchSession.getStatus() == WatchSessionStatus.COMPLETED) {
            return existingSettlement(watchSession);
        }

        long awardedMinutes = snapshot.eligibleMilliseconds() / MILLISECONDS_PER_MINUTE;
        long awardedPoint;
        try {
            awardedPoint = Math.multiplyExact(awardedMinutes, properties.pointsPerMinute());
        } catch (ArithmeticException exception) {
            throw new WatchException(WatchError.REWARD_POINT_OVERFLOW, exception);
        }

        User user = userRepository.findById(watchSession.getUserId())
                .orElseThrow(() -> new WatchException(WatchError.USER_NOT_FOUND));

        try {
            user.changePoint(awardedPoint);
        } catch (ArithmeticException exception) {
            throw new WatchException(WatchError.USER_POINT_OVERFLOW, exception);
        }
        watchSession.complete(toUtcLocalDateTime(snapshot.lastSeen()), snapshot.eligibleMilliseconds());

        WatchPointTransaction transaction = WatchPointTransaction.create(
                watchSession.getUserId(),
                watchSession.getId(),
                LEGACY_SETTLEMENT_SEQUENCE,
                watchSession.getEsportsMatchId(),
                awardedPoint
        );
        watchPointTransactionRepository.save(transaction);

        return new WatchRewardResult(
                watchSession.getSessionKey(),
                snapshot.eligibleMilliseconds(),
                awardedMinutes,
                awardedPoint,
                true
        );
    }

    /**
     * Redis의 마지막 시청 상태만 DB 세션에 반영하고 포인트는 지급하지 않는다.
     * 미수령 보상과 부분 누적시간이 세션 종료 시 자동 지급되지 않도록 사용한다.
     *
     * @param snapshot 종료할 Redis 시청 세션 상태
     */
    @Transactional
    public void discard(WatchSessionSnapshot snapshot) {
        if (snapshot == null) {
            throw new WatchException(WatchError.REWARD_SNAPSHOT_REQUIRED);
        }
        if (snapshot.eligibleMilliseconds() < 0) {
            throw new WatchException(WatchError.ELIGIBLE_TIME_NEGATIVE);
        }

        WatchSession watchSession = watchSessionRepository
                .findBySessionKey(snapshot.sessionKey())
                .orElseThrow(() -> new WatchException(WatchError.WATCH_SESSION_NOT_FOUND));
        validateSnapshotOwner(watchSession, snapshot);

        if (watchSession.getStatus() == WatchSessionStatus.WATCHING) {
            watchSession.complete(
                    toUtcLocalDateTime(snapshot.lastSeen()),
                    snapshot.eligibleMilliseconds()
            );
        }
    }

    /**
     * Redis snapshot이 DB 세션과 같은 사용자 및 경기를 가리키는지 검증한다.
     *
     * @param watchSession DB에서 조회한 시청 세션
     * @param snapshot Redis에서 조회한 시청 세션 상태
     * @throws WatchException 사용자 또는 경기 식별자가 일치하지 않는 경우
     */
    private void validateSnapshotOwner(WatchSession watchSession, WatchSessionSnapshot snapshot) {
        if (!watchSession.getUserId().equals(snapshot.userId())) {
            throw new WatchException(WatchError.REDIS_SESSION_USER_MISMATCH);
        }
        if (!watchSession.getEsportsMatchId().equals(snapshot.matchId())) {
            throw new WatchException(WatchError.REDIS_SESSION_MATCH_MISMATCH);
        }
    }

    /**
     * 이미 완료된 세션에 저장된 포인트 거래를 조회하여 기존 정산 결과를 반환한다.
     *
     * @param watchSession 이미 완료된 DB 시청 세션
     * @return 기존 정산 정보를 담은 결과
     * @throws WatchException 완료 세션에 연결된 포인트 거래가 없는 경우
     */
    private WatchRewardResult existingSettlement(WatchSession watchSession) {
        WatchPointTransaction transaction = watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(
                        watchSession.getId(),
                        LEGACY_SETTLEMENT_SEQUENCE
                )
                .orElseThrow(() -> new WatchException(WatchError.POINT_TRANSACTION_NOT_FOUND));

        return new WatchRewardResult(
                watchSession.getSessionKey(),
                watchSession.getEligibleMilliseconds(),
                watchSession.getEligibleMilliseconds() / MILLISECONDS_PER_MINUTE,
                transaction.getAwardedPoint(),
                false
        );
    }

    /**
     * Epoch milliseconds 시각을 DB에서 사용하는 UTC LocalDateTime으로 변환한다.
     *
     * @param epochMilliseconds 변환할 epoch milliseconds
     * @return UTC 기준 LocalDateTime
     */
    private LocalDateTime toUtcLocalDateTime(long epochMilliseconds) {
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilliseconds), ZoneOffset.UTC);
        } catch (DateTimeException exception) {
            throw new WatchException(WatchError.REDIS_SESSION_TIME_INVALID, exception);
        }
    }
}
