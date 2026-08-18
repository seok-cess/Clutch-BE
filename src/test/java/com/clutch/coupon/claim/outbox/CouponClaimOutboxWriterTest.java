package com.clutch.coupon.claim.outbox;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.contract.kafka.CouponClaimAcceptedEvent;
import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 발급 Outbox 작성기 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponClaimOutboxWriterTest {

    @Mock
    private CouponClaimOutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CouponClaimOutboxWriter outboxWriter;

    /**
     * 쿠폰 발급 접수 이벤트 저장 검증
     */
    @Test
    void writeAcceptedEventSavesPendingOutbox()
            throws Exception {
        // given
        CouponClaimRequest claimRequest =
                claimRequest();

        CouponBenefitSnapshot benefitSnapshot =
                new CouponBenefitSnapshot(
                        "RATE",
                        new BigDecimal("20.00")
                );

        Instant occurredAt =
                Instant.parse("2026-08-17T00:00:00Z");

        when(objectMapper.writeValueAsString(
                any(CouponClaimAcceptedEvent.class)
        )).thenReturn("{\"claimId\":10}");

        // when
        outboxWriter.writeAcceptedEvent(
                claimRequest,
                benefitSnapshot,
                occurredAt
        );

        // then
        ArgumentCaptor<CouponClaimAcceptedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        CouponClaimAcceptedEvent.class
                );

        verify(objectMapper)
                .writeValueAsString(eventCaptor.capture());

        CouponClaimAcceptedEvent event =
                eventCaptor.getValue();

        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.messageId()).hasSize(36);
        assertThat(event.claimId()).isEqualTo(10L);
        assertThat(event.userId()).isEqualTo(20L);
        assertThat(event.couponEventId()).isEqualTo(30L);
        assertThat(event.couponEventOccurrenceId())
                .isEqualTo(40L);
        assertThat(event.couponEventItemId())
                .isEqualTo(50L);
        assertThat(event.discountType()).isEqualTo("RATE");
        assertThat(event.discountValue())
                .isEqualByComparingTo("20.00");
        assertThat(event.expiresAt())
                .isEqualTo(
                        occurredAt.plus(
                                7,
                                ChronoUnit.DAYS
                        )
                );
        assertThat(event.occurredAt())
                .isEqualTo(occurredAt);

        ArgumentCaptor<CouponClaimOutbox> outboxCaptor =
                ArgumentCaptor.forClass(
                        CouponClaimOutbox.class
                );

        verify(outboxRepository)
                .save(outboxCaptor.capture());

        CouponClaimOutbox outbox =
                outboxCaptor.getValue();

        assertThat(outbox.getMessageId())
                .isEqualTo(event.messageId());
        assertThat(outbox.getAggregateId()).isEqualTo(10L);
        assertThat(outbox.getTopic())
                .isEqualTo(CouponKafkaTopics.CLAIM_ACCEPTED);
        assertThat(outbox.getPayload())
                .isEqualTo("{\"claimId\":10}");
        assertThat(outbox.getStatus())
                .isEqualTo(CouponClaimOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isZero();
    }

    /**
     * 이벤트 직렬화 실패 검증
     */
    @Test
    void serializationFailureDoesNotSaveOutbox()
            throws Exception {
        // given
        CouponClaimRequest claimRequest =
                claimRequest();

        CouponBenefitSnapshot benefitSnapshot =
                new CouponBenefitSnapshot(
                        "RATE",
                        new BigDecimal("20.00")
                );

        Instant occurredAt =
                Instant.parse("2026-08-17T00:00:00Z");

        when(objectMapper.writeValueAsString(
                any(CouponClaimAcceptedEvent.class)
        )).thenThrow(
                new IllegalStateException("JSON 오류")
        );

        // when & then
        assertThatThrownBy(() ->
                outboxWriter.writeAcceptedEvent(
                        claimRequest,
                        benefitSnapshot,
                        occurredAt
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("쿠폰 발급 이벤트 직렬화 실패");

        verifyNoInteractions(outboxRepository);
    }

    private CouponClaimRequest claimRequest() {
        CouponClaimRequest claimRequest =
                org.mockito.Mockito.mock(
                        CouponClaimRequest.class
                );

        when(claimRequest.getId()).thenReturn(10L);
        when(claimRequest.getUserId()).thenReturn(20L);
        when(claimRequest.getCouponEventId()).thenReturn(30L);
        when(claimRequest.getCouponEventOccurrenceId())
                .thenReturn(40L);
        when(claimRequest.getCouponEventItemId())
                .thenReturn(50L);

        return claimRequest;
    }
}