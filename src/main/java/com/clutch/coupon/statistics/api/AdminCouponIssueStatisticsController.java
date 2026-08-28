package com.clutch.coupon.statistics.api;

import com.clutch.coupon.statistics.api.dto.AdminCouponIssueStatisticsResponse;
import com.clutch.coupon.statistics.service.CouponIssueStatisticsService;
import com.clutch.wallet.web.CurrentAdminId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 쿠폰 발급 통계 대시보드 API. */
@RestController
@RequestMapping("/api/v1/admin/coupon-statistics")
@RequiredArgsConstructor
public class AdminCouponIssueStatisticsController {

    private final CouponIssueStatisticsService statisticsService;

    /** 전체 요약과 최근 이벤트별 발급 결과를 조회한다. */
    @GetMapping
    public AdminCouponIssueStatisticsResponse findAll(
            @CurrentAdminId Long adminId,
            @RequestParam(defaultValue = "20") int size
    ) {
        return statisticsService.findAll(size);
    }
}
