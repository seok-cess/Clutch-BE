package com.clutch.coupon.claim.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 Outbox 엔티티
 */
@Getter
@Entity
@Table(name = "coupon_claim_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponClaimOutbox {

    /**
     * Outbox 식별자
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * 이벤트 메시지 식별자
     */
    @Column(name = "message_id", nullable = false, length = 36)
    private String messageId;

    /**
     * 쿠폰 발급 요청 식별자
     */
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    /**
     * 발행 토픽명
     */
    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    /**
     * 이벤트 페이로드
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    /**
     * 발행 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponClaimOutboxStatus status;

    /**
     * 재시도 횟수
     */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /**
     * 생성 시각
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 발행 완료 시각
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    private CouponClaimOutbox(
            String messageId,
            Long aggregateId,
            String topic,
            String payload
    ) {
        this.messageId = messageId;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.payload = payload;
        this.status = CouponClaimOutboxStatus.PENDING;
        this.retryCount = 0;
    }

    /**
     * 쿠폰 발급 Outbox 생성
     *
     * @param messageId 이벤트 메시지 식별자
     * @param aggregateId 쿠폰 발급 요청 식별자
     * @param topic 발행 토픽명
     * @param payload 이벤트 페이로드
     * @return 쿠폰 발급 Outbox
     */
    public static CouponClaimOutbox create(
            String messageId,
            Long aggregateId,
            String topic,
            String payload
    ) {
        return new CouponClaimOutbox(
                messageId,
                aggregateId,
                topic,
                payload
        );
    }
    /**
     * 발행 완료 처리
     *
     * @param sentAt 발행 완료 시각
     */
    public void markSent(
            LocalDateTime sentAt
    ) {
        this.status = CouponClaimOutboxStatus.SENT;
        this.sentAt = sentAt;
    }

    /**
     * 발행 재시도 횟수 증가
     */
    public void increaseRetryCount() {
        this.retryCount++;
    }
}