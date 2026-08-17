package com.clutch.coupon.contract.kafka;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CouponKafkaContractTest {

    @Test
    void topicNamesAreFixed() {
        assertThat(CouponKafkaTopics.CLAIM_ACCEPTED)
                .isEqualTo("coupon.claim.accepted");
        assertThat(CouponKafkaTopics.ISSUE_RESULT)
                .isEqualTo("coupon.issue.result");
    }

    @Test
    void claimAcceptedEventContainsAgreedValues() {
        Instant expiresAt = Instant.parse("2026-08-24T00:00:00Z");
        Instant occurredAt = Instant.parse("2026-08-17T00:00:00Z");

        CouponClaimAcceptedEvent event = new CouponClaimAcceptedEvent(
                1,
                "message-1",
                10L,
                20L,
                30L,
                40L,
                50L,
                "RATE",
                new BigDecimal("20.00"),
                expiresAt,
                occurredAt
        );

        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.messageId()).isEqualTo("message-1");
        assertThat(event.claimId()).isEqualTo(10L);
        assertThat(event.userId()).isEqualTo(20L);
        assertThat(event.couponEventId()).isEqualTo(30L);
        assertThat(event.couponEventOccurrenceId()).isEqualTo(40L);
        assertThat(event.couponEventItemId()).isEqualTo(50L);
        assertThat(event.discountType()).isEqualTo("RATE");
        assertThat(event.discountValue()).isEqualByComparingTo("20.00");
        assertThat(event.expiresAt()).isEqualTo(expiresAt);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void issueResultEventContainsAgreedValues() {
        Instant occurredAt = Instant.parse("2026-08-17T00:00:01Z");

        CouponIssueResultEvent event = new CouponIssueResultEvent(
                1,
                "message-2",
                10L,
                100L,
                CouponIssueResultStatus.SUCCEEDED,
                null,
                occurredAt
        );

        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.messageId()).isEqualTo("message-2");
        assertThat(event.claimId()).isEqualTo(10L);
        assertThat(event.couponId()).isEqualTo(100L);
        assertThat(event.status())
                .isEqualTo(CouponIssueResultStatus.SUCCEEDED);
        assertThat(event.failureReason()).isNull();
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }
}