package com.clutch.watch.dto;

/**
 * DB에서 확정된 한 회차의 포인트 지급 결과.
 *
 * @param rewardSequence 지급을 확정한 포인트 회차
 * @param awardedPoint 이번 회차에서 지급한 포인트
 * @param totalPoint 지급 후 사용자 총포인트
 */
public record WatchPointAwardResult(
        long rewardSequence,
        long awardedPoint,
        long totalPoint
) {
}
