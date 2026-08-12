package com.clutch.watch.service;

import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.HeartbeatResult;
import com.clutch.watch.redis.WatchSessionRedisRepository;
import com.clutch.watch.redis.WatchSessionSnapshot;
import com.clutch.watch.repository.WatchSessionRepository;
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
     * 사용자를 경기에 입장시키고 새 시청 세션을 활성 세션으로 지정한다.
     * 기존 활성 세션이 있으면 Redis에 확정된 시청시간을 먼저 포인트로 지급한다.
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
            rewardExistingSession(userId);
            return createSession(userId, matchId);
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
     * @return Redis에서 처리한 heartbeat 결과
     * @throws WatchException Redis 세션의 경기가 없거나 현재 시청 가능한 상태가 아닌 경우
     */
    @Transactional(readOnly = true)
    public HeartbeatResult heartbeat(long userId, String sessionKey, long sequence) {
        WatchSessionSnapshot snapshot = watchSessionRedisRepository.findSession(sessionKey)
                .orElse(null);
        if (snapshot == null) {
            return HeartbeatResult.SESSION_NOT_FOUND;
        }

        validateMatch(snapshot.matchId());
        return watchSessionRedisRepository.heartbeat(
                userId,
                sessionKey,
                sequence,
                Instant.now().toEpochMilli()
        );
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

    /**
     * 사용자의 기존 활성 세션이 있으면 Redis snapshot을 조회하여 포인트를 지급한다.
     *
     * @param userId 기존 활성 세션을 조회할 사용자 ID
     * @throws WatchException active 키가 가리키는 Redis session Hash가 없는 경우
     */
    private void rewardExistingSession(long userId) {
        watchSessionRedisRepository.findActiveSessionKey(userId).ifPresent(sessionKey -> {
            WatchSessionSnapshot snapshot = watchSessionRedisRepository.findSession(sessionKey)
                    .orElseThrow(() -> new WatchException(WatchError.WATCH_SESSION_STATE_MISSING));
            watchRewardService.settle(snapshot);
        });
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

        return new WatchSessionStartResult(
                sessionKey,
                matchId,
                enteredAt,
                properties.heartbeatInterval().toSeconds(),
                properties.aliveTtl().toSeconds()
        );
    }
}
