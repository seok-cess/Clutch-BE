package com.clutch.wallet.web;

import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.service.CouponQueryService;
import com.clutch.wallet.web.dto.CouponCursor;
import com.clutch.wallet.web.dto.CouponPageResponse;
import com.clutch.wallet.web.dto.CouponResponse;
import com.clutch.wallet.service.CouponUseService;
import com.clutch.wallet.web.exception.InvalidCouponQueryException;
import org.springframework.web.bind.annotation.*;

/**
 * 로그인한 사용자의 쿠폰 조회·사용 API를 제공하는 컨트롤러.
 */
@RestController
public class MyCouponController {

    private final CouponQueryService couponQueryService;
    private final CouponUseService couponUseService;

    public MyCouponController(CouponQueryService couponQueryService,
                              CouponUseService couponUseService){
        this.couponQueryService = couponQueryService;
        this.couponUseService = couponUseService;
    }

    /**
     * 내 쿠폰 목록을 커서 기반으로 조회한다.
     *
     * @param userId 요청한 사용자 ID
     * @param status 조회할 쿠폰 상태, 전체 조회 시 {@code null}
     * @param cursor 이전 페이지의 마지막 커서, 첫 조회 시 {@code null}
     * @param size 한 번에 조회할 쿠폰 수
     * @return 쿠폰 목록과 다음 커서 정보
     */
    @GetMapping("/api/users/me/coupons")
    public CouponPageResponse getMyCoupons(
            @CurrentUserId Long userId,
            @RequestParam(required = false)UserCouponStatus status,
            @RequestParam(required = false)String cursor,
            @RequestParam(defaultValue = "20")int size
    ){
        if(size < 1 || size > 100){
            throw new InvalidCouponQueryException("size는 1이상 100이하여야 합니다.");
        }
        CouponCursor parsedCursor = CouponCursor.parse(cursor);
        return couponQueryService.getMyCoupons(userId, status, parsedCursor.expiresAt(), parsedCursor.id(), size);
    }

    /**
     * 내 쿠폰을 단건 조회한다.
     *
     * @param userId 요청한 사용자 ID
     * @param couponId 조회할 쿠폰 ID
     * @return 조회된 쿠폰 정보
     */
    @GetMapping("/api/users/me/coupons/{couponId}")
    public CouponResponse getMyCoupon(@CurrentUserId Long userId,
                                      @PathVariable Long couponId){
        return couponQueryService.getMyCoupon(userId, couponId);
    }

    /**
     * 내 쿠폰을 사용 처리한다.
     *
     * @param userId 요청한 사용자 ID
     * @param couponId 사용할 쿠폰 ID
     * @return 사용 처리된 쿠폰 정보
     */
    @PostMapping("/api/users/me/coupons/{couponId}/use")
    public CouponResponse useCoupon(@CurrentUserId Long userId, @PathVariable Long couponId){
        return couponUseService.use(userId, couponId);
    }
}
