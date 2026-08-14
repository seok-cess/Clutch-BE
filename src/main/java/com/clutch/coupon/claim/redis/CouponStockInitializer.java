package com.clutch.coupon.claim.redis;

import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 쿠폰 재고 Redis 초기화
 */
@Component
@RequiredArgsConstructor
public class CouponStockInitializer {

    private final CouponEventItemRepository
            couponEventItemRepository;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 쿠폰 이벤트 재고 초기화
     *
     * @param couponEventId 쿠폰 이벤트 식별자
     */
    @Transactional(readOnly = true)
    public void initialize(Long couponEventId) {
        List<CouponEventItem> couponEventItems =
                couponEventItemRepository
                        .findAllByCouponEventId(couponEventId);

        for (CouponEventItem couponEventItem
                : couponEventItems) {
            initializeItem(couponEventItem);
        }
    }

    /**
     * 쿠폰 이벤트 항목 재고 초기화
     *
     * @param couponEventItem 쿠폰 이벤트 항목
     */
    private void initializeItem(
            CouponEventItem couponEventItem
    ) {
        String stockKey = CouponClaimRedisKeys.stock(
                couponEventItem.getId()
        );

        String remainingStock = String.valueOf(
                couponEventItem.remainingStock()
        );

        stringRedisTemplate
                .opsForValue()
                .setIfAbsent(stockKey, remainingStock);
    }
}