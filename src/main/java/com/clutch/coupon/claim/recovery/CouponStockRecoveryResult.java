package com.clutch.coupon.claim.recovery;

/** 쿠폰 재고 복구 결과 */
public record CouponStockRecoveryResult(
        CouponStockRecoveryState state,
        int recoveredOccurrences,
        int recoveredItems,
        int recoveredUsers
) {

    /** 빈 복구 결과 */
    public static CouponStockRecoveryResult empty(
            CouponStockRecoveryState state
    ) {
        return new CouponStockRecoveryResult(state, 0, 0, 0);
    }
}
