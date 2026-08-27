package com.clutch.wallet.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 사용자에게 발급된 쿠폰.
 *
 * <p>발급 시 {@link UserCouponStatus#ISSUED} 상태로 생성되며,
 * 이후 사용 또는 취소를 통해 상태가 전이된다.</p>
 */
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

    /**
     * 발급 요청을 기준으로 {@code ISSUED} 상태의 사용자 쿠폰을 생성한다.
     *
     * @param claimId 쿠폰 발급 요청(claim) 식별자
     * @param userId 발급 대상 사용자 ID
     * @param couponEventId 쿠폰 이벤트 ID
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 ID
     * @param couponEventItemId 발급된 쿠폰 종류(이벤트 항목) ID
     * @param couponCode 발급된 쿠폰 코드
     * @param discountType 할인 유형
     * @param discountValue 할인 값
     * @param expiresAt 만료 시각
     */
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

    /**
     * 저장된 상태와 만료 시각을 함께 반영한 외부 노출 상태를 반환한다.
     *
     * <p>사용 및 취소처럼 명시적으로 종료된 상태는 그대로 유지한다.
     * 발급 상태인 쿠폰만 기준 시각에 만료됐으면 {@link UserCouponStatus#EXPIRED}로
     * 해석하며, 이 계산을 위해 DB 상태를 일괄 갱신하지 않는다.</p>
     *
     * @param referenceTime 만료 여부를 판단할 UTC 기준 시각
     * @return 기준 시각에 유효한 쿠폰 상태
     */
    public UserCouponStatus getEffectiveStatus(Instant referenceTime) {
        if (status == UserCouponStatus.ISSUED
                && !expiresAt.isAfter(referenceTime)) {
            return UserCouponStatus.EXPIRED;
        }
        return status;
    }
}
