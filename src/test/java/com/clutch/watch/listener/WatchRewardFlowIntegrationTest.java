package com.clutch.watch.listener;

import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.domain.WatchSessionStatus;
import com.clutch.watch.repository.WatchPointTransactionRepository;
import com.clutch.watch.repository.WatchSessionRepository;
import com.clutch.watch.redis.WatchSessionRedisRepository;
import com.clutch.watch.redis.WatchSessionSnapshot;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 MySQL, Redis, API, 서비스와 만료 Listener를 연결한 시청 보상 전체 흐름 테스트.
 * 테스트 실행 전 {@code docker compose up -d mysql redis}가 필요하다.
 */
@SpringBootTest(properties = {
        "spring.data.redis.database=14",
        "watch.reward.heartbeat-interval=100ms",
        "watch.reward.alive-ttl=750ms",
        "watch.reward.active-ttl=3s",
        "watch.reward.session-ttl=10s",
        "watch.reward.switch-lock-ttl=2s",
        "watch.reward.max-eligible-interval=60s"
})
@AutoConfigureMockMvc
class WatchRewardFlowIntegrationTest {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EsportsMatchRepository esportsMatchRepository;

    @Autowired
    private WatchSessionRepository watchSessionRepository;

    @Autowired
    private WatchPointTransactionRepository watchPointTransactionRepository;

    @Autowired
    private WatchSessionRedisRepository watchSessionRedisRepository;

    @Autowired
    private WatchAliveExpirationListener expirationListener;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> matchIds = new ArrayList<>();
    private final List<String> sessionKeys = new ArrayList<>();

    /**
     * 각 통합 테스트가 독립적으로 실행되도록 테스트 전용 Redis DB를 초기화한다.
     */
    @BeforeEach
    void clearRedisBeforeTest() {
        flushRedisDatabase();
    }

    /**
     * 테스트에서 생성한 Watch 데이터와 테스트 전용 Redis DB를 외래 키 순서에 맞춰 정리한다.
     */
    @AfterEach
    void cleanUpFixtures() {
        flushRedisDatabase();
        for (String sessionKey : sessionKeys) {
            watchSessionRepository.findBySessionKey(sessionKey).ifPresent(session -> {
                watchPointTransactionRepository.deleteAllByWatchSessionId(session.getId());
                watchSessionRepository.delete(session);
            });
        }
        userRepository.deleteAllById(userIds);
        esportsMatchRepository.deleteAllById(matchIds);
    }

