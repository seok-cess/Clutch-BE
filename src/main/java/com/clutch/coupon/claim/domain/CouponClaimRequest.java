package com.clutch.coupon.claim.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 요청 엔티티
 */
@Getter
@Entity
@Table(name = "coupon_claim_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponClaimRequest {

    /**
     * 발급 요청 식별자
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_claim_request_id", nullable = false)
    private Long id;

    /**
     * 쿠폰 이벤트 식별자
     */
    @Column(name = "coupon_event_id", nullable = false)
    private Long couponEventId;

    /**
     * 쿠폰 이벤트 회차 식별자
     */
    @Column(name = "coupon_event_occurrence_id")
    private Long couponEventOccurrenceId;

    /**
     * 쿠폰 이벤트 항목 식별자
     */
    @Column(name = "coupon_event_item_id", nullable = false)
    private Long couponEventItemId;

    /**
     * 사용자 식별자
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 발급 요청 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 30)
    private ClaimRequestStatus requestStatus;

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
     * 쿠폰 발급 요청 생성자
     *
     * @param couponEventId     쿠폰 이벤트 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @param couponEventItemId       쿠폰 이벤트 항목 식별자
     * @param userId                  사용자 식별자
     */
    private CouponClaimRequest(
            Long couponEventId,
            Long couponEventOccurrenceId,
            Long couponEventItemId,
            Long userId
    ) {
        this.couponEventId = couponEventId;
        this.couponEventOccurrenceId = couponEventOccurrenceId;
        this.couponEventItemId = couponEventItemId;
        this.userId = userId;
        this.requestStatus = ClaimRequestStatus.PENDING;
    }

    /**
     * 쿠폰 발급 요청 생성 팩토리
     *
     * @param couponEventId     쿠폰 이벤트 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @param couponEventItemId       쿠폰 이벤트 항목 식별자
     * @param userId                  사용자 식별자
     * @return 쿠폰 발급 요청
     */
    public static CouponClaimRequest create(
            Long couponEventId,
            Long couponEventOccurrenceId,
            Long couponEventItemId,
            Long userId
    ) {
        return new CouponClaimRequest(
                couponEventId,
                couponEventOccurrenceId,
                couponEventItemId,
                userId
        );
    }

    /**
     * 발급 성공 상태 전이
     */
    public void succeed() {
        this.requestStatus = ClaimRequestStatus.SUCCEEDED;
    }

    /**
     * 발급 실패 상태 전이
     */
    public void fail() {
        this.requestStatus = ClaimRequestStatus.FAILED;
    }
}
