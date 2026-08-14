package com.clutch.coupon.event.api;

import com.clutch.coupon.event.api.dto.CouponEventCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventDetailResponse;
import com.clutch.coupon.event.api.dto.CouponEventListResponse;
import com.clutch.coupon.event.api.dto.CouponEventUpdateRequest;
import com.clutch.coupon.event.api.dto.CouponEventUpdateResponse;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.service.CouponEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public CouponEventListResponse findAll(
            @RequestParam(required = false) CouponEventStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return couponEventService.findAll(status, cursor, size);
    }

    @GetMapping("/{couponEventId}")
    public CouponEventDetailResponse findById(
            @PathVariable Long couponEventId
    ) {
        return couponEventService.findById(couponEventId);
    }

    @PatchMapping("/{couponEventId}")
    public CouponEventUpdateResponse update(
            @PathVariable Long couponEventId,
            @Valid @RequestBody CouponEventUpdateRequest request
    ) {
        return couponEventService.update(couponEventId, request);
    }

    @DeleteMapping("/{couponEventId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long couponEventId
    ) {
        couponEventService.delete(couponEventId);
        return ResponseEntity.noContent().build();
    }
}
