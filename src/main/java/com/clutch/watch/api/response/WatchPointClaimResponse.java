package com.clutch.watch.api.response;

import com.clutch.watch.dto.WatchPointClaimResult;

/**
 * 시청 포인트 수령 완료 응답.
 *
 * @param rewardSequence 지급을 완료한 포인트 회차
 * @param awardedPoint 이번 회차에서 지급한 포인트
 * @param totalPoint 지급 후 사용자 총포인트
 * @param nextRewardSequence 다음 수령 대상 회차
 */
public record WatchPointClaimResponse(
        long rewardSequence,
        long awardedPoint,
        long totalPoint,
        long nextRewardSequence
) {

    /**
     * 서비스 포인트 수령 결과를 API 응답으로 변환한다.
     *
     * @param result 서비스 포인트 수령 결과
     * @return 클라이언트에 반환할 포인트 수령 응답
     */
    public static WatchPointClaimResponse from(WatchPointClaimResult result) {
        return new WatchPointClaimResponse(
                result.rewardSequence(),
                result.awardedPoint(),
                result.totalPoint(),
                result.nextRewardSequence()
        );
    }
}
