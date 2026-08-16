package com.clutch.coupon.event.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 이벤트 항목 테스트
 */
class CouponEventItemTest {

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