    /**
     * Alive 만료 시 누적시간을 기록하되 포인트와 거래를 생성하지 않는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void discardsUnclaimedRewardOnExpiration() throws Exception {
        User user = saveUser();
        EsportsMatch match = saveMatch("inProgress");
        String sessionKey = startSession(user.getId(), match.getId());
        setEligibleMilliseconds(sessionKey, 60_000L);

        expirationListener.handleExpiredKey(aliveKey(user.getId(), sessionKey));

        WatchSession session = watchSessionRepository.findBySessionKey(sessionKey).orElseThrow();
        User rewardedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(rewardedUser.getPoint()).isZero();
        assertThat(session.getStatus()).isEqualTo(WatchSessionStatus.COMPLETED);
        assertThat(session.getEligibleMilliseconds()).isEqualTo(60_000L);
        assertThat(watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(session.getId(), 1L)).isEmpty();
        assertThat(watchSessionRedisRepository.findActiveSessionKey(user.getId())).isEmpty();
        assertThat(watchSessionRedisRepository.findSession(sessionKey)).isEmpty();
    }

    /**
     * 부분 누적시간도 Alive 만료 후 거래 없이 완료 상태로 기록되는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void completesShortSessionWithoutPointTransaction() throws Exception {
        User user = saveUser();
        EsportsMatch match = saveMatch("inProgress");
        String sessionKey = startSession(user.getId(), match.getId());
        setEligibleMilliseconds(sessionKey, 59_999L);

        expirationListener.handleExpiredKey(aliveKey(user.getId(), sessionKey));

        WatchSession session = watchSessionRepository.findBySessionKey(sessionKey).orElseThrow();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoint()).isZero();
        assertThat(session.getStatus()).isEqualTo(WatchSessionStatus.COMPLETED);
        assertThat(watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(session.getId(), 1L)).isEmpty();
    }

    /**
     * 다른 경기 입장 시 기존 세션을 미지급 종료하고 새 세션이 active로 유지되는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void switchesMatchWithoutRewardingPreviousSession() throws Exception {
        User user = saveUser();
        EsportsMatch firstMatch = saveMatch("inProgress");
        EsportsMatch secondMatch = saveMatch("inProgress");
        String firstSessionKey = startSession(user.getId(), firstMatch.getId());
        setEligibleMilliseconds(firstSessionKey, 60_000L);

        String secondSessionKey = startSession(user.getId(), secondMatch.getId());
        WatchSession firstSession = watchSessionRepository.findBySessionKey(firstSessionKey).orElseThrow();
        WatchSession secondSession = watchSessionRepository.findBySessionKey(secondSessionKey).orElseThrow();
        assertThat(firstSession.getStatus()).isEqualTo(WatchSessionStatus.COMPLETED);
        assertThat(secondSession.getStatus()).isEqualTo(WatchSessionStatus.WATCHING);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoint()).isZero();
        assertThat(watchSessionRedisRepository.findActiveSessionKey(user.getId())).contains(secondSessionKey);
        assertThat(watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(firstSession.getId(), 1L)).isEmpty();
        assertThat(watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(secondSession.getId(), 1L)).isEmpty();
    }

    /**
     * 같은 경기 재입장 시 DB 세션과 누적시간은 유지하고 이전 sessionKey만 무효화하는지 검증한다.
     */
    @Test
    void resumesSameMatchWithLatestSessionKey() throws Exception {
        User user = saveUser();
        EsportsMatch match = saveMatch("inProgress");
        String firstSessionKey = startSession(user.getId(), match.getId());
        setEligibleMilliseconds(firstSessionKey, 60_000L);
        WatchSession firstSession = watchSessionRepository.findBySessionKey(firstSessionKey).orElseThrow();
        Long watchSessionId = firstSession.getId();

        String secondSessionKey = startSession(user.getId(), match.getId());

        WatchSession resumedSession = watchSessionRepository.findBySessionKey(secondSessionKey).orElseThrow();
        WatchSessionSnapshot snapshot = watchSessionRedisRepository.findSession(secondSessionKey).orElseThrow();
        assertThat(resumedSession.getId()).isEqualTo(watchSessionId);
        assertThat(snapshot.eligibleMilliseconds()).isEqualTo(60_000L);
        assertThat(watchSessionRedisRepository.findSession(firstSessionKey)).isEmpty();
        heartbeat(user.getId(), firstSessionKey, 1L, 409, "WATCH_SESSION_REPLACED");
        heartbeat(user.getId(), secondSessionKey, 1L, 200, null);
    }

