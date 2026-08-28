package com.clutch.coupon.admin.dashboard.controller;

import com.clutch.coupon.admin.dashboard.dto.AdminCouponDashboardResponse;
import com.clutch.coupon.admin.dashboard.service.AdminCouponDashboardService;
import com.clutch.wallet.web.CurrentAdminId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 관리자 페이지 운영 홈의 쿠폰 통계·차트·이벤트 데이터를 제공한다. */
@RestController
@RequestMapping("/api/v1/admin/coupon-dashboard")
@RequiredArgsConstructor
public class AdminCouponDashboardController {

    private final AdminCouponDashboardService dashboardService;

    /**
     * 관리자 권한을 확인하고 운영 기준일의 쿠폰 대시보드를 조회한다.
     *
     * @param adminId X-User-Id 헤더로 확인된 관리자 ID
     * @param date 운영 기준일, 값이 없으면 KST 오늘
     * @param trendDays 추이 일수, 값이 없으면 7일이고 최대 30일
     * @return 관리자 페이지 운영 홈 전용 응답
     */
    @GetMapping
    public AdminCouponDashboardResponse findDashboard(
            @CurrentAdminId Long adminId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(required = false) Integer trendDays
    ) {
        return dashboardService.findDashboard(date, trendDays);
    }
}
