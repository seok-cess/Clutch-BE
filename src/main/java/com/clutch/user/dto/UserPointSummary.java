package com.clutch.user.dto;

/** 포인트 모달의 개인 정보 탭에 필요한 조회 결과다. */
public record UserPointSummary(
        long point,
        long predictionCount,
        long predictionSuccessCount,
        long maxEarnedPoint
) {
}
