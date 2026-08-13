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

    private CouponEventItem(
            Long couponEventId,
            Long couponTypeId,
            int quantity
    ) {
        if (couponEventId == null || couponEventId <= 0) {
            throw new IllegalArgumentException("쿠폰 이벤트 ID는 필수입니다.");
        }
        if (couponTypeId == null || couponTypeId <= 0) {
            throw new IllegalArgumentException("쿠폰 종류 ID는 필수입니다.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("쿠폰 수량은 1장 이상이어야 합니다.");
        }
        this.couponEventId = couponEventId;
        this.couponTypeId = couponTypeId;
        this.quantity = quantity;
        this.successCount = 0;
    }

    public static CouponEventItem create(
            Long couponEventId,
            Long couponTypeId,
            int quantity
    ) {
        return new CouponEventItem(couponEventId, couponTypeId, quantity);
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
