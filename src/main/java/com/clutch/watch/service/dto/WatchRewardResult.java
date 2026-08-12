package com.clutch.watch.service.dto;

/**
 * 시청 세션 포인트 정산 결과.
 *
 * @param sessionKey 정산한 시청 세션 외부 식별자
 * @param eligibleMilliseconds 최종 유효 시청시간(milliseconds)
 * @param awardedMinutes 포인트 지급 대상으로 인정된 완료 분
 * @param awardedPoint 최종 지급 포인트
 * @param newlySettled 이번 요청에서 새로 정산했으면 true, 기존 정산 결과면 false
 */
public record WatchRewardResult(
        String sessionKey,
        long eligibleMilliseconds,
        long awardedMinutes,
        long awardedPoint,
        boolean newlySettled
) {
}
