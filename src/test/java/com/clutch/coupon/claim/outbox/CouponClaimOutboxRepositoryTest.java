package com.clutch.coupon.claim.outbox;

import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 Outbox 저장소 테스트
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class CouponClaimOutboxRepositoryTest {

    /**
     * 쿠폰 발급 Outbox 저장소
     */
    @Autowired
    private CouponClaimOutboxRepository couponClaimOutboxRepository;

    /**
     * JPA 엔티티 관리자
     */
    @Autowired
    private EntityManager entityManager;

    /**
     * Outbox 저장 및 조회 검증
     */
    @Test
    void saveAndFindById() {
        // given
        String messageId = UUID.randomUUID().toString();
        String payload = """
                {
                  "claimId": 100
                }
                """;

        CouponClaimOutbox outbox =
                CouponClaimOutbox.create(
                        messageId,
                        100L,
                        CouponKafkaTopics.CLAIM_ACCEPTED,
                        payload
                );

        // when
        CouponClaimOutbox savedOutbox =
                couponClaimOutboxRepository.saveAndFlush(outbox);

        Long savedOutboxId = savedOutbox.getId();

        entityManager.clear();

        CouponClaimOutbox foundOutbox =
                couponClaimOutboxRepository
                        .findById(savedOutboxId)
                        .orElseThrow();

        // then
        assertThat(foundOutbox.getId()).isNotNull();
        assertThat(foundOutbox.getMessageId())
                .isEqualTo(messageId);
        assertThat(foundOutbox.getAggregateId())
                .isEqualTo(100L);
        assertThat(foundOutbox.getTopic())
                .isEqualTo(CouponKafkaTopics.CLAIM_ACCEPTED);
        assertThat(foundOutbox.getPayload())
                .contains("\"claimId\": 100");
        assertThat(foundOutbox.getStatus())
                .isEqualTo(CouponClaimOutboxStatus.PENDING);
        assertThat(foundOutbox.getRetryCount()).isZero();
        assertThat(foundOutbox.getCreatedAt()).isNotNull();
        assertThat(foundOutbox.getSentAt()).isNull();
    }
}