package com.clutch.watch.api;

import com.clutch.watch.exception.WatchSessionError;
import com.clutch.watch.exception.WatchSessionException;
import com.clutch.watch.redis.HeartbeatResult;
import com.clutch.watch.service.WatchSessionService;
import com.clutch.watch.service.WatchSessionStartResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경기 시청 세션 입장과 Heartbeat API를 제공한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}")
public class WatchSessionController {

    private final WatchSessionService watchSessionService;

    /**
     * 사용자를 경기에 입장시키고 새 시청 세션을 발급한다.
     *
     * @param userId 경기를 시청할 사용자 ID
     * @param matchId 시청할 경기 ID
     * @return 생성된 시청 세션과 Heartbeat 정책
     */
    @PostMapping("/matches/{matchId}/watch-sessions")
    public ResponseEntity<WatchSessionStartResponse> start(
            @PathVariable @Positive(message = "사용자 ID는 1 이상이어야 합니다.") long userId,
            @PathVariable @Positive(message = "경기 ID는 1 이상이어야 합니다.") long matchId
    ) {
        WatchSessionStartResult result = watchSessionService.start(userId, matchId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WatchSessionStartResponse.from(result));
    }

    /**
     * 시청 중인 세션의 Heartbeat를 처리한다.
     *
     * @param userId Heartbeat를 보낸 사용자 ID
     * @param sessionKey Heartbeat 대상 시청 세션 외부 식별자
     * @param request 증가한 Heartbeat 순번을 담은 요청
     * @return 정상 처리 시 body가 없는 204 응답
     * @throws WatchSessionException Redis 검증 결과 Heartbeat를 처리할 수 없는 경우
     */
    @PostMapping("/watch-sessions/{sessionKey}/heartbeat")
    public ResponseEntity<Void> heartbeat(
            @PathVariable @Positive(message = "사용자 ID는 1 이상이어야 합니다.") long userId,
            @PathVariable String sessionKey,
            @Valid @RequestBody HeartbeatRequest request
    ) {
        HeartbeatResult result = watchSessionService.heartbeat(userId, sessionKey, request.sequence());
        if (result != HeartbeatResult.SUCCESS) {
            throw new WatchSessionException(toError(result));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Redis Heartbeat 처리 결과를 API 오류 정책으로 변환한다.
     *
     * @param result 실패한 Redis Heartbeat 처리 결과
     * @return HTTP 상태와 메시지를 가진 시청 세션 오류
     * @throws IllegalStateException 성공 결과를 오류로 변환하려는 경우
     */
    private WatchSessionError toError(HeartbeatResult result) {
        return switch (result) {
            case SWITCHING -> WatchSessionError.WATCH_SESSION_SWITCHING;
            case REPLACED -> WatchSessionError.WATCH_SESSION_REPLACED;
            case EXPIRED -> WatchSessionError.WATCH_SESSION_EXPIRED;
            case SESSION_NOT_FOUND -> WatchSessionError.WATCH_SESSION_NOT_FOUND;
            case USER_MISMATCH -> WatchSessionError.WATCH_SESSION_USER_MISMATCH;
            case INVALID_SEQUENCE -> WatchSessionError.INVALID_HEARTBEAT_SEQUENCE;
            case SUCCESS -> throw new IllegalStateException("성공한 Heartbeat 결과는 오류로 변환할 수 없습니다.");
        };
    }
}
