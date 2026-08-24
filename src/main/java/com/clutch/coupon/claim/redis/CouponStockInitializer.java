package com.clutch.coupon.claim.redis;

import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.wallet.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 새로 열린 쿠폰 이벤트의 Redis 재고를 준비한다. */
@Component
@RequiredArgsConstructor
public class CouponStockInitializer {

    private final CouponEventItemRepository couponEventItemRepository;
    private final UserCouponRepository userCouponRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 쿠폰 이벤트의 재고 키가 없을 때만 남은 수량으로 생성한다.
     *
     * <p>이미 발급이 진행 중인 이벤트의 재고를 덮어쓰지 않도록 SETNX를 사용한다.</p>
     *
     * @param couponEventId 쿠폰 이벤트 식별자
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public void initialize(Long couponEventId) {
        List<CouponEventItem> items = couponEventItemRepository
                .findAllByCouponEventId(couponEventId);

        for (CouponEventItem item : items) {
            stringRedisTemplate.opsForValue().setIfAbsent(
                    CouponClaimRedisKeys.stock(item.getId()),
                    String.valueOf(
                            item.getQuantity()
                                    - userCouponRepository
                                    .countByCouponEventItemId(item.getId())
                    )
            );
        }
    }
}
