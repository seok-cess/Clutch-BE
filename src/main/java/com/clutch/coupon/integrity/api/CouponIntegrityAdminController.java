package com.clutch.coupon.integrity.api;

import com.clutch.coupon.integrity.api.dto.CouponIntegrityDetailResponse;
import com.clutch.coupon.integrity.api.dto.CouponIntegrityListResponse;
import com.clutch.coupon.integrity.api.dto.CouponIntegrityStartResponse;
import com.clutch.coupon.integrity.service.CouponIntegrityCheckService;
import com.clutch.wallet.web.CurrentAdminId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/integrity-checks")
@RequiredArgsConstructor
public class CouponIntegrityAdminController {
    private final CouponIntegrityCheckService checkService;

    @PostMapping
    public ResponseEntity<CouponIntegrityStartResponse> start(
            @CurrentAdminId Long adminId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(checkService.start(adminId));
    }

    @GetMapping
    public CouponIntegrityListResponse findAll(
            @CurrentAdminId Long adminId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return checkService.findAll(cursor, size);
    }

    @GetMapping("/{checkId}")
    public CouponIntegrityDetailResponse findById(
            @CurrentAdminId Long adminId,
            @PathVariable Long checkId
    ) {
        return checkService.findById(checkId);
    }
}
