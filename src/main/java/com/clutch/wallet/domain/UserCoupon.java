package com.clutch.wallet.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_coupon")
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_coupon_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_event_id", nullable = false)
    private Long couponEventId;

    @Column(name = "coupon_event_occurrence_id")
    private Long couponEventOccurrenceId;

    @Column(name = "coupon_event_item_id", nullable = false)
    private Long couponEventItemId;

    @Column(name = "claim_id", nullable = false, unique = true)
    private Long claimId;

    @Column(name = "coupon_code", nullable = false, unique = true, length = 100)
    private String couponCode;

    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_status", nullable = false, length = 30)
    private UserCouponStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected UserCoupon() {}

    public UserCoupon(Long claimId, Long userId, Long couponEventId, Long couponEventOccurrenceId, Long couponEventItemId,
                      String couponCode, String discountType, BigDecimal discountValue, Instant expiresAt){
        this.claimId = claimId;
        this.userId = userId;
        this.couponEventId = couponEventId;
        this.couponEventOccurrenceId = couponEventOccurrenceId;
        this.couponEventItemId = couponEventItemId;
        this.couponCode = couponCode;
        this.status = UserCouponStatus.ISSUED;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public Long getUserId() { return userId; }
    public Long getCouponEventId() { return couponEventId; }
    public Long getCouponEventOccurrenceId() { return couponEventOccurrenceId; }
    public Long getCouponEventItemId() { return couponEventItemId; }
    public String getCouponCode() { return couponCode; }
    public UserCouponStatus getStatus() { return status; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
