package com.clutch.coupon.statistics.repository;

import java.time.LocalDateTime;

/** Kafka 발급 결과 처리 실패 한 건을 저장하기 위한 값 객체. */
public record CouponIssueProcessingError(
        String originalConsumerGroup,
        String originalTopic,
        int originalPartition,
        long originalOffset,
        String messageId,
        Long claimId,
        Long couponEventId,
        String exceptionType,
        String exceptionMessage,
        String payload,
        LocalDateTime originalOccurredAt
) {
}
