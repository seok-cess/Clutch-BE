package com.clutch.wallet.web;

import com.clutch.wallet.service.AdminCouponService;
import com.clutch.wallet.web.dto.CancelCouponRequest;
import com.clutch.wallet.web.dto.CouponResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    public AdminCouponController(AdminCouponService adminCouponService){
        this.adminCouponService = adminCouponService;
    }

    @PostMapping("/api/admin/coupons/{couponId}/cancel")
    public CouponResponse cancelCoupon(@CurrentAdminId Long adminId,
                                       @PathVariable Long couponId,
                                       @RequestBody @Valid CancelCouponRequest request){
        return adminCouponService.cancel(couponId, request.reason());
    }
}
