package com.clutch.coupon.claim.redis;

import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 재고 Redis 초기화 통합 테스트
 */
@SpringBootTest
class CouponStockInitializerIntegrationTest {

    private static final Long COUPON_EVENT_ID = 9_999_990L;
    private static final Long COUPON_EVENT_ITEM_ID = 9_999_991L;

    private static final String STOCK_KEY =
            "coupon:event-item:9999991:stock";

    @Autowired
    private CouponStockInitializer couponStockInitializer;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private CouponEventItemRepository
            couponEventItemRepository;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.delete(STOCK_KEY);
    }

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete(STOCK_KEY);
    }

    /**
     * 기존 Redis 재고 보존 검증
     */
    @Test
    void initializationDoesNotOverwriteExistingStock() {
        CouponEventItem couponEventItem =
                mock(CouponEventItem.class);

        when(couponEventItem.getId())
                .thenReturn(COUPON_EVENT_ITEM_ID);
        when(couponEventItem.remainingStock())
                .thenReturn(10);

        when(couponEventItemRepository
                .findAllByCouponEventId(COUPON_EVENT_ID))
                .thenReturn(List.of(couponEventItem));

        couponStockInitializer.initialize(COUPON_EVENT_ID);

        assertThat(stringRedisTemplate
                .opsForValue()
                .get(STOCK_KEY))
                .isEqualTo("10");

        stringRedisTemplate
                .opsForValue()
                .decrement(STOCK_KEY, 6);

        couponStockInitializer.initialize(COUPON_EVENT_ID);

        assertThat(stringRedisTemplate
                .opsForValue()
                .get(STOCK_KEY))
                .isEqualTo("4");
    }
}