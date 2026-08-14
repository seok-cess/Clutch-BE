package com.clutch.watch.redis.session;

import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.heartbeat.HeartbeatProcessingResult;
import com.clutch.watch.redis.reward.RewardClaimCompletionResult;
import com.clutch.watch.redis.reward.RewardClaimCompletionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 시청 세션의 활성 상태와 유효 시청시간을 Redis에서 관리한다.
 */
@Repository
@RequiredArgsConstructor
public class WatchSessionRedisRepository {

    private static final String ALIVE_VALUE = "1";

    private final StringRedisTemplate redisTemplate;
    private final WatchRewardProperties properties;

    /**
     * 신규 세션의 정산 상태를 만든 뒤 해당 사용자의 활성 세션으로 지정한다.
     *
     * @param userId 시청 사용자 ID
     * @param matchId 시청 경기 ID
     * @param sessionKey 시청 세션 외부 식별자
     * @param enteredAt 서버가 확정한 입장 시각(epoch milliseconds)
     */
    public void initialize(long userId, long matchId, String sessionKey, long enteredAt) {
        String sessionRedisKey = redisSessionKey(sessionKey);
        redisTemplate.opsForHash().putAll(sessionRedisKey, Map.of(
                "userId", Long.toString(userId),
                "matchId", Long.toString(matchId),
                "enteredAt", Long.toString(enteredAt),
                "lastSeen", Long.toString(enteredAt),
                "eligibleMilliseconds", "0",
                "sequence", "0",
                "rewardSequence", "1"
        ));
        redisTemplate.expire(sessionRedisKey, properties.sessionTtl());
        redisTemplate.opsForValue().set(
                aliveKey(userId, sessionKey),
                ALIVE_VALUE,
                properties.aliveTtl()
        );
        redisTemplate.opsForValue().set(
                activeKey(userId),
                sessionKey,
                properties.activeTtl()
        );
    }

