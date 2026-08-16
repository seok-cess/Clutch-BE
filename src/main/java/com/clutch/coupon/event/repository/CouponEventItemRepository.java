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

    /**
     * 이벤트에 속한 모든 쿠폰 항목을 조회한다.
     *
     * @param couponEventId 쿠폰 이벤트 ID
     * @return 쿠폰 이벤트 항목 목록
     */
    List<CouponEventItem> findAllByCouponEventId(Long couponEventId);

    /**
     * 여러 이벤트의 쿠폰 항목을 한 번에 조회한다.
     *
     * @param couponEventIds 쿠폰 이벤트 ID 목록
     * @return 해당 이벤트들에 속한 쿠폰 항목 목록
     */
    List<CouponEventItem> findAllByCouponEventIdIn(
            List<Long> couponEventIds
    );

    /**
     * 이벤트에 속한 모든 쿠폰 항목을 물리 삭제한다.
     *
     * @param couponEventId 삭제할 쿠폰 이벤트 ID
     */
    void deleteAllByCouponEventId(Long couponEventId);
}
