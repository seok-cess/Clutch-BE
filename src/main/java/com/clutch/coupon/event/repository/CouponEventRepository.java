package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 쿠폰 이벤트 저장소
 */
public interface CouponEventRepository
        extends JpaRepository<CouponEvent, Long> {

    boolean existsByEsportsMatchIdAndTriggerType(
            Long esportsMatchId,
            String triggerType
    );

    Slice<CouponEvent> findAllByOrderByIdDesc(Pageable pageable);

    Slice<CouponEvent> findByIdLessThanOrderByIdDesc(
            Long cursor,
            Pageable pageable
    );

    Slice<CouponEvent> findByEventStatusOrderByIdDesc(
            CouponEventStatus eventStatus,
            Pageable pageable
    );

    Slice<CouponEvent> findByEventStatusAndIdLessThanOrderByIdDesc(
            CouponEventStatus eventStatus,
            Long cursor,
            Pageable pageable
    );
}
