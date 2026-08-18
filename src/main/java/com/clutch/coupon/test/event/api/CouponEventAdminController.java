package com.clutch.coupon.test.event.api;

import com.clutch.coupon.test.event.api.dto.CouponEventActivationResponse;
import com.clutch.coupon.test.event.service.CouponEventActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자가 쿠폰 발급을 수동으로 테스트하는 API. */
@RestController("couponTestEventAdminController")
@RequestMapping("/api/v1/admin/coupon-events")
@RequiredArgsConstructor
public class CouponEventAdminController {

    private final CouponEventActivationService activationService;

    /** 경기 트리거와 무관하게 쿠폰 이벤트 회차를 즉시 연다. */
    @PostMapping("/{couponEventId}/occurrences/manual-open")
    public ResponseEntity<CouponEventActivationResponse> manualOpen(
            @PathVariable Long couponEventId
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(activationService.manualOpen(couponEventId));
    }
}
