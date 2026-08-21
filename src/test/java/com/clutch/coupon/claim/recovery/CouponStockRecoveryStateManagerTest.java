package com.clutch.coupon.claim.recovery;

import com.clutch.coupon.claim.exception.CouponClaimErrorCode;
import com.clutch.coupon.claim.exception.CouponClaimException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 쿠폰 재고 복구 상태 관리 테스트 */
class CouponStockRecoveryStateManagerTest {

    @Test
    void blocksClaimsWhileRedisIsUnavailable() {
        CouponStockRecoveryStateManager manager =
                new CouponStockRecoveryStateManager();

        manager.markUnavailable();

        assertThatThrownBy(manager::requireReady)
                .isInstanceOfSatisfying(
                        CouponClaimException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        CouponClaimErrorCode
                                                .COUPON_REDIS_UNAVAILABLE
                                )
                );
    }

    @Test
    void returnsToReadyAfterRecovery() {
        CouponStockRecoveryStateManager manager =
                new CouponStockRecoveryStateManager();

        manager.markUnavailable();
        assertThat(manager.beginRecovery()).isTrue();
        assertThat(manager.current())
                .isEqualTo(CouponStockRecoveryState.RECOVERING);

        manager.markReady();

        assertThat(manager.current())
                .isEqualTo(CouponStockRecoveryState.READY);
        manager.requireReady();
    }
}
