package com.clutch.watch.service;

import com.clutch.user.domain.User;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.domain.WatchPointTransaction;
import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.domain.WatchSessionStatus;
import com.clutch.watch.redis.WatchSessionSnapshot;
import com.clutch.watch.repository.WatchPointTransactionRepository;
import com.clutch.watch.repository.WatchSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Redis에서 확정한 시청시간을 DB 포인트와 거래 내역으로 정산한다.
 */
@Service
@RequiredArgsConstructor
public class WatchRewardService {

    private static final long MILLISECONDS_PER_MINUTE = 60_000L;

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
     * @throws NullPointerException snapshot이 null인 경우
     * @throws IllegalArgumentException 유효 시청시간이 음수이거나, DB 세션이 없거나,
     *                                  snapshot 식별자가 DB 세션과 다른 경우
     * @throws IllegalStateException 완료 세션의 기존 거래를 찾을 수 없는 경우
     * @throws ArithmeticException 지급 포인트 계산 중 long 범위를 초과하는 경우
     */
    @Transactional
    public WatchRewardResult settle(WatchSessionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "정산할 Redis 시청 세션은 필수입니다.");
        if (snapshot.eligibleMilliseconds() < 0) {
            throw new IllegalArgumentException("유효 시청시간은 음수일 수 없습니다.");
        }

        WatchSession watchSession = watchSessionRepository
                .findBySessionKey(snapshot.sessionKey())
                .orElseThrow(() -> new IllegalArgumentException("시청 세션을 찾을 수 없습니다."));

        validateSnapshotOwner(watchSession, snapshot);

        if (watchSession.getStatus() == WatchSessionStatus.COMPLETED) {
            return existingSettlement(watchSession);
        }

        long awardedMinutes = snapshot.eligibleMilliseconds() / MILLISECONDS_PER_MINUTE;
        long awardedPoint = Math.multiplyExact(awardedMinutes, properties.pointsPerMinute());

        User user = userRepository.findById(watchSession.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        user.changePoint(awardedPoint);
        watchSession.complete(toUtcLocalDateTime(snapshot.lastSeen()), snapshot.eligibleMilliseconds());

        WatchPointTransaction transaction = WatchPointTransaction.create(
                watchSession.getUserId(),
                watchSession.getId(),
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
     * Redis snapshot이 DB 세션과 같은 사용자 및 경기를 가리키는지 검증한다.
     *
     * @param watchSession DB에서 조회한 시청 세션
     * @param snapshot Redis에서 조회한 시청 세션 상태
     * @throws IllegalArgumentException 사용자 또는 경기 식별자가 일치하지 않는 경우
     */
    private void validateSnapshotOwner(WatchSession watchSession, WatchSessionSnapshot snapshot) {
        if (!watchSession.getUserId().equals(snapshot.userId())) {
            throw new IllegalArgumentException("Redis 세션의 사용자 ID가 DB 세션과 일치하지 않습니다.");
        }
        if (!watchSession.getEsportsMatchId().equals(snapshot.matchId())) {
            throw new IllegalArgumentException("Redis 세션의 경기 ID가 DB 세션과 일치하지 않습니다.");
        }
    }

    /**
     * 이미 완료된 세션에 저장된 포인트 거래를 조회하여 기존 정산 결과를 반환한다.
     *
     * @param watchSession 이미 완료된 DB 시청 세션
     * @return 기존 정산 정보를 담은 결과
     * @throws IllegalStateException 완료 세션에 연결된 포인트 거래가 없는 경우
     */
    private WatchRewardResult existingSettlement(WatchSession watchSession) {
        WatchPointTransaction transaction = watchPointTransactionRepository
                .findByWatchSessionId(watchSession.getId())
                .orElseThrow(() -> new IllegalStateException("완료된 시청 세션의 포인트 거래를 찾을 수 없습니다."));

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
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilliseconds), ZoneOffset.UTC);
    }
}
