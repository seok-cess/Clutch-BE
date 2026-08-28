package com.clutch.coupon.statistics.kafka;

import java.time.LocalDateTime;

/** coupon.issue.result-dlt 메시지와 원본 처리 실패 메타데이터. */
public record CouponIssueResultDltRecord(
        String key,
        String payload,
        String originalConsumerGroup,
        String originalTopic,
        int originalPartition,
        long originalOffset,
        String exceptionType,
        String exceptionMessage,
        LocalDateTime originalOccurredAt
) {
}
