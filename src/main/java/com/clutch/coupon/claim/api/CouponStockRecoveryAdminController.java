package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.api.dto.CouponStockRecoveryResponse;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryService;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 쿠폰 재고 복구 API */
@RestController
@RequestMapping("/api/v1/admin/coupon-stock-recovery")
@RequiredArgsConstructor
public class CouponStockRecoveryAdminController {

    private final CouponStockRecoveryStateManager stateManager;
    private final CouponStockRecoveryService recoveryService;

    /** 쿠폰 재고 복구 상태 조회 */
    @GetMapping
    public CouponStockRecoveryResponse status() {
        return CouponStockRecoveryResponse.status(
                stateManager.current()
        );
    }

    /** 쿠폰 재고 복구 실행 */
    @PostMapping
    public CouponStockRecoveryResponse recover() {
        return CouponStockRecoveryResponse.from(
                recoveryService.recoverOpenOccurrences()
        );
    }
}
