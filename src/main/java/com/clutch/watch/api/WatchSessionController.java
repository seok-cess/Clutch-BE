package com.clutch.watch.api;

import com.clutch.watch.api.dto.HeartbeatRequest;
import com.clutch.watch.api.dto.HeartbeatResponse;
import com.clutch.watch.api.dto.WatchSessionStartResponse;
import com.clutch.watch.api.dto.WatchPointClaimRequest;
import com.clutch.watch.api.dto.WatchPointClaimResponse;
import com.clutch.watch.service.service.WatchPointClaimService;
import com.clutch.watch.service.service.WatchSessionService;
import com.clutch.watch.service.dto.WatchHeartbeatResult;
import com.clutch.watch.service.dto.WatchSessionStartResult;
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
    private final WatchPointClaimService watchPointClaimService;

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
        HttpStatus status = result.newlyCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(WatchSessionStartResponse.from(result));
    }

    /**
     * 시청 중인 세션의 Heartbeat를 처리한다.
     *
     * @param userId Heartbeat를 보낸 사용자 ID
     * @param sessionKey Heartbeat 대상 시청 세션 외부 식별자
     * @param request 증가한 Heartbeat 순번을 담은 요청
     * @return 현재 회차의 누적시간과 포인트 수령 가능 상태
     */
    @PostMapping("/watch-sessions/{sessionKey}/heartbeat")
    public ResponseEntity<HeartbeatResponse> heartbeat(
            @PathVariable @Positive(message = "사용자 ID는 1 이상이어야 합니다.") long userId,
            @PathVariable String sessionKey,
            @Valid @RequestBody HeartbeatRequest request
    ) {
        WatchHeartbeatResult result = watchSessionService.heartbeat(
                userId,
                sessionKey,
                request.sequence()
        );
        return ResponseEntity.ok(HeartbeatResponse.from(result));
    }

    /**
     * 5분 누적을 완료한 현재 회차의 시청 포인트를 수령한다.
     */
    @PostMapping("/watch-sessions/{sessionKey}/point-claims")
    public ResponseEntity<WatchPointClaimResponse> claimPoint(
            @PathVariable @Positive(message = "사용자 ID는 1 이상이어야 합니다.") long userId,
            @PathVariable String sessionKey,
            @Valid @RequestBody WatchPointClaimRequest request
    ) {
        return ResponseEntity.ok(WatchPointClaimResponse.from(
                watchPointClaimService.claim(userId, sessionKey, request.rewardSequence())
        ));
    }
}
