package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.api.dto.CouponStockResponse;
import com.clutch.coupon.claim.service.CouponStockService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Redis 기반 쿠폰 잔여 재고 조회와 SSE 스트림 제공 */
@Validated
@RestController
@RequestMapping("/api/v1/coupon-event-items")
@RequiredArgsConstructor
public class CouponStockController {

    private final CouponStockService couponStockService;

    /** 현재 Redis 잔여 재고 조회 */
    @GetMapping("/{couponEventItemId}/stock")
    public ResponseEntity<CouponStockResponse> getStock(
            @PathVariable @Positive Long couponEventItemId
    ) {
        return ResponseEntity.ok(
                couponStockService.getStock(couponEventItemId)
        );
    }

}
