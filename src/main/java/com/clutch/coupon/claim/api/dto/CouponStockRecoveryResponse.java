package com.clutch.coupon.claim.api.dto;

import com.clutch.coupon.claim.recovery.CouponStockRecoveryResult;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryState;

/** 쿠폰 재고 복구 응답 */
public record CouponStockRecoveryResponse(
        CouponStockRecoveryState state,
        int recoveredOccurrences,
        int recoveredItems,
        int recoveredUsers
) {

    /** 현재 복구 상태 응답 */
    public static CouponStockRecoveryResponse status(
            CouponStockRecoveryState state
    ) {
        return new CouponStockRecoveryResponse(state, 0, 0, 0);
    }

    /** 복구 결과 응답 */
    public static CouponStockRecoveryResponse from(
            CouponStockRecoveryResult result
    ) {
        return new CouponStockRecoveryResponse(
                result.state(),
                result.recoveredOccurrences(),
                result.recoveredItems(),
                result.recoveredUsers()
        );
    }
}
