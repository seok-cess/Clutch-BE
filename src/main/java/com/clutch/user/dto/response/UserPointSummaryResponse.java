package com.clutch.user.dto.response;

import com.clutch.user.dto.UserPointSummary;

/** 포인트 모달의 개인 정보 탭 응답이다. */
public record UserPointSummaryResponse(
        long point,
        long predictionCount,
        long predictionSuccessCount,
        long maxEarnedPoint
) {

    public static UserPointSummaryResponse from(UserPointSummary summary) {
        return new UserPointSummaryResponse(
                summary.point(),
                summary.predictionCount(),
                summary.predictionSuccessCount(),
                summary.maxEarnedPoint()
        );
    }
}
