package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEventItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 이벤트 항목 저장소
 */
public interface CouponEventItemRepository
        extends JpaRepository<CouponEventItem, Long> {

    /**
     * 쿠폰 이벤트별 쿠폰 이벤트 항목 조회
     *
     * @param couponEventId     쿠폰 이벤트 식별자
     * @param couponEventItemId 쿠폰 이벤트 항목 식별자
     * @return 쿠폰 이벤트 항목
     */
    Optional<CouponEventItem> findByCouponEventIdAndId(
            Long couponEventId,
            Long couponEventItemId
    );

    List<CouponEventItem> findAllByCouponEventId(Long couponEventId);

    List<CouponEventItem> findAllByCouponEventIdIn(
            List<Long> couponEventIds
    );
}
