package com.clutch.watch.api.request;

import jakarta.validation.constraints.Positive;

/**
 * 시청 세션 Heartbeat 요청.
 *
 * @param sequence 프론트엔드가 1부터 증가시키는 Heartbeat 순번
 */
public record HeartbeatRequest(
        @Positive(message = "Heartbeat 순번은 1 이상이어야 합니다.")
        long sequence
) {
}
