package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEventPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponEventPhaseRepository
        extends JpaRepository<CouponEventPhase, Long> {

    List<CouponEventPhase> findAllByCouponEventIdOrderByOpenOffsetSecondsAsc(
            Long couponEventId
    );

    Optional<CouponEventPhase>
    findFirstByCouponEventIdAndOpenOffsetSecondsLessThanEqualOrderByOpenOffsetSecondsDesc(
            Long couponEventId,
            int elapsedSeconds
    );
}