    /**
     * 동일 만료 처리가 반복되어도 사용자 포인트와 거래를 생성하지 않는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void doesNotRewardRepeatedExpiration() throws Exception {
        User user = saveUser();
        EsportsMatch match = saveMatch("inProgress");
        String sessionKey = startSession(user.getId(), match.getId());
        setEligibleMilliseconds(sessionKey, 60_000L);
        WatchSessionSnapshot snapshot = watchSessionRedisRepository.findSession(sessionKey).orElseThrow();

        expirationListener.handleExpiredKey(aliveKey(user.getId(), sessionKey));
        redisTemplate.opsForHash().putAll(sessionRedisKey(sessionKey), snapshotFields(snapshot));
        expirationListener.handleExpiredKey(aliveKey(user.getId(), sessionKey));

        WatchSession session = watchSessionRepository.findBySessionKey(sessionKey).orElseThrow();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoint()).isZero();
        assertThat(watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(session.getId(), 1L)).isEmpty();
    }

    /**
     * 실제 Redis Alive TTL 만료 이벤트가 세션을 미지급 종료하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void discardsRewardWhenAliveKeyExpires() throws Exception {
        User user = saveUser();
        EsportsMatch match = saveMatch("inProgress");
        String sessionKey = startSession(user.getId(), match.getId());
        setEligibleMilliseconds(sessionKey, 60_000L);
        redisTemplate.expire(aliveKey(user.getId(), sessionKey), Duration.ofMillis(750));

        await(() -> watchSessionRepository.findBySessionKey(sessionKey)
                .map(session -> session.getStatus() == WatchSessionStatus.COMPLETED)
                .orElse(false));

        WatchSession session = watchSessionRepository.findBySessionKey(sessionKey).orElseThrow();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoint()).isZero();
        assertThat(watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(session.getId(), 1L)).isEmpty();
        assertThat(watchSessionRedisRepository.findSession(sessionKey)).isEmpty();
    }

    /**
     * 실제 서비스가 없는 사용자와 시청 불가능한 경기를 정의된 API 오류로 반환하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void returnsApiErrorsForMissingUserAndUnwatchableMatch() throws Exception {
        EsportsMatch inProgressMatch = saveMatch("inProgress");
        mockMvc.perform(post("/api/users/{userId}/matches/{matchId}/watch-sessions",
                        Long.MAX_VALUE, inProgressMatch.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        User user = saveUser();
        EsportsMatch completedMatch = saveMatch("completed");
        mockMvc.perform(post("/api/users/{userId}/matches/{matchId}/watch-sessions",
                        user.getId(), completedMatch.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_WATCHABLE"));
    }

    /**
     * 실제 MVC 검증이 1보다 작은 경로 ID를 서비스 호출 전에 거부하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void rejectsNonPositivePathIdentifiers() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/matches/{matchId}/watch-sessions", 0L, 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * 실제 Redis Lua 검증 결과가 Heartbeat API 오류 코드로 연결되는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void convertsActualRedisHeartbeatFailuresToApiErrors() throws Exception {
        User user = saveUser();
        EsportsMatch match = saveMatch("inProgress");
        String sessionKey = startSession(user.getId(), match.getId());

        heartbeat(user.getId(), sessionKey, 1L, 200, null);
        heartbeat(user.getId(), sessionKey, 1L, 409, "INVALID_HEARTBEAT_SEQUENCE");

        assertThat(watchSessionRedisRepository.tryAcquireSwitchLock(user.getId(), "integration-lock")).isTrue();
        heartbeat(user.getId(), sessionKey, 2L, 409, "WATCH_SESSION_SWITCHING");
        assertThat(watchSessionRedisRepository.releaseSwitchLock(user.getId(), "integration-lock")).isTrue();

        watchSessionRedisRepository.deleteAlive(user.getId(), sessionKey);
        heartbeat(user.getId(), sessionKey, 2L, 410, "WATCH_SESSION_EXPIRED");

        heartbeat(user.getId(), "missing-session", 1L, 409, "WATCH_SESSION_REPLACED");
    }

    /**
     * 다른 세션으로 교체된 이전 Heartbeat를 실제 Redis active 상태로 거부하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void rejectsHeartbeatFromActuallyReplacedSession() throws Exception {
        User user = saveUser();
        EsportsMatch firstMatch = saveMatch("inProgress");
        EsportsMatch secondMatch = saveMatch("inProgress");
        String firstSessionKey = startSession(user.getId(), firstMatch.getId());
        String secondSessionKey = startSession(user.getId(), secondMatch.getId());

        heartbeat(user.getId(), firstSessionKey, 1L, 409, "WATCH_SESSION_REPLACED");
        assertThat(watchSessionRedisRepository.findActiveSessionKey(user.getId())).contains(secondSessionKey);
    }

    /**
     * Redis active와 alive를 위조해도 session Hash의 사용자 검증이 다른 사용자 요청을 차단하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void rejectsHeartbeatWithActualRedisUserMismatch() throws Exception {
        User owner = saveUser();
        User attacker = saveUser();
        EsportsMatch match = saveMatch("inProgress");
        String sessionKey = startSession(owner.getId(), match.getId());
        redisTemplate.opsForValue().set("watch:active:" + attacker.getId(), sessionKey, Duration.ofSeconds(3));
        redisTemplate.opsForValue().set(aliveKey(attacker.getId(), sessionKey), "1", Duration.ofSeconds(1));

        heartbeat(attacker.getId(), sessionKey, 1L, 403, "WATCH_SESSION_USER_MISMATCH");
    }

    /**
     * 입장 API를 호출하고 응답에서 생성된 sessionKey를 반환한다.
     *
     * @param userId 입장 사용자 ID
     * @param matchId 입장 경기 ID
     * @return API가 발급한 sessionKey
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    private String startSession(long userId, long matchId) throws Exception {
        String response = mockMvc.perform(post(
                        "/api/users/{userId}/matches/{matchId}/watch-sessions", userId, matchId))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        String sessionKey = JsonPath.read(response, "$.sessionKey");
        sessionKeys.add(sessionKey);
        redisTemplate.expire(aliveKey(userId, sessionKey), Duration.ofSeconds(30));
        return sessionKey;
    }

    /**
     * Heartbeat API를 호출하고 기대 HTTP 상태와 오류 코드를 검증한다.
     *
     * @param userId 요청 사용자 ID
     * @param sessionKey 요청 시청 세션 키
     * @param sequence Heartbeat 순번
     * @param expectedStatus 기대 HTTP 상태
     * @param expectedCode 실패 시 기대 오류 코드, 성공이면 null
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    private void heartbeat(
            long userId,
            String sessionKey,
            long sequence,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        var actions = mockMvc.perform(post(
                        "/api/users/{userId}/watch-sessions/{sessionKey}/heartbeat", userId, sessionKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sequence\":" + sequence + "}"))
                .andExpect(status().is(expectedStatus));
        if (expectedCode != null) {
            actions.andExpect(jsonPath("$.code").value(expectedCode));
        }
    }

    /**
     * 고유 이메일을 가진 테스트 사용자를 저장한다.
     *
     * @return 저장된 사용자
     */
    private User saveUser() {
        User user = userRepository.saveAndFlush(
                User.create(UserRole.USER, "watch-flow-" + UUID.randomUUID() + "@example.com")
        );
        userIds.add(user.getId());
        return user;
    }

