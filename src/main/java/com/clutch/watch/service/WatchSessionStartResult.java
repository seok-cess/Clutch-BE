package com.clutch.watch.service;

import java.time.Instant;

/**
 * 경기 시청 세션 입장 결과.
 *
 * @param sessionKey 생성된 시청 세션 외부 식별자
 * @param matchId 시청할 경기 ID
 * @param enteredAt 서버가 확정한 입장 시각
 * @param heartbeatIntervalSeconds 프론트엔드 heartbeat 전송 주기(초)
 * @param sessionTimeoutSeconds heartbeat 중단을 판단하는 시간(초)
 */
public record WatchSessionStartResult(
        String sessionKey,
        long matchId,
        Instant enteredAt,
        long heartbeatIntervalSeconds,
        long sessionTimeoutSeconds
) {
}
