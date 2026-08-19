package com.clutch.coupon.claim.api.dto;

import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.domain.CouponClaimRequest;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 요청 생성 응답 DTO
 *
 * @param claimId 쿠폰 발급 요청 식별자
 * @param couponId 발급 쿠폰 식별자
 * @param couponEventId 쿠폰 이벤트 식별자
 * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
 * @param couponEventItemId 쿠폰 이벤트 항목 식별자
 * @param requestStatus 쿠폰 발급 요청 상태
 * @param createdAt 요청 생성 일시
 */
public record CouponClaimCreateResponse(
        Long claimId,
        Long couponId,
        Long couponEventId,
        Long couponEventOccurrenceId,
        Long couponEventItemId,
        ClaimRequestStatus requestStatus,
        LocalDateTime createdAt
) {

    /**
     * 쿠폰 발급 요청 생성 응답 변환
     *
     * @param claimRequest 쿠폰 발급 요청 엔티티
     * @param couponId 발급 쿠폰 식별자
     * @return 쿠폰 발급 요청 생성 응답
     */
    public static CouponClaimCreateResponse from(
            CouponClaimRequest claimRequest,
            Long couponId
    ) {
        return new CouponClaimCreateResponse(
                claimRequest.getId(),
                couponId,
                claimRequest.getCouponEventId(),
                claimRequest.getCouponEventOccurrenceId(),
                claimRequest.getCouponEventItemId(),
                claimRequest.getRequestStatus(),
                claimRequest.getCreatedAt()
        );
    }
}