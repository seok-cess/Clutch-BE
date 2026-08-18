package com.clutch.watch.dto.request;

import jakarta.validation.constraints.Positive;

/**
 * 현재 수령 가능 회차의 시청 포인트 수령 요청.
 *
 * @param rewardSequence 수령할 포인트 회차
 */
public record WatchPointClaimRequest(
        @Positive(message = "포인트 수령 회차는 1 이상이어야 합니다.")
        long rewardSequence
) {
}
