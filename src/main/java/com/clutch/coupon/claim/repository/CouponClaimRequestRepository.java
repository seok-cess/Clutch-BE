package com.clutch.coupon.claim.repository;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 쿠폰 발급 요청 저장소
 */
public interface CouponClaimRequestRepository
        extends JpaRepository<CouponClaimRequest, Long> {

    /**
     * 사용자별 쿠폰 이벤트 회차 발급 요청 존재 여부
     *
     * @param userId            사용자 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @return 발급 요청 존재 여부
     */
    boolean existsByUserIdAndCouponEventOccurrenceId(
            Long userId,
            Long couponEventOccurrenceId
    );
}