    /**
     * 사용자의 현재 포인트 적립 가능 세션 키를 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return active 키가 있으면 sessionKey, 없으면 빈 Optional
     */
    public Optional<String> findActiveSessionKey(long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(activeKey(userId)));
    }

    /**
     * 최종 정산에 사용할 Redis 시청 세션 상태를 조회한다.
     *
     * @param sessionKey 조회할 시청 세션 외부 식별자
     * @return session Hash가 있으면 snapshot, 없으면 빈 Optional
     * @throws WatchException session Hash 필드가 없거나 숫자 형식이 올바르지 않은 경우
     */
    public Optional<WatchSessionSnapshot> findSession(String sessionKey) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(redisSessionKey(sessionKey));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(WatchSessionSnapshot.from(sessionKey, fields));
    }

    /**
     * Heartbeat 검증, 시청시간 누적 및 Redis TTL 갱신을 하나의 Lua 스크립트로 처리한다.
     *
     * @param userId heartbeat를 보낸 사용자 ID
     * @param sessionKey heartbeat 대상 시청 세션 외부 식별자
     * @param sequence 프론트엔드가 증가시킨 heartbeat 순번
     * @param nowMillis 서버가 확정한 heartbeat 수신 시각(epoch milliseconds)
     * @return heartbeat 처리 결과
     * @throws WatchException Lua 스크립트 결과가 없거나 정의되지 않은 경우
     */
    public HeartbeatProcessingResult heartbeat(
            long userId,
            String sessionKey,
            long sequence,
            long nowMillis
    ) {
        String result = redisTemplate.execute(
                WatchRedisScripts.HEARTBEAT,
                List.of(
                        switchLockKey(userId),
                        activeKey(userId),
                        aliveKey(userId, sessionKey),
                        redisSessionKey(sessionKey)
                ),
                sessionKey,
                Long.toString(userId),
                Long.toString(sequence),
                Long.toString(nowMillis),
                Long.toString(properties.maxEligibleInterval().toMillis()),
                Long.toString(properties.claimInterval().toMillis()),
                Long.toString(properties.aliveTtl().toMillis()),
                Long.toString(properties.activeTtl().toMillis()),
                Long.toString(properties.sessionTtl().toMillis())
        );
        if (result == null) {
            throw new WatchException(WatchError.HEARTBEAT_RESULT_MISSING);
        }
        return HeartbeatProcessingResult.from(result);
    }

    /**
     * 동일 경기의 Redis 누적 상태를 유지하면서 최신 화면용 sessionKey로 원자적으로 교체한다.
     *
     * @param userId 시청 사용자 ID
     * @param oldSessionKey 이전 화면의 세션 키
     * @param newSessionKey 최신 화면에 발급할 세션 키
     * @return Redis 세션 키 교체 결과
     */
    public SessionKeyReplacementResult replaceSessionKey(
            long userId,
            String oldSessionKey,
            String newSessionKey
    ) {
        String result = redisTemplate.execute(
                WatchRedisScripts.REPLACE_SESSION_KEY,
                List.of(
                        activeKey(userId),
                        redisSessionKey(oldSessionKey),
                        aliveKey(userId, oldSessionKey),
                        redisSessionKey(newSessionKey),
                        aliveKey(userId, newSessionKey)
                ),
                oldSessionKey,
                newSessionKey,
                Long.toString(userId),
                ALIVE_VALUE,
                Long.toString(properties.aliveTtl().toMillis()),
                Long.toString(properties.activeTtl().toMillis()),
                Long.toString(properties.sessionTtl().toMillis())
        );
        if (result == null) {
            throw new WatchException(WatchError.SESSION_KEY_REPLACEMENT_RESULT_MISSING);
        }
        return SessionKeyReplacementResult.from(result);
    }

    /**
     * DB에서 지급이 확정된 회차를 Redis에 반영하고 다음 5분 누적을 시작한다.
     */
    public RewardClaimCompletionResult completeRewardClaim(
            long userId,
            String sessionKey,
            long rewardSequence,
            long claimedAt
    ) {
        String result = redisTemplate.execute(
                WatchRedisScripts.COMPLETE_REWARD_CLAIM,
                List.of(
                        activeKey(userId),
                        aliveKey(userId, sessionKey),
                        redisSessionKey(sessionKey)
                ),
                sessionKey,
                Long.toString(userId),
                Long.toString(rewardSequence),
                Long.toString(properties.claimInterval().toMillis()),
                Long.toString(claimedAt),
                Long.toString(properties.aliveTtl().toMillis()),
                Long.toString(properties.activeTtl().toMillis()),
                Long.toString(properties.sessionTtl().toMillis())
        );
        if (result == null) {
            throw new WatchException(WatchError.REWARD_CLAIM_RESULT_MISSING);
        }
        return RewardClaimCompletionResult.from(result);
    }

    /**
     * DB 지급 직전에 Redis 수령 자격을 확인하고 처리 중 만료되지 않도록 TTL을 갱신한다.
     */
    public RewardClaimCompletionStatus prepareRewardClaim(
            long userId,
            String sessionKey,
            long rewardSequence
    ) {
        String result = redisTemplate.execute(
                WatchRedisScripts.PREPARE_REWARD_CLAIM,
                List.of(
                        activeKey(userId),
                        aliveKey(userId, sessionKey),
                        redisSessionKey(sessionKey)
                ),
                sessionKey,
                Long.toString(userId),
                Long.toString(rewardSequence),
                Long.toString(properties.claimInterval().toMillis()),
                Long.toString(properties.aliveTtl().toMillis()),
                Long.toString(properties.activeTtl().toMillis()),
                Long.toString(properties.sessionTtl().toMillis())
        );
        if (result == null) {
            throw new WatchException(WatchError.REWARD_CLAIM_RESULT_MISSING);
        }
        try {
            return RewardClaimCompletionStatus.valueOf(result);
        } catch (IllegalArgumentException exception) {
            throw new WatchException(WatchError.REWARD_CLAIM_RESULT_UNKNOWN, exception);
        }
    }

    /**
     * 사용자별 세션 전환 lock이 없을 때 token과 TTL을 가진 lock을 생성한다.
     *
     * @param userId 세션을 전환할 사용자 ID
     * @param lockToken 현재 전환 요청을 식별하는 고유 token
     * @return lock 획득 성공 시 true, 이미 lock이 있으면 false
     */
    public boolean tryAcquireSwitchLock(long userId, String lockToken) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                switchLockKey(userId),
                lockToken,
                properties.switchLockTtl()
        );
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 저장된 token이 현재 요청의 token과 일치할 때만 세션 전환 lock을 삭제한다.
     *
     * @param userId 세션 전환 lock의 사용자 ID
     * @param lockToken 현재 요청이 소유한 lock token
     * @return lock을 삭제했으면 true, token이 다르거나 lock이 없으면 false
     */
    public boolean releaseSwitchLock(long userId, String lockToken) {
        return compareAndDelete(switchLockKey(userId), lockToken);
    }

    /**
     * 현재 active 값이 종료 대상 sessionKey와 일치할 때만 active 키를 삭제한다.
     *
     * @param userId active 키의 사용자 ID
     * @param sessionKey 종료 대상 시청 세션 외부 식별자
     * @return active 키를 삭제했으면 true, 값이 다르거나 키가 없으면 false
     */
    public boolean deleteActiveIfMatches(long userId, String sessionKey) {
        return compareAndDelete(activeKey(userId), sessionKey);
    }

    /**
     * 시청 세션의 heartbeat 생존 확인 키를 삭제한다.
     *
     * @param userId 시청 사용자 ID
     * @param sessionKey 삭제할 시청 세션 외부 식별자
     */
    public void deleteAlive(long userId, String sessionKey) {
        redisTemplate.delete(aliveKey(userId, sessionKey));
    }

    /**
     * 최종 정산용 Redis session Hash를 삭제한다.
     *
     * @param sessionKey 삭제할 시청 세션 외부 식별자
     */
    public void deleteSession(String sessionKey) {
        redisTemplate.delete(redisSessionKey(sessionKey));
    }

    /**
     * Redis String 값이 기대값과 일치할 때만 키를 삭제한다.
     *
     * @param key 조건부로 삭제할 Redis 키
     * @param expectedValue 현재 Redis 값과 비교할 기대값
     * @return 키를 삭제했으면 true, 값이 다르거나 키가 없으면 false
     */
    private boolean compareAndDelete(String key, String expectedValue) {
        Long deleted = redisTemplate.execute(
                WatchRedisScripts.COMPARE_AND_DELETE,
                List.of(key),
                expectedValue
        );
        return Long.valueOf(1L).equals(deleted);
    }

    /**
     * 사용자별 현재 활성 시청 세션 Redis 키를 생성한다.
     *
     * @param userId 사용자 ID
     * @return {@code watch:active:{userId}} 형식의 Redis 키
     */
    private static String activeKey(long userId) {
        return "watch:active:" + userId;
    }

    /**
     * 시청 세션의 heartbeat 생존 확인 Redis 키를 생성한다.
     *
     * @param userId 사용자 ID
     * @param sessionKey 시청 세션 외부 식별자
     * @return {@code watch:alive:{userId}:{sessionKey}} 형식의 Redis 키
     */
    private static String aliveKey(long userId, String sessionKey) {
        return "watch:alive:" + userId + ":" + sessionKey;
    }

    /**
     * 최종 정산용 시청 세션 Redis Hash 키를 생성한다.
     *
     * @param sessionKey 시청 세션 외부 식별자
     * @return {@code watch:session:{sessionKey}} 형식의 Redis 키
     */
    private static String redisSessionKey(String sessionKey) {
        return "watch:session:" + sessionKey;
    }

    /**
     * 사용자별 세션 전환 lock Redis 키를 생성한다.
     *
     * @param userId 사용자 ID
     * @return {@code watch:switch-lock:{userId}} 형식의 Redis 키
     */
    private static String switchLockKey(long userId) {
        return "watch:switch-lock:" + userId;
    }
}
