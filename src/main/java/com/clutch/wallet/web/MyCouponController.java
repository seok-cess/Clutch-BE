package com.clutch.wallet.web;

import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.service.CouponQueryService;
import com.clutch.wallet.web.dto.CouponPageResponse;
import com.clutch.wallet.web.dto.CouponResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyCouponController {

    private final CouponQueryService couponQueryService;

    public MyCouponController(CouponQueryService couponQueryService){
        this.couponQueryService = couponQueryService;
    }

    @GetMapping("/api/users/me/coupons")
    public CouponPageResponse getMyCoupons(
            @CurrentUserId Long userId,
            @RequestParam(required = false)UserCouponStatus status,
            @RequestParam(required = false)String cursor,
            @RequestParam(defaultValue = "20")int size
    ){
        return couponQueryService.getMyCoupons(userId, status, cursor, size);
    }

    @GetMapping("/api/users/me/coupons/{couponId}")
    public CouponResponse getMyCoupon(@CurrentUserId Long userId,
                                      @PathVariable Long couponId){
        return couponQueryService.getMyCoupon(userId, couponId);
    }

}