    /**
     * 지정한 진행 상태의 테스트 경기를 저장한다.
     *
     * @param lifecycleStatus 경기 진행 상태
     * @return 저장된 경기
     */
    private EsportsMatch saveMatch(String lifecycleStatus) {
        String externalMatchId = UUID.randomUUID().toString().replace("-", "");
        EsportsMatch match = esportsMatchRepository.saveAndFlush(new EsportsMatch(
                externalMatchId,
                "integration-league",
                "2026",
                "integration-tournament",
                "integration-block",
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().minusMinutes(30),
                lifecycleStatus,
                3
        ));
        matchIds.add(match.getId());
        return match;
    }

    /**
     * Redis session Hash의 최종 유효 시청시간을 테스트 값으로 설정한다.
     *
     * @param sessionKey 설정할 시청 세션 키
     * @param eligibleMilliseconds 최종 유효 시청시간
     */
    private void setEligibleMilliseconds(String sessionKey, long eligibleMilliseconds) {
        redisTemplate.opsForHash().put(
                sessionRedisKey(sessionKey),
                "eligibleMilliseconds",
                Long.toString(eligibleMilliseconds)
        );
    }

    /**
     * Redis snapshot을 Hash 복원에 사용할 문자열 필드로 변환한다.
     *
     * @param snapshot 복원할 Redis 시청 세션 상태
     * @return Redis Hash 필드와 값
     */
    private java.util.Map<String, String> snapshotFields(WatchSessionSnapshot snapshot) {
        return java.util.Map.of(
                "userId", Long.toString(snapshot.userId()),
                "matchId", Long.toString(snapshot.matchId()),
                "enteredAt", Long.toString(snapshot.enteredAt()),
                "lastSeen", Long.toString(snapshot.lastSeen()),
                "eligibleMilliseconds", Long.toString(snapshot.eligibleMilliseconds()),
                "sequence", Long.toString(snapshot.sequence()),
                "rewardSequence", Long.toString(snapshot.rewardSequence())
        );
    }

    /**
     * 조건이 제한 시간 안에 참이 될 때까지 짧은 간격으로 확인한다.
     *
     * @param condition 비동기 완료 여부
     * @throws InterruptedException 대기 중 테스트 thread가 중단된 경우
     */
    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + ASYNC_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50L);
        }
        assertThat(condition.getAsBoolean()).as("제한 시간 안에 비동기 처리가 완료되어야 합니다.").isTrue();
    }

    /**
     * 테스트 전용 Redis DB의 모든 키를 삭제한다.
     */
    private void flushRedisDatabase() {
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        try {
            connection.serverCommands().flushDb();
        } finally {
            connection.close();
        }
    }

    /**
     * 테스트에서 사용할 Alive Redis 키를 생성한다.
     *
     * @param userId 사용자 ID
     * @param sessionKey 시청 세션 키
     * @return Alive Redis 키
     */
    private String aliveKey(long userId, String sessionKey) {
        return "watch:alive:" + userId + ":" + sessionKey;
    }

    /**
     * 테스트에서 사용할 session Hash Redis 키를 생성한다.
     *
     * @param sessionKey 시청 세션 키
     * @return session Hash Redis 키
     */
    private String sessionRedisKey(String sessionKey) {
        return "watch:session:" + sessionKey;
    }
}
