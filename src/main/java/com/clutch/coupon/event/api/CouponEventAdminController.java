package com.clutch.coupon.event.api;

import com.clutch.coupon.event.api.dto.CouponEventCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventCreateResponse;
import com.clutch.coupon.event.service.CouponEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/coupon-events")
@RequiredArgsConstructor
public class CouponEventAdminController {

    private final CouponEventService couponEventService;

    @PostMapping
    public ResponseEntity<CouponEventCreateResponse> create(
            @Valid @RequestBody CouponEventCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(couponEventService.create(request));
    }
}
