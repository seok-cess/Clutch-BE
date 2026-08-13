package com.clutch.coupon.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 쿠폰 이벤트 항목 엔티티
 */
@Getter
@Entity
@Table(name = "coupon_event_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEventItem {

    /**
     * 쿠폰 이벤트 항목 식별자
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_event_item_id", nullable = false)
    private Long id;

    /**
     * 쿠폰 이벤트 식별자
     */
    @Column(name = "coupon_event_id", nullable = false)
    private Long couponEventId;

    /**
     * 쿠폰 유형 식별자
     */
    @Column(name = "coupon_type_id", nullable = false)
    private Long couponTypeId;

    /**
     * 총 발급 가능 수량
     */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * 발급 성공 수량
     */
    @Column(name = "success_count", nullable = false)
    private int successCount;

    /**
     * 활성 시작 초
     */
    @Column(name = "available_from_seconds", nullable = false)
    private int availableFromSeconds;

    /**
     * 활성 종료 초
     */
    @Column(name = "available_until_seconds", nullable = false)
    private int availableUntilSeconds;

    /**
     * 생성 시각
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 시각
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 활성 시간 포함 여부
     *
     * @param elapsedSeconds 회차 시작 후 경과 초
     * @return 활성 시간 포함 여부
     */
    public boolean isAvailableAt(long elapsedSeconds) {
        return availableFromSeconds <= elapsedSeconds
                && elapsedSeconds < availableUntilSeconds;
    }

    /**
     * 잔여 수량 존재 여부
     *
     * @return 잔여 수량 존재 여부
     */
    public boolean hasRemainingStock() {
        return successCount < quantity;
    }

    /**
     * 발급 성공 수량 증가
     */
    public void increaseSuccessCount() {
        if (!hasRemainingStock()) {
            throw new IllegalStateException("쿠폰 재고 소진");
        }

        successCount++;

    }
}
