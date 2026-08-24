package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEventItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
     * 여러 이벤트의 쿠폰 항목을 한 번에 조회한다.
     *
     * @param couponEventIds 쿠폰 이벤트 ID 목록
     * @return 해당 이벤트들에 속한 쿠폰 항목 목록
     */
    List<CouponEventItem> findAllByCouponEventIdIn(
            List<Long> couponEventIds
    );

    /**
     * 쿠폰 종류가 쿠폰 이벤트에서 사용된 적이 있는지 확인한다.
     *
     * @param couponTypeId 쿠폰 종류 ID
     * @return 이벤트 항목이 존재하면 {@code true}
     */
    boolean existsByCouponTypeId(Long couponTypeId);

    /**
     * 여러 쿠폰 종류 중 이벤트 사용 이력이 있는 ID만 조회한다.
     *
     * @param couponTypeIds 확인할 쿠폰 종류 ID 목록
     * @return 이벤트에서 사용된 쿠폰 종류 ID 집합
     */
    @Query("""
            select distinct item.couponTypeId
              from CouponEventItem item
             where item.couponTypeId in :couponTypeIds
            """)
    Set<Long> findUsedCouponTypeIds(
            @Param("couponTypeIds") Collection<Long> couponTypeIds
    );

    /**
     * 이벤트에 속한 모든 쿠폰 항목을 물리 삭제한다.
     *
     * @param couponEventId 삭제할 쿠폰 이벤트 ID
     */
    void deleteAllByCouponEventId(Long couponEventId);
}
