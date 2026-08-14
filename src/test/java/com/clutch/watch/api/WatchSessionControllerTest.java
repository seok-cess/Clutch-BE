package com.clutch.watch.api;

import com.clutch.watch.api.handler.WatchExceptionHandler;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.service.service.WatchSessionService;
import com.clutch.watch.service.service.WatchPointClaimService;
import com.clutch.watch.service.dto.WatchHeartbeatResult;
import com.clutch.watch.service.dto.WatchPointClaimResult;
import com.clutch.watch.service.dto.WatchRewardState;
import com.clutch.watch.service.dto.WatchSessionStartResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WatchSessionControllerTest {

    private static final long USER_ID = 100L;
    private static final long MATCH_ID = 200L;
    private static final String SESSION_KEY = "33ce4d12-e136-4da4-9ba5-e955091b09bf";

    @Mock
    private WatchSessionService watchSessionService;

    @Mock
    private WatchPointClaimService watchPointClaimService;

    private MockMvc mockMvc;

    /**
     * Controller와 전용 예외 처리기를 standalone MockMvc에 등록한다.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WatchSessionController(
                        watchSessionService,
                        watchPointClaimService
                ))
                .setControllerAdvice(new WatchExceptionHandler())
                .build();
    }

    /**
     * 경기 입장 요청이 새 세션 정보와 함께 201 응답을 반환하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void startsWatchSession() throws Exception {
        Instant enteredAt = Instant.parse("2026-08-13T03:00:00Z");
        when(watchSessionService.start(USER_ID, MATCH_ID)).thenReturn(new WatchSessionStartResult(
                SESSION_KEY,
                MATCH_ID,
                enteredAt,
                30L,
                90L,
                0L
        ));

        mockMvc.perform(post("/api/users/{userId}/matches/{matchId}/watch-sessions", USER_ID, MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sessionKey").value(SESSION_KEY))
                .andExpect(jsonPath("$.matchId").value(MATCH_ID))
                .andExpect(jsonPath("$.enteredAt").value("2026-08-13T03:00:00Z"))
                .andExpect(jsonPath("$.heartbeatIntervalSeconds").value(30))
                .andExpect(jsonPath("$.sessionTimeoutSeconds").value(90))
                .andExpect(jsonPath("$.heartbeatSequence").value(0));

        verify(watchSessionService).start(USER_ID, MATCH_ID);
    }

    /**
     * 동일 경기 재입장은 기존 세션 상태를 이어받았음을 200 응답으로 반환하는지 검증한다.
     */
    @Test
    void resumesSameMatchSession() throws Exception {
        Instant enteredAt = Instant.parse("2026-08-13T03:00:00Z");
        when(watchSessionService.start(USER_ID, MATCH_ID)).thenReturn(new WatchSessionStartResult(
                SESSION_KEY,
                MATCH_ID,
                enteredAt,
                30L,
                90L,
                7L
        ));

        mockMvc.perform(post("/api/users/{userId}/matches/{matchId}/watch-sessions", USER_ID, MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionKey").value(SESSION_KEY))
                .andExpect(jsonPath("$.heartbeatSequence").value(7));
    }

    /**
     * 정상 Heartbeat가 현재 포인트 수령 상태를 반환하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void acceptsHeartbeat() throws Exception {
        when(watchSessionService.heartbeat(USER_ID, SESSION_KEY, 1L))
                .thenReturn(new WatchHeartbeatResult(
                        WatchRewardState.CLAIMABLE,
                        1L,
                        300L,
                        0L,
                        100L
                ));

        mockMvc.perform(post("/api/users/{userId}/watch-sessions/{sessionKey}/heartbeat", USER_ID, SESSION_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sequence\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardState").value("CLAIMABLE"))
                .andExpect(jsonPath("$.rewardSequence").value(1))
                .andExpect(jsonPath("$.accumulatedSeconds").value(300))
                .andExpect(jsonPath("$.remainingSeconds").value(0))
                .andExpect(jsonPath("$.rewardPoint").value(100));

        verify(watchSessionService).heartbeat(USER_ID, SESSION_KEY, 1L);
    }

    @Test
    void claimsWatchPoint() throws Exception {
        when(watchPointClaimService.claim(USER_ID, SESSION_KEY, 1L))
                .thenReturn(new WatchPointClaimResult(1L, 100L, 1_100L, 2L));

        mockMvc.perform(post(
                        "/api/users/{userId}/watch-sessions/{sessionKey}/point-claims",
                        USER_ID,
                        SESSION_KEY
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardSequence\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardSequence").value(1))
                .andExpect(jsonPath("$.awardedPoint").value(100))
                .andExpect(jsonPath("$.totalPoint").value(1100))
                .andExpect(jsonPath("$.nextRewardSequence").value(2));

        verify(watchPointClaimService).claim(USER_ID, SESSION_KEY, 1L);
    }

    /**
     * Redis Heartbeat 실패 결과가 정의된 HTTP 상태와 오류 코드로 변환되는지 검증한다.
     *
     * @param result Redis Heartbeat 실패 결과
     * @param expectedStatus 기대하는 HTTP 상태
     * @param expectedCode 기대하는 API 오류 코드
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @ParameterizedTest
    @MethodSource("heartbeatErrors")
    void convertsHeartbeatFailureToApiError(
            WatchError error,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        when(watchSessionService.heartbeat(USER_ID, SESSION_KEY, 1L))
                .thenThrow(new WatchException(error));

        mockMvc.perform(post("/api/users/{userId}/watch-sessions/{sessionKey}/heartbeat", USER_ID, SESSION_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sequence\":1}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /**
     * 1보다 작은 Heartbeat 순번을 400 입력값 오류로 거부하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void rejectsInvalidHeartbeatSequence() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/watch-sessions/{sessionKey}/heartbeat", USER_ID, SESSION_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sequence\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Heartbeat 순번은 1 이상이어야 합니다."));
    }

    /**
     * Heartbeat 순번이 누락되면 기본값 0을 검증하여 400 오류로 거부하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void rejectsMissingHeartbeatSequence() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/watch-sessions/{sessionKey}/heartbeat", USER_ID, SESSION_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * JSON 형식이 깨진 Heartbeat 요청을 공통 400 오류 응답으로 변환하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void rejectsMalformedHeartbeatJson() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/watch-sessions/{sessionKey}/heartbeat", USER_ID, SESSION_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sequence\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * 서비스의 입장 오류가 정의된 API 오류 응답으로 변환되는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void returnsApiErrorWhenMatchIsNotWatchable() throws Exception {
        when(watchSessionService.start(USER_ID, MATCH_ID))
                .thenThrow(new WatchException(WatchError.MATCH_NOT_WATCHABLE));

        mockMvc.perform(post("/api/users/{userId}/matches/{matchId}/watch-sessions", USER_ID, MATCH_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_WATCHABLE"))
                .andExpect(jsonPath("$.message").value("현재 시청 가능한 경기가 아닙니다."));
    }

    /**
     * Heartbeat 실패 결과와 기대 API 응답의 조합을 제공한다.
     *
     * @return Redis 결과, HTTP 상태, 오류 코드 조합
     */
    private static Stream<Arguments> heartbeatErrors() {
        return Stream.of(
                Arguments.of(WatchError.WATCH_SESSION_SWITCHING, 409, "WATCH_SESSION_SWITCHING"),
                Arguments.of(WatchError.WATCH_SESSION_REPLACED, 409, "WATCH_SESSION_REPLACED"),
                Arguments.of(WatchError.WATCH_SESSION_EXPIRED, 410, "WATCH_SESSION_EXPIRED"),
                Arguments.of(WatchError.WATCH_SESSION_NOT_FOUND, 404, "WATCH_SESSION_NOT_FOUND"),
                Arguments.of(WatchError.WATCH_SESSION_USER_MISMATCH, 403, "WATCH_SESSION_USER_MISMATCH"),
                Arguments.of(WatchError.INVALID_HEARTBEAT_SEQUENCE, 409, "INVALID_HEARTBEAT_SEQUENCE")
        );
    }
}
