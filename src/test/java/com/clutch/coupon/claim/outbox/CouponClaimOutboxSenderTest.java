package com.clutch.coupon.claim.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 발급 Outbox Kafka 전송기 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponClaimOutboxSenderTest {

    @Mock
    private CouponClaimOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private CouponClaimOutboxSender outboxSender;

    /**
     * Kafka 전송 성공 검증
     */
    @Test
    void sendMarksOutboxAsSent() {
        // given
        CouponClaimOutbox outbox =
                createOutbox();

        when(outboxRepository.findById(1L))
                .thenReturn(Optional.of(outbox));

        CompletableFuture<SendResult<String, String>>
                successFuture =
                CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(
                "coupon.claim.accepted",
                "10",
                "{\"claimId\":10}"
        )).thenReturn(successFuture);

        // when
        outboxSender.send(1L);

        // then
        verify(kafkaTemplate).send(
                "coupon.claim.accepted",
                "10",
                "{\"claimId\":10}"
        );

        assertThat(outbox.getStatus())
                .isEqualTo(CouponClaimOutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
        assertThat(outbox.getRetryCount()).isZero();
    }

    /**
     * Kafka 전송 실패 검증
     */
    @Test
    void sendIncreasesRetryCountWhenKafkaFails() {
        // given
        CouponClaimOutbox outbox =
                createOutbox();

        when(outboxRepository.findById(1L))
                .thenReturn(Optional.of(outbox));

        CompletableFuture<SendResult<String, String>>
                failedFuture =
                new CompletableFuture<>();

        failedFuture.completeExceptionally(
                new IllegalStateException("Kafka 전송 실패")
        );

        when(kafkaTemplate.send(
                "coupon.claim.accepted",
                "10",
                "{\"claimId\":10}"
        )).thenReturn(failedFuture);

        // when
        outboxSender.send(1L);

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(CouponClaimOutboxStatus.PENDING);
        assertThat(outbox.getSentAt()).isNull();
        assertThat(outbox.getRetryCount()).isEqualTo(1);
    }

    private CouponClaimOutbox createOutbox() {
        return CouponClaimOutbox.create(
                "123e4567-e89b-12d3-a456-426614174000",
                10L,
                "coupon.claim.accepted",
                "{\"claimId\":10}"
        );
    }
}