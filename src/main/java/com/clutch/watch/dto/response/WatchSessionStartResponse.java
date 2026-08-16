package com.clutch.watch.dto.response;

import com.clutch.watch.dto.WatchSessionStartResult;

import java.time.Instant;

/**
 * 경기 시청 세션 입장 응답.
 *
 * @param sessionKey 생성된 시청 세션 외부 식별자
 * @param matchId 시청 경기 ID
 * @param enteredAt 서버가 확정한 입장 시각
 * @param heartbeatIntervalSeconds Heartbeat 전송 주기(초)
 * @param sessionTimeoutSeconds Heartbeat 중단을 판단하는 시간(초)
 * @param heartbeatSequence 마지막으로 처리한 Heartbeat 순번
 */
public record WatchSessionStartResponse(
        String sessionKey,
        long matchId,
        Instant enteredAt,
        long heartbeatIntervalSeconds,
        long sessionTimeoutSeconds,
        long heartbeatSequence
) {

    /**
     * 서비스 입장 결과를 API 응답으로 변환한다.
     *
     * @param result 시청 세션 서비스 입장 결과
     * @return 클라이언트에 반환할 입장 응답
     */
    public static WatchSessionStartResponse from(WatchSessionStartResult result) {
        return new WatchSessionStartResponse(
                result.sessionKey(),
                result.matchId(),
                result.enteredAt(),
                result.heartbeatIntervalSeconds(),
                result.sessionTimeoutSeconds(),
                result.heartbeatSequence()
        );
    }
}
