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

    /**
     * 이벤트의 가장 최근 발생 회차를 조회한다.
     *
     * @param couponEventId 쿠폰 이벤트 ID
     * @return 최근 발생 회차, 발생 이력이 없으면 빈 값
     */
    Optional<CouponEventOccurrence> findFirstByCouponEventIdOrderByIdDesc(
            Long couponEventId
    );

    /**
     * 이벤트가 한 번이라도 실제로 발생했는지 확인한다.
     *
     * @param couponEventId 쿠폰 이벤트 ID
     * @return 발생 이력이 있으면 {@code true}
     */
    boolean existsByCouponEventId(Long couponEventId);
}
