package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEventItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 이벤트 항목 저장소
 */
public interface CouponEventItemRepository
        extends JpaRepository<CouponEventItem, Long> {

    /**
     * 쿠폰 이벤트 항목 목록 조회
     *
     * @param couponEventId 쿠폰 이벤트 식별자
     * @return 쿠폰 이벤트 항목 목록
     */
    List<CouponEventItem> findAllByCouponEventId(
            Long couponEventId
    );

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
     /**
     * 쿠폰 발급 성공 수량을 재고 범위 안에서 원자적으로 증가시킨다.
     *
     * @param couponEventItemId 쿠폰 이벤트 항목 ID
     * @return 증가에 성공하면 1, 재고가 소진되어 증가하지 못하면 0
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update CouponEventItem item
               set item.successCount = item.successCount + 1
             where item.id = :couponEventItemId
               and item.successCount < item.quantity
            """)
    int increaseSuccessCountAtomically(
            @Param("couponEventItemId") Long couponEventItemId
    );

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
