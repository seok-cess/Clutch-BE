package com.clutch.coupon.contract.kafka;

/**
 * 쿠폰 Kafka 토픽 상수
 */
public final class CouponKafkaTopics {

    /**
     * 쿠폰 발급 접수 토픽
     */
    public static final String CLAIM_ACCEPTED = "coupon.claim.accepted";

    /**
     * 쿠폰 생성 결과 토픽
     */
    public static final String ISSUE_RESULT = "coupon.issue.result";

    /**
     * Redis 또는 발급 조건 검증에서 거절된 쿠폰 신청 토픽
     */
    public static final String CLAIM_REJECTED = "coupon.claim.rejected";

    private CouponKafkaTopics() {
    }
}
