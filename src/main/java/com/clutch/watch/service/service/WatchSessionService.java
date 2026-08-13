package com.clutch.watch.service.service;

import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.HeartbeatProcessingResult;
import com.clutch.watch.redis.HeartbeatResult;
import com.clutch.watch.redis.SessionKeyReplacementResult;
import com.clutch.watch.redis.WatchSessionRedisRepository;
import com.clutch.watch.redis.WatchSessionSnapshot;
import com.clutch.watch.repository.WatchSessionRepository;
import com.clutch.watch.service.dto.WatchHeartbeatResult;
import com.clutch.watch.service.dto.WatchRewardState;
import com.clutch.watch.service.dto.WatchSessionStartResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 경기 입장과 사용자별 활성 시청 세션 전환을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class WatchSessionService {

    private static final String WATCHABLE_MATCH_STATUS = "inProgress";

    private final UserRepository userRepository;
    private final EsportsMatchRepository esportsMatchRepository;
    private final WatchSessionRepository watchSessionRepository;
    private final WatchSessionRedisRepository watchSessionRedisRepository;
    private final WatchRewardService watchRewardService;
    private final WatchRewardProperties properties;

    /**
     * 사용자를 경기에 입장시킨다. 동일 경기에 재입장하면 기존 누적 상태를 유지하면서
     * sessionKey를 교체하고, 다른 경기에 입장하면 기존 세션을 미지급 종료한다.
     *
     * @param userId 경기를 시청할 사용자 ID
     * @param matchId 시청할 경기 ID
     * @return 새 시청 세션과 heartbeat 정책을 담은 결과
     * @throws WatchException 사용자나 경기가 없거나, 경기가 시청 불가능하거나,
     *                               동일 사용자의 세션 전환이 진행 중인 경우
     */
    @Transactional
    public WatchSessionStartResult start(long userId, long matchId) {
        validateUser(userId);
        validateMatch(matchId);

        String lockToken = UUID.randomUUID().toString();
        if (!watchSessionRedisRepository.tryAcquireSwitchLock(userId, lockToken)) {
            throw new WatchException(WatchError.WATCH_SESSION_SWITCHING);
        }

        try {
            return startOrReplaceSession(userId, matchId);
        } finally {
            watchSessionRedisRepository.releaseSwitchLock(userId, lockToken);
        }
    }

    /**
     * 프론트엔드 heartbeat를 서버 수신 시각으로 처리한다.
     * 사용자, 활성 세션, TTL 및 sequence 검증과 시간 누적은 Redis Lua script가 원자적으로 수행한다.
     *
     * @param userId heartbeat를 보낸 사용자 ID
     * @param sessionKey heartbeat 대상 시청 세션 외부 식별자
     * @param sequence 프론트엔드가 이전 값보다 증가시킨 heartbeat 순번
     * @return heartbeat 처리 후 현재 회차의 포인트 수령 상태
     * @throws WatchException Redis 세션을 찾을 수 없거나 heartbeat 검증에 실패한 경우
     */
    @Transactional(readOnly = true)
    public WatchHeartbeatResult heartbeat(long userId, String sessionKey, long sequence) {
        String activeSessionKey = watchSessionRedisRepository.findActiveSessionKey(userId)
                .orElse(null);
        if (activeSessionKey != null && !activeSessionKey.equals(sessionKey)) {
            throw new WatchException(WatchError.WATCH_SESSION_REPLACED);
        }

        WatchSessionSnapshot snapshot = watchSessionRedisRepository.findSession(sessionKey)
                .orElseThrow(() -> new WatchException(WatchError.WATCH_SESSION_NOT_FOUND));

        HeartbeatProcessingResult result = watchSessionRedisRepository.heartbeat(
                userId,
                sessionKey,
                sequence,
                Instant.now().toEpochMilli()
        );
        if (result.status() != HeartbeatResult.SUCCESS) {
            throw new WatchException(toError(result.status()));
        }
        return toHeartbeatResult(result);
    }

    private WatchHeartbeatResult toHeartbeatResult(HeartbeatProcessingResult result) {
        long claimIntervalMillis = properties.claimInterval().toMillis();
        long eligibleMilliseconds = Math.min(
                result.eligibleMilliseconds(),
                claimIntervalMillis
        );
        long remainingMilliseconds = claimIntervalMillis - eligibleMilliseconds;
        WatchRewardState rewardState = remainingMilliseconds == 0L
                ? WatchRewardState.CLAIMABLE
                : WatchRewardState.ACCUMULATING;

        return new WatchHeartbeatResult(
                rewardState,
                result.rewardSequence(),
                eligibleMilliseconds / 1_000L,
                ceilSeconds(remainingMilliseconds),
                properties.pointsPerClaim()
        );
    }

    private long ceilSeconds(long milliseconds) {
        return milliseconds == 0L ? 0L : ((milliseconds - 1L) / 1_000L) + 1L;
    }

    private WatchError toError(HeartbeatResult result) {
        return switch (result) {
            case SWITCHING -> WatchError.WATCH_SESSION_SWITCHING;
            case REPLACED -> WatchError.WATCH_SESSION_REPLACED;
            case EXPIRED -> WatchError.WATCH_SESSION_EXPIRED;
            case SESSION_NOT_FOUND -> WatchError.WATCH_SESSION_NOT_FOUND;
            case USER_MISMATCH -> WatchError.WATCH_SESSION_USER_MISMATCH;
            case INVALID_SEQUENCE -> WatchError.INVALID_HEARTBEAT_SEQUENCE;
            case SUCCESS -> throw new WatchException(WatchError.HEARTBEAT_SUCCESS_MAPPING);
        };
    }

    /**
     * 시청 세션을 시작할 사용자가 존재하는지 검증한다.
     *
     * @param userId 검증할 사용자 ID
     * @throws WatchException 사용자가 존재하지 않는 경우
     */
    private void validateUser(long userId) {
        if (!userRepository.existsById(userId)) {
            throw new WatchException(WatchError.USER_NOT_FOUND);
        }
    }

    /**
     * 경기가 존재하고 현재 시청 가능한 진행 상태인지 검증한다.
     *
     * @param matchId 검증할 경기 ID
     * @throws WatchException 경기가 없거나 진행 중 상태가 아닌 경우
     */
    private void validateMatch(long matchId) {
        EsportsMatch esportsMatch = esportsMatchRepository.findById(matchId)
                .orElseThrow(() -> new WatchException(WatchError.MATCH_NOT_FOUND));
        if (!WATCHABLE_MATCH_STATUS.equalsIgnoreCase(esportsMatch.getLifecycleStatus())) {
            throw new WatchException(WatchError.MATCH_NOT_WATCHABLE);
        }
    }

    private WatchSessionStartResult startOrReplaceSession(long userId, long matchId) {
        String activeSessionKey = watchSessionRedisRepository.findActiveSessionKey(userId)
                .orElse(null);
        if (activeSessionKey == null) {
            return createSession(userId, matchId);
        }

        WatchSessionSnapshot snapshot = watchSessionRedisRepository.findSession(activeSessionKey)
                .orElseThrow(() -> new WatchException(WatchError.WATCH_SESSION_STATE_MISSING));
        if (snapshot.matchId() == matchId) {
            return resumeSameMatch(userId, snapshot);
        }

        discardSession(snapshot);
        return createSession(userId, matchId);
    }

    /**
     * 동일 경기 누적 상태를 새 sessionKey로 옮겨 최신 입장 화면만 활성화한다.
     */
    private WatchSessionStartResult resumeSameMatch(long userId, WatchSessionSnapshot snapshot) {
        String newSessionKey = UUID.randomUUID().toString();
        SessionKeyReplacementResult replacement = watchSessionRedisRepository.replaceSessionKey(
                userId,
                snapshot.sessionKey(),
                newSessionKey
        );
        if (replacement == SessionKeyReplacementResult.EXPIRED) {
            discardSession(snapshot);
            return createSession(userId, snapshot.matchId());
        }
        if (replacement != SessionKeyReplacementResult.SUCCESS) {
            throw new WatchException(WatchError.SESSION_KEY_REPLACEMENT_FAILED);
        }

        try {
            WatchSession watchSession = watchSessionRepository
                    .findBySessionKey(snapshot.sessionKey())
                    .orElseThrow(() -> new WatchException(WatchError.WATCH_SESSION_NOT_FOUND));
            watchSession.replaceSessionKey(newSessionKey);
            watchSessionRepository.flush();
        } catch (RuntimeException exception) {
            watchSessionRedisRepository.replaceSessionKey(
                    userId,
                    newSessionKey,
                    snapshot.sessionKey()
            );
            throw exception;
        }

        return startResult(
                newSessionKey,
                snapshot.matchId(),
                Instant.ofEpochMilli(snapshot.enteredAt()),
                snapshot.sequence(),
                false
        );
    }

    /**
     * 기존 세션을 포인트 지급 없이 완료하고 Redis 활성 상태를 정리한다.
     */
    private void discardSession(WatchSessionSnapshot snapshot) {
        watchRewardService.discard(snapshot);
        watchSessionRedisRepository.deleteActiveIfMatches(snapshot.userId(), snapshot.sessionKey());
        watchSessionRedisRepository.deleteAlive(snapshot.userId(), snapshot.sessionKey());
        watchSessionRedisRepository.deleteSession(snapshot.sessionKey());
    }

    /**
     * DB와 Redis에 신규 시청 세션을 생성한다.
     *
     * @param userId 시청 사용자 ID
     * @param matchId 시청 경기 ID
     * @return 생성된 세션과 heartbeat 정책을 담은 결과
     */
    private WatchSessionStartResult createSession(long userId, long matchId) {
        String sessionKey = UUID.randomUUID().toString();
        Instant enteredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        WatchSession watchSession = WatchSession.start(
                sessionKey,
                userId,
                matchId,
                LocalDateTime.ofInstant(enteredAt, ZoneOffset.UTC)
        );
        watchSessionRepository.save(watchSession);
        watchSessionRedisRepository.initialize(userId, matchId, sessionKey, enteredAt.toEpochMilli());

        return startResult(
                sessionKey,
                matchId,
                enteredAt,
                0L,
                true
        );
    }

    private WatchSessionStartResult startResult(
            String sessionKey,
            long matchId,
            Instant enteredAt,
            long heartbeatSequence,
            boolean newlyCreated
    ) {
        return new WatchSessionStartResult(
                sessionKey,
                matchId,
                enteredAt,
                properties.heartbeatInterval().toSeconds(),
                properties.aliveTtl().toSeconds(),
                heartbeatSequence,
                newlyCreated
        );
    }
}
