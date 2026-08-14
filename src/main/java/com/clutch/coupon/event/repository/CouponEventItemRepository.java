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
     * 쿠폰 발급 성공 수량 원자적 증가
     *
     * @param couponEventItemId 쿠폰 이벤트 항목 식별자
     * @return 변경 행 수
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

    List<CouponEventItem> findAllByCouponEventIdIn(
            List<Long> couponEventIds
    );

    void deleteAllByCouponEventId(Long couponEventId);
}
