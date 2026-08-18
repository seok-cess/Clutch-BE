package com.clutch.coupon.test.event.repository;

import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
import com.clutch.coupon.test.event.domain.CouponEventOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 수동 발급 테스트용 쿠폰 이벤트 회차 저장소. */
@Repository("couponTestEventOccurrenceRepository")
public interface CouponEventOccurrenceRepository
        extends JpaRepository<CouponEventOccurrence, Long> {

    Optional<CouponEventOccurrence>
            findFirstByCouponEventIdAndOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                    Long couponEventId,
                    CouponEventOccurrenceStatus occurrenceStatus,
                    LocalDateTime openedAt,
                    LocalDateTime expiresAt
            );

    Optional<CouponEventOccurrence>
            findFirstByOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                    CouponEventOccurrenceStatus occurrenceStatus,
                    LocalDateTime openedAt,
                    LocalDateTime expiresAt
            );

    List<CouponEventOccurrence>
            findAllByOccurrenceStatusAndClosedAtIsNullAndExpiresAtLessThanEqual(
                    CouponEventOccurrenceStatus occurrenceStatus,
                    LocalDateTime expiresAt
            );
}
