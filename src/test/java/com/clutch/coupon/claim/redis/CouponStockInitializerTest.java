package com.clutch.coupon.claim.redis;

import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 재고 Redis 초기화 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponStockInitializerTest {

    private static final Long COUPON_EVENT_ID = 10L;

    @Mock
    private CouponEventItemRepository
            couponEventItemRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CouponEventItem firstCouponEventItem;

    @Mock
    private CouponEventItem secondCouponEventItem;

    @InjectMocks
    private CouponStockInitializer couponStockInitializer;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue())
                .thenReturn(valueOperations);
    }

    /**
     * 쿠폰 이벤트 항목별 재고 초기화 검증
     */
    @Test
    void initializesEveryCouponEventItemStock() {
        when(couponEventItemRepository
                .findAllByCouponEventId(COUPON_EVENT_ID))
                .thenReturn(List.of(
                        firstCouponEventItem,
                        secondCouponEventItem
                ));

        when(firstCouponEventItem.getId())
                .thenReturn(100L);
        when(firstCouponEventItem.remainingStock())
                .thenReturn(9_000);

        when(secondCouponEventItem.getId())
                .thenReturn(200L);
        when(secondCouponEventItem.remainingStock())
                .thenReturn(900);

        couponStockInitializer.initialize(COUPON_EVENT_ID);

        verify(valueOperations).setIfAbsent(
                "coupon:event-item:100:stock",
                "9000"
        );

        verify(valueOperations).setIfAbsent(
                "coupon:event-item:200:stock",
                "900"
        );
    }
}