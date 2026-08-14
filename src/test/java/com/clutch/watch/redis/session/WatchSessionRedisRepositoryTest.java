package com.clutch.watch.redis.session;

import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.redis.heartbeat.HeartbeatProcessingResult;
import com.clutch.watch.redis.heartbeat.HeartbeatResult;
import com.clutch.watch.redis.reward.RewardClaimCompletionResult;
import com.clutch.watch.redis.reward.RewardClaimCompletionStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WatchSessionRedisRepositoryTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static WatchSessionRedisRepository repository;

    /**
     * 테스트 전용 Redis DB 15에 연결하고 테스트 대상을 생성한다.
     */
    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("REDIS_HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"))
        );
        configuration.setPassword(RedisPassword.of(
                System.getenv().getOrDefault("REDIS_PASSWORD", "clutch_local_password")
        ));
        configuration.setDatabase(15);

        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        repository = new WatchSessionRedisRepository(redisTemplate, testProperties());
    }

    /**
     * 모든 테스트가 끝나면 Redis connection factory를 종료한다.
     */
    @AfterAll
    static void closeRedis() {
        connectionFactory.destroy();
    }

    /**
     * 각 테스트가 독립적으로 실행되도록 테스트 전용 Redis DB 15를 초기화한다.
     */
    @BeforeEach
    void clearTestDatabase() {
        RedisConnection connection = connectionFactory.getConnection();
        try {
            connection.serverCommands().flushDb();
        } finally {
            connection.close();
        }
    }

    /**
     * 신규 세션의 active, alive, session 키와 초기 정산 상태가 생성되는지 검증한다.
     */
    @Test
    void initializesWatchSessionState() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);

        WatchSessionSnapshot snapshot = repository.findSession("session-1").orElseThrow();

        assertThat(repository.findActiveSessionKey(100L)).contains("session-1");
        assertThat(snapshot.userId()).isEqualTo(100L);
        assertThat(snapshot.matchId()).isEqualTo(200L);
        assertThat(snapshot.enteredAt()).isEqualTo(1_000_000L);
        assertThat(snapshot.lastSeen()).isEqualTo(1_000_000L);
        assertThat(snapshot.eligibleMilliseconds()).isZero();
        assertThat(snapshot.sequence()).isZero();
        assertThat(snapshot.rewardSequence()).isEqualTo(1L);
        assertThat(ttlMillis("watch:alive:100:session-1")).isPositive();
        assertThat(ttlMillis("watch:active:100")).isPositive();
        assertThat(ttlMillis("watch:session:session-1")).isPositive();
    }

    /**
     * 정상 heartbeat가 경과 밀리초를 누적하고 lastSeen, sequence, TTL을 갱신하는지 검증한다.
     */
    @Test
    void accumulatesEligibleMillisecondsOnHeartbeat() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);

        HeartbeatProcessingResult result = repository.heartbeat(
                100L, "session-1", 1L, 1_030_000L);
        WatchSessionSnapshot snapshot = repository.findSession("session-1").orElseThrow();

        assertThat(result.status()).isEqualTo(HeartbeatResult.SUCCESS);
        assertThat(result.eligibleMilliseconds()).isEqualTo(30_000L);
        assertThat(result.rewardSequence()).isEqualTo(1L);
        assertThat(snapshot.lastSeen()).isEqualTo(1_030_000L);
        assertThat(snapshot.eligibleMilliseconds()).isEqualTo(30_000L);
        assertThat(snapshot.sequence()).isEqualTo(1L);
        assertThat(snapshot.rewardSequence()).isEqualTo(1L);
        assertThat(ttlMillis("watch:alive:100:session-1"))
                .isLessThanOrEqualTo(90_000L)
                .isGreaterThan(85_000L);
    }

    @Test
    void capsAccumulationAtClaimInterval() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        for (long sequence = 1L; sequence <= 4L; sequence++) {
            repository.heartbeat(
                    100L,
                    "session-1",
                    sequence,
                    1_000_000L + (sequence * 60_000L)
            );
        }

        HeartbeatProcessingResult result = repository.heartbeat(
                100L,
                "session-1",
                5L,
                1_300_000L
        );

        assertThat(result.status()).isEqualTo(HeartbeatResult.SUCCESS);
        assertThat(result.eligibleMilliseconds()).isEqualTo(300_000L);
        assertThat(repository.findSession("session-1").orElseThrow().eligibleMilliseconds())
                .isEqualTo(300_000L);
    }

    @Test
    void capsHeartbeatThatOvershootsClaimInterval() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        redisTemplate.opsForHash().put(
                "watch:session:session-1",
                "eligibleMilliseconds",
                "299000"
        );

        HeartbeatProcessingResult result = repository.heartbeat(
                100L,
                "session-1",
                1L,
                1_030_000L
        );

        assertThat(result.eligibleMilliseconds()).isEqualTo(300_000L);
        assertThat(repository.findSession("session-1").orElseThrow().eligibleMilliseconds())
                .isEqualTo(300_000L);
    }

    @Test
    void keepsHeartbeatAliveWithoutAccumulatingWhileRewardIsClaimable() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        for (long sequence = 1L; sequence <= 5L; sequence++) {
            repository.heartbeat(
                    100L,
                    "session-1",
                    sequence,
                    1_000_000L + (sequence * 60_000L)
            );
        }

        HeartbeatProcessingResult result = repository.heartbeat(
                100L,
                "session-1",
                6L,
                1_360_000L
        );
        WatchSessionSnapshot snapshot = repository.findSession("session-1").orElseThrow();

        assertThat(result.eligibleMilliseconds()).isEqualTo(300_000L);
        assertThat(result.rewardSequence()).isEqualTo(1L);
        assertThat(snapshot.lastSeen()).isEqualTo(1_360_000L);
        assertThat(snapshot.sequence()).isEqualTo(6L);
        assertThat(snapshot.eligibleMilliseconds()).isEqualTo(300_000L);
        assertThat(ttlMillis("watch:alive:100:session-1")).isPositive();
    }

    @Test
    void completesRewardClaimAndStartsNextSequence() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        redisTemplate.opsForHash().put(
                "watch:session:session-1",
                "eligibleMilliseconds",
                "300000"
        );

        assertThat(repository.prepareRewardClaim(100L, "session-1", 1L))
                .isEqualTo(RewardClaimCompletionStatus.SUCCESS);
        RewardClaimCompletionResult result = repository.completeRewardClaim(
                100L,
                "session-1",
                1L,
                1_300_000L
        );
        WatchSessionSnapshot snapshot = repository.findSession("session-1").orElseThrow();

        assertThat(result.status()).isEqualTo(RewardClaimCompletionStatus.SUCCESS);
        assertThat(result.nextRewardSequence()).isEqualTo(2L);
        assertThat(snapshot.eligibleMilliseconds()).isZero();
        assertThat(snapshot.rewardSequence()).isEqualTo(2L);
        assertThat(snapshot.lastSeen()).isEqualTo(1_300_000L);
    }

    @Test
    void rejectsRewardClaimBeforeFiveMinutes() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);

        assertThat(repository.prepareRewardClaim(100L, "session-1", 1L))
                .isEqualTo(RewardClaimCompletionStatus.NOT_CLAIMABLE);
    }

    @Test
    void reportsAlreadyCompletedForRepeatedRedisCompletion() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        redisTemplate.opsForHash().put(
                "watch:session:session-1",
                "eligibleMilliseconds",
                "300000"
        );
        repository.completeRewardClaim(100L, "session-1", 1L, 1_300_000L);

        RewardClaimCompletionResult result = repository.completeRewardClaim(
                100L,
                "session-1",
                1L,
                1_300_001L
        );

        assertThat(result.status()).isEqualTo(RewardClaimCompletionStatus.ALREADY_COMPLETED);
        assertThat(result.nextRewardSequence()).isEqualTo(2L);
    }

    /**
     * 동일 경기 재입장 시 누적 상태와 heartbeat 순번을 유지하면서 sessionKey를 교체하는지 검증한다.
     */
    @Test
    void replacesSessionKeyWhilePreservingWatchState() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        repository.heartbeat(100L, "session-1", 1L, 1_030_000L);

        SessionKeyReplacementResult result = repository.replaceSessionKey(
                100L,
                "session-1",
                "session-2"
        );

        assertThat(result).isEqualTo(SessionKeyReplacementResult.SUCCESS);
        assertThat(repository.findActiveSessionKey(100L)).contains("session-2");
        assertThat(repository.findSession("session-1")).isEmpty();
        WatchSessionSnapshot snapshot = repository.findSession("session-2").orElseThrow();
        assertThat(snapshot.eligibleMilliseconds()).isEqualTo(30_000L);
        assertThat(snapshot.sequence()).isEqualTo(1L);
        assertThat(redisTemplate.hasKey("watch:alive:100:session-1")).isFalse();
        assertThat(redisTemplate.hasKey("watch:alive:100:session-2")).isTrue();
        assertThat(repository.heartbeat(100L, "session-1", 2L, 1_060_000L))
                .extracting(HeartbeatProcessingResult::status)
                .isEqualTo(HeartbeatResult.REPLACED);
    }

    @Test
    void returnsExpiredWhenReplacingSessionWithoutActiveKey() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        repository.deleteActiveIfMatches(100L, "session-1");

        SessionKeyReplacementResult result = repository.replaceSessionKey(
                100L,
                "session-1",
                "session-2"
        );

        assertThat(result).isEqualTo(SessionKeyReplacementResult.EXPIRED);
        assertThat(repository.findSession("session-1")).isPresent();
        assertThat(repository.findSession("session-2")).isEmpty();
    }

    @Test
    void returnsReplacedWhenAnotherSessionIsActive() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        repository.initialize(100L, 201L, "session-2", 1_010_000L);

        SessionKeyReplacementResult result = repository.replaceSessionKey(
                100L,
                "session-1",
                "session-3"
        );

        assertThat(result).isEqualTo(SessionKeyReplacementResult.REPLACED);
        assertThat(repository.findSession("session-1")).isPresent();
        assertThat(repository.findSession("session-3")).isEmpty();
    }

    /**
     * 이미 처리한 sequence 이하의 heartbeat가 시간과 상태를 다시 변경하지 않는지 검증한다.
     */
    @Test
    void rejectsDuplicatedHeartbeatSequence() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        repository.heartbeat(100L, "session-1", 1L, 1_030_000L);

        HeartbeatProcessingResult result = repository.heartbeat(
                100L, "session-1", 1L, 1_060_000L);
        WatchSessionSnapshot snapshot = repository.findSession("session-1").orElseThrow();

        assertThat(result.status()).isEqualTo(HeartbeatResult.INVALID_SEQUENCE);
        assertThat(snapshot.lastSeen()).isEqualTo(1_030_000L);
        assertThat(snapshot.eligibleMilliseconds()).isEqualTo(30_000L);
        assertThat(snapshot.sequence()).isEqualTo(1L);
    }

    /**
     * 최대 인정 간격을 초과한 heartbeat는 상태를 갱신하되 경과시간을 적립하지 않는지 검증한다.
     */
    @Test
    void doesNotAccumulateIntervalOverMaximum() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);

        HeartbeatProcessingResult result = repository.heartbeat(
                100L, "session-1", 1L, 1_060_001L);
        WatchSessionSnapshot snapshot = repository.findSession("session-1").orElseThrow();

        assertThat(result.status()).isEqualTo(HeartbeatResult.SUCCESS);
        assertThat(snapshot.lastSeen()).isEqualTo(1_060_001L);
        assertThat(snapshot.eligibleMilliseconds()).isZero();
        assertThat(snapshot.sequence()).isEqualTo(1L);
    }

    /**
     * active가 새 세션으로 교체된 뒤 도착한 이전 세션 heartbeat를 거부하는지 검증한다.
     */
    @Test
    void rejectsHeartbeatFromReplacedSession() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        repository.initialize(100L, 201L, "session-2", 1_010_000L);

        HeartbeatProcessingResult result = repository.heartbeat(
                100L, "session-1", 1L, 1_030_000L);

        assertThat(result.status()).isEqualTo(HeartbeatResult.REPLACED);
        assertThat(repository.findSession("session-1").orElseThrow().eligibleMilliseconds()).isZero();
    }

    /**
     * active 키가 사라진 세션의 heartbeat가 교체가 아닌 만료 결과를 반환하는지 검증한다.
     */
    @Test
    void rejectsHeartbeatAfterActiveExpired() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        repository.deleteActiveIfMatches(100L, "session-1");

        HeartbeatProcessingResult result = repository.heartbeat(
                100L, "session-1", 1L, 1_030_000L);

        assertThat(result.status()).isEqualTo(HeartbeatResult.EXPIRED);
        assertThat(repository.findSession("session-1").orElseThrow().sequence()).isZero();
    }

    /**
     * alive 키가 사라진 세션의 heartbeat가 세션을 되살리지 않고 만료 결과를 반환하는지 검증한다.
     */
    @Test
    void rejectsHeartbeatAfterAliveExpired() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);
        repository.deleteAlive(100L, "session-1");

        HeartbeatProcessingResult result = repository.heartbeat(
                100L, "session-1", 1L, 1_030_000L);

        assertThat(result.status()).isEqualTo(HeartbeatResult.EXPIRED);
        assertThat(redisTemplate.hasKey("watch:alive:100:session-1")).isFalse();
        assertThat(repository.findSession("session-1").orElseThrow().sequence()).isZero();
    }

    /**
     * 세션 전환 lock이 있으면 heartbeat를 거부하고 소유 token으로만 lock을 해제하는지 검증한다.
     */
    @Test
    void blocksHeartbeatWhileSwitchLockExists() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);

        assertThat(repository.tryAcquireSwitchLock(100L, "token-a")).isTrue();
        assertThat(repository.tryAcquireSwitchLock(100L, "token-b")).isFalse();
        assertThat(repository.heartbeat(100L, "session-1", 1L, 1_030_000L))
                .extracting(HeartbeatProcessingResult::status)
                .isEqualTo(HeartbeatResult.SWITCHING);
        assertThat(repository.releaseSwitchLock(100L, "token-b")).isFalse();
        assertThat(repository.releaseSwitchLock(100L, "token-a")).isTrue();
    }

    /**
     * active 값이 종료할 sessionKey와 일치할 때만 active 키를 삭제하는지 검증한다.
     */
    @Test
    void deletesActiveOnlyWhenSessionKeyMatches() {
        repository.initialize(100L, 200L, "session-1", 1_000_000L);

        assertThat(repository.deleteActiveIfMatches(100L, "session-2")).isFalse();
        assertThat(repository.findActiveSessionKey(100L)).contains("session-1");
        assertThat(repository.deleteActiveIfMatches(100L, "session-1")).isTrue();
        assertThat(repository.findActiveSessionKey(100L)).isEmpty();
    }

    /**
     * Redis 키의 남은 TTL을 밀리초 단위로 조회한다.
     *
     * @param key TTL을 조회할 Redis 키
     * @return 남은 TTL(milliseconds), 키 상태에 따라 Redis의 음수 상태값
     */
    private long ttlMillis(String key) {
        return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }

    /**
     * Redis Repository 테스트에 사용할 고정 시청 보상 정책을 생성한다.
     *
     * @return 테스트용 시청 보상 설정
     */
    private static WatchRewardProperties testProperties() {
        return new WatchRewardProperties(
                Duration.ofSeconds(30),
                Duration.ofSeconds(90),
                Duration.ofSeconds(120),
                Duration.ofHours(1),
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                100L
        );
    }
}
