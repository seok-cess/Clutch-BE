package com.clutch.coupon.claim.recovery;

import com.clutch.coupon.claim.exception.CouponClaimException;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_REDIS_UNAVAILABLE;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_RECOVERING;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_RECOVERY_FAILED;

/** 쿠폰 재고 복구 상태 관리 */
@Component
public class CouponStockRecoveryStateManager {

    private final AtomicReference<CouponStockRecoveryState> state =
            new AtomicReference<>(CouponStockRecoveryState.READY);

    /** 현재 복구 상태 */
    public CouponStockRecoveryState current() {
        return state.get();
    }

    /** 쿠폰 발급 가능 상태 검증 */
    public void requireReady() {
        switch (state.get()) {
            case READY -> {
            }
            case UNAVAILABLE -> throw new CouponClaimException(
                    COUPON_REDIS_UNAVAILABLE
            );
            case RECOVERING -> throw new CouponClaimException(
                    COUPON_STOCK_RECOVERING
            );
            case FAILED -> throw new CouponClaimException(
                    COUPON_STOCK_RECOVERY_FAILED
            );
        }
    }

    /** Redis 장애 상태 전이 */
    public void markUnavailable() {
        state.updateAndGet(current ->
                current == CouponStockRecoveryState.FAILED
                        ? current
                        : CouponStockRecoveryState.UNAVAILABLE
        );
    }

    /** 복구 시작 상태 전이 */
    public boolean beginRecovery() {
        CouponStockRecoveryState current = state.get();
        if (current == CouponStockRecoveryState.RECOVERING) {
            return false;
        }
        return state.compareAndSet(
                current,
                CouponStockRecoveryState.RECOVERING
        );
    }

    /** 복구 완료 상태 전이 */
    public void markReady() {
        state.set(CouponStockRecoveryState.READY);
    }

    /** 복구 실패 상태 전이 */
    public void markFailed() {
        state.set(CouponStockRecoveryState.FAILED);
    }
}
