package com.clutch.wallet.web;

import com.clutch.wallet.service.AdminCouponService;
import com.clutch.wallet.web.dto.CancelCouponRequest;
import com.clutch.wallet.web.dto.CouponResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 쿠폰 취소 API를 제공하는 컨트롤러.
 */
@RestController
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    public AdminCouponController(AdminCouponService adminCouponService){
        this.adminCouponService = adminCouponService;
    }

    /**
     * 사용자 쿠폰을 관리자 권한으로 취소한다.
     *
     * @param adminId 요청한 관리자 ID
     * @param couponId 취소할 사용자 쿠폰 ID
     * @param request 취소 사유
     * @return 취소된 쿠폰 정보
     */
    @PostMapping("/api/admin/coupons/{couponId}/cancel")
    public CouponResponse cancelCoupon(@CurrentAdminId Long adminId,
                                       @PathVariable Long couponId,
                                       @RequestBody @Valid CancelCouponRequest request){
        return adminCouponService.cancel(couponId, request.reason());
    }
}
