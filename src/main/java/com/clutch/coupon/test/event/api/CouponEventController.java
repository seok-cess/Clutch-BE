package com.clutch.coupon.test.event.api;

import com.clutch.coupon.test.event.api.dto.CouponEventActivationResponse;
import com.clutch.coupon.test.event.service.CouponEventActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자가 현재 테스트 발급 가능한 쿠폰 이벤트를 조회하는 API. */
@RestController
@RequestMapping("/api/v1/coupon-events")
@RequiredArgsConstructor
public class CouponEventController {

    private final CouponEventActivationService activationService;

    /** 전체 경기와 무관하게 현재 열린 최신 테스트 이벤트를 조회한다. */
    @GetMapping("/active")
    public ResponseEntity<CouponEventActivationResponse> findActive() {
        return activationService.findActive()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
