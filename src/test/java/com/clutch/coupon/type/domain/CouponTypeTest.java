package com.clutch.coupon.type.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTypeTest {

    @Test
    void 정률_쿠폰_종류를_활성_상태로_생성한다() {
        CouponType couponType = CouponType.create(
                "10% 할인 쿠폰",
                CouponDiscountType.RATE,
                BigDecimal.TEN
        );

        assertThat(couponType.getCouponName()).isEqualTo("10% 할인 쿠폰");
        assertThat(couponType.getDiscountType())
                .isEqualTo(CouponDiscountType.RATE);
        assertThat(couponType.getDiscountValue()).isEqualByComparingTo("10");
        assertThat(couponType.getStatus()).isEqualTo(CouponTypeStatus.ACTIVE);
    }

    @Test
    void 정률_할인은_100을_초과할_수_없다() {
        assertThatThrownBy(() -> CouponType.create(
                "101% 할인 쿠폰",
                CouponDiscountType.RATE,
                BigDecimal.valueOf(101)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정률 할인 값은 100 이하여야 합니다.");
    }

    @Test
    void 정액_할인은_100보다_클_수_있다() {
        CouponType couponType = CouponType.create(
                "5천원 할인 쿠폰",
                CouponDiscountType.AMOUNT,
                BigDecimal.valueOf(5_000)
        );

        assertThat(couponType.getDiscountValue())
                .isEqualByComparingTo("5000");
    }

    @Test
    void 할인_값은_0보다_커야_한다() {
        assertThatThrownBy(() -> CouponType.create(
                "잘못된 쿠폰",
                CouponDiscountType.AMOUNT,
                BigDecimal.ZERO
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("할인 값은 0보다 커야 합니다.");
    }

    @Test
    void 할인_값의_소수점이_두_자리를_넘으면_생성할_수_없다() {
        assertThatThrownBy(() -> CouponType.create(
                "잘못된 쿠폰",
                CouponDiscountType.AMOUNT,
                new BigDecimal("1000.001")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("할인 값은 정수 8자리와 소수 2자리 이하여야 합니다.");
    }

    @Test
    void 쿠폰_종류를_비활성화하고_다시_활성화한다() {
        CouponType couponType = CouponType.create(
                "상태 변경 쿠폰",
                CouponDiscountType.RATE,
                BigDecimal.TEN
        );

        couponType.deactivate();
        assertThat(couponType.isActive()).isFalse();

        couponType.activate();
        assertThat(couponType.isActive()).isTrue();
    }
}
