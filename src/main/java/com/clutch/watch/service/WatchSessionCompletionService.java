package com.clutch.watch.service;

import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.domain.WatchSessionStatus;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.session.WatchSessionSnapshot;
import com.clutch.watch.repository.WatchSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 미수령 보상을 지급하지 않고 시청 세션을 종료한다.
 */
@Service
@RequiredArgsConstructor
public class WatchSessionCompletionService {

    private final WatchSessionRepository watchSessionRepository;

    /**
     * Redis의 마지막 시청 상태만 DB 세션에 반영하고 포인트는 지급하지 않는다.
     * 미수령 보상과 부분 누적시간이 세션 종료 시 자동 지급되지 않도록 사용한다.
     *
     * @param snapshot 종료할 Redis 시청 세션 상태
     */
    @Transactional
    public void completeWithoutReward(WatchSessionSnapshot snapshot) {
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
