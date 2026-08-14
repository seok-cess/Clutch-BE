package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEventOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 쿠폰 이벤트 회차 저장소
 */
public interface CouponEventOccurrenceRepository
        extends JpaRepository<CouponEventOccurrence, Long> {

    /**
     * 쿠폰 이벤트별 회차 조회
     *
     * @param couponEventId 쿠폰 이벤트 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @return 쿠폰 이벤트 회차
     */
    Optional<CouponEventOccurrence> findByCouponEventIdAndId(
            Long couponEventId,
            Long couponEventOccurrenceId
    );

    Optional<CouponEventOccurrence> findFirstByCouponEventIdOrderByIdDesc(
            Long couponEventId
    );

    boolean existsByCouponEventId(Long couponEventId);
}
