package com.clutch.coupon.admin.dashboard.controller;

import com.clutch.coupon.admin.dashboard.dto.AdminCouponDashboardResponse;
import com.clutch.coupon.admin.dashboard.dto.CouponDashboardSummaryResponse;
import com.clutch.coupon.admin.dashboard.service.AdminCouponDashboardService;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 페이지 운영 홈 API의 권한과 요청 계약을 검증한다. */
@WebMvcTest(AdminCouponDashboardController.class)
class AdminCouponDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminCouponDashboardService dashboardService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void 관리자는_기준일과_추이_기간으로_운영_홈을_조회한다() throws Exception {
        User admin = User.create(UserRole.ADMIN, "admin@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(dashboardService.findDashboard(
                LocalDate.of(2026, 8, 28),
                7
        )).thenReturn(response());

        mockMvc.perform(get("/api/v1/admin/coupon-dashboard")
                        .header("X-User-Id", "1")
                        .param("date", "2026-08-28")
                        .param("trendDays", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.openEventCount").value(3))
                .andExpect(jsonPath("$.summary.todaySuccessRate").value(92.21))
                .andExpect(jsonPath("$.issuanceTrend").isArray())
                .andExpect(jsonPath("$.alerts").isArray())
                .andExpect(jsonPath("$.events").isArray());

        verify(dashboardService).findDashboard(
                LocalDate.of(2026, 8, 28),
                7
        );
    }

    @Test
    void 일반_사용자는_관리자_운영_홈을_조회할_수_없다() throws Exception {
        User user = User.create(UserRole.USER, "user@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/admin/coupon-dashboard")
                        .header("X-User-Id", "2"))
                .andExpect(status().isForbidden());
    }

    private AdminCouponDashboardResponse response() {
        return new AdminCouponDashboardResponse(
                LocalDateTime.of(2026, 8, 28, 11, 30),
                new CouponDashboardSummaryResponse(
                        3,
                        1,
                        158,
                        142,
                        12,
                        4,
                        new java.math.BigDecimal("92.21")
                ),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
