package com.clutch.coupon.contract.kafka;

import java.time.Instant;

/**
 * Redis 재고 차감 전에 종료된 요청을 포함한 쿠폰 신청 거절 이벤트다.
 *
 * @param eventVersion 이벤트 계약 버전
 * @param messageId Kafka 재전달 중복 방지 식별자
 * @param couponEventId 요청한 쿠폰 이벤트 식별자
 * @param couponEventOccurrenceId 요청한 쿠폰 이벤트 회차 식별자
 * @param reason 거절 오류 코드
 * @param occurredAt 거절 발생 시각
 */
public record CouponClaimRejectedEvent(
        int eventVersion,
        String messageId,
        Long couponEventId,
        Long couponEventOccurrenceId,
        String reason,
        Instant occurredAt
) {
}
