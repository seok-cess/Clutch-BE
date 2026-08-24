package com.clutch.coupon.claim.redis;

import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.wallet.repository.UserCouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponStockInitializerTest {

    private static final Long COUPON_EVENT_ID = 10L;
    private static final Long COUPON_EVENT_ITEM_ID = 20L;

    @Mock
    private CouponEventItemRepository couponEventItemRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void 없는_재고_키만_남은_수량으로_초기화한다() {
        CouponEventItem item = CouponEventItem.create(
                COUPON_EVENT_ID,
                30L,
                100
        );
        setId(item, COUPON_EVENT_ITEM_ID);

        when(couponEventItemRepository.findAllByCouponEventId(
                COUPON_EVENT_ID
        )).thenReturn(List.of(item));
        when(userCouponRepository.countByCouponEventItemId(
                COUPON_EVENT_ITEM_ID
        )).thenReturn(1L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        CouponStockInitializer initializer = new CouponStockInitializer(
                couponEventItemRepository,
                userCouponRepository,
                stringRedisTemplate
        );

        initializer.initialize(COUPON_EVENT_ID);

        verify(valueOperations).setIfAbsent(
                CouponClaimRedisKeys.stock(COUPON_EVENT_ITEM_ID),
                "99"
        );
    }

    private void setId(CouponEventItem item, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(
                item,
                "id",
                id
        );
    }
}
