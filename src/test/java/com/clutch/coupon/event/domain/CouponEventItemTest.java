package com.clutch.coupon.event.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 이벤트 항목 테스트
 */
class CouponEventItemTest {

    @Test
    void couponItemIsAvailableOnlyWithinTimeWindow() {
        CouponEventItem couponEventItem =
                new CouponEventItem();

        ReflectionTestUtils.setField(
                couponEventItem,
                "availableFromSeconds",
                5
        );
        ReflectionTestUtils.setField(
                couponEventItem,
                "availableUntilSeconds",
                10
        );

        assertThat(couponEventItem.isAvailableAt(4))
                .isFalse();
        assertThat(couponEventItem.isAvailableAt(5))
                .isTrue();
        assertThat(couponEventItem.isAvailableAt(9))
                .isTrue();
        assertThat(couponEventItem.isAvailableAt(10))
                .isFalse();
    }
    /**
     * 잔여 수량 계산 검증
     */
    @Test
    void remainingStockIsQuantityMinusSuccessCount() {
        CouponEventItem couponEventItem =
                new CouponEventItem();

        ReflectionTestUtils.setField(
                couponEventItem,
                "quantity",
                100
        );
        ReflectionTestUtils.setField(
                couponEventItem,
                "successCount",
                20
        );

        assertThat(couponEventItem.remainingStock())
                .isEqualTo(80);
    }
}