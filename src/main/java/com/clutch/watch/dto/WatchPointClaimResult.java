package com.clutch.watch.dto;

/**
 * 포인트 수령 완료 후 API 계층에 전달할 결과.
 *
 * @param rewardSequence 지급을 완료한 포인트 회차
 * @param awardedPoint 이번 회차에서 지급한 포인트
 * @param totalPoint 지급 후 사용자 총포인트
 * @param nextRewardSequence 다음 수령 대상 회차
 */
public record WatchPointClaimResult(
        long rewardSequence,
        long awardedPoint,
        long totalPoint,
        long nextRewardSequence
) {
}
