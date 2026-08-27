package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.api.dto.AdminCouponClaimListResponse;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.service.AdminCouponClaimService;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.web.CurrentAdminId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 관리자 쿠폰 발급 내역 조회 API를 제공한다.
 *
 * <p>관리자 권한을 확인한 뒤 이벤트, 트리거, 사용자, 상태, 쿠폰 종류,
 * 요청 기간을 조합하여 발급 요청과 실제 발급 결과를 조회한다.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/coupon-claims")
@RequiredArgsConstructor
public class AdminCouponClaimController {

    private final AdminCouponClaimService adminCouponClaimService;

    /**
     * 발급 요청을 이벤트·트리거·사용자·상태·기간 조건으로 조회한다.
     *
     * <p>숫자로만 구성된 {@code eventKeyword}는 이벤트 ID로 정확히
     * 일치 검색하고, 그 외 값은 이벤트 이름 부분 일치로 검색한다.</p>
     *
     * @param adminId 요청 헤더에서 확인된 관리자 사용자 ID
     * @param eventKeyword 이벤트 ID 또는 이벤트 이름 검색어
     * @param triggerKeyword 경기 트리거 문자열 검색어
     * @param userId 발급을 요청한 사용자 ID
     * @param requestStatus 발급 요청 처리 상태
     * @param couponStatus 실제 발급 쿠폰의 유효 상태
     * @param couponTypeId 쿠폰 종류 ID
     * @param from 발급 요청 조회 시작 시각
     * @param to 발급 요청 조회 종료 시각
     * @param cursor 이전 페이지의 마지막 발급 요청 ID
     * @param size 한 페이지에서 조회할 내역 수
     * @return 필터가 적용된 관리자 발급 내역 커서 페이지
     */
    @GetMapping
    public AdminCouponClaimListResponse findAll(
            @CurrentAdminId Long adminId,
            @RequestParam(required = false) String eventKeyword,
            @RequestParam(required = false) String triggerKeyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) ClaimRequestStatus requestStatus,
            @RequestParam(required = false) UserCouponStatus couponStatus,
            @RequestParam(required = false) Long couponTypeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminCouponClaimService.findAll(
                eventKeyword,
                triggerKeyword,
                userId,
                requestStatus,
                couponStatus,
                couponTypeId,
                from,
                to,
                cursor,
                size
        );
    }
}
