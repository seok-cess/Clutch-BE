package com.clutch.coupon.statistics.api;

import com.clutch.coupon.statistics.api.dto.AdminCouponIssueStatisticsResponse;
import com.clutch.coupon.statistics.api.dto.AdminCouponIssueStatisticsSummaryResponse;
import com.clutch.coupon.statistics.service.CouponIssueStatisticsService;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCouponIssueStatisticsController.class)
class AdminCouponIssueStatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponIssueStatisticsService statisticsService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void 관리자는_쿠폰_발급_통계를_조회한다() throws Exception {
        User admin = User.create(UserRole.ADMIN, "admin@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(statisticsService.findAll(30)).thenReturn(
                new AdminCouponIssueStatisticsResponse(
                        new AdminCouponIssueStatisticsSummaryResponse(
                                10,
                                8,
                                2,
                                1,
                                0,
                                LocalDateTime.of(2026, 8, 28, 14, 0)
                        ),
                        List.of()
                )
        );

        mockMvc.perform(get("/api/v1/admin/coupon-statistics")
                        .header("X-User-Id", "1")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalResultCount").value(10))
                .andExpect(jsonPath("$.summary.successCount").value(8))
                .andExpect(jsonPath("$.summary.failureCount").value(2))
                .andExpect(jsonPath("$.summary.processingErrorCount").value(1))
                .andExpect(jsonPath("$.events").isArray());

        verify(statisticsService).findAll(30);
    }

    @Test
    void 일반_사용자는_쿠폰_통계를_조회할_수_없다() throws Exception {
        User user = User.create(UserRole.USER, "user@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/admin/coupon-statistics")
                        .header("X-User-Id", "2"))
                .andExpect(status().isForbidden());
    }
}
