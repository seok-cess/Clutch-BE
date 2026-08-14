package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode
        .COUPON_EVENT_ITEM_NOT_AVAILABLE;

/**
 * 쿠폰 발급 항목 선택기
 */
@Component
@RequiredArgsConstructor
public class CouponClaimItemSelector {

    private final CouponEventItemRepository
            couponEventItemRepository;

    /**
     * 활성 쿠폰 이벤트 항목 선택
     *
     * @param couponEventId 쿠폰 이벤트 식별자
     * @param couponEventOccurrence 쿠폰 이벤트 회차
     * @param currentTime 현재 시각
     * @return 활성 쿠폰 이벤트 항목
     */
    public CouponEventItem select(
            Long couponEventId,
            CouponEventOccurrence couponEventOccurrence,
            LocalDateTime currentTime
    ) {
        long elapsedSeconds = Duration.between(
                couponEventOccurrence.getOpenedAt(),
                currentTime
        ).getSeconds();

        return couponEventItemRepository
                .findAllByCouponEventId(couponEventId)
                .stream()
                .filter(couponEventItem ->
                        couponEventItem.isAvailableAt(
                                elapsedSeconds
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new CouponClaimException(
                                COUPON_EVENT_ITEM_NOT_AVAILABLE
                        )
                );
    }
}