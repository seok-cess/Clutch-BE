package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.api.dto.AdminCouponClaimListResponse;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.service.AdminCouponClaimService;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import com.clutch.wallet.domain.UserCouponStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCouponClaimController.class)
class AdminCouponClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminCouponClaimService adminCouponClaimService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void 관리자는_트리거_문자열과_필터로_발급_내역을_조회한다()
            throws Exception {
        User admin = User.create(UserRole.ADMIN, "admin@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminCouponClaimService.findAll(
                "펜타킬",
                "PENTA",
                10L,
                ClaimRequestStatus.SUCCEEDED,
                UserCouponStatus.ISSUED,
                20L,
                null,
                null,
                100L,
                30
        )).thenReturn(new AdminCouponClaimListResponse(
                List.of(),
                null,
                false
        ));

        mockMvc.perform(get("/api/v1/admin/coupon-claims")
                        .header("X-User-Id", "1")
                        .param("eventKeyword", "펜타킬")
                        .param("triggerKeyword", "PENTA")
                        .param("userId", "10")
                        .param("requestStatus", "SUCCEEDED")
                        .param("couponStatus", "ISSUED")
                        .param("couponTypeId", "20")
                        .param("cursor", "100")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claims").isArray())
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(adminCouponClaimService).findAll(
                "펜타킬",
                "PENTA",
                10L,
                ClaimRequestStatus.SUCCEEDED,
                UserCouponStatus.ISSUED,
                20L,
                null,
                null,
                100L,
                30
        );
    }

    @Test
    void 일반_사용자는_관리자_발급_내역을_조회할_수_없다()
            throws Exception {
        User user = User.create(UserRole.USER, "user@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/admin/coupon-claims")
                        .header("X-User-Id", "2"))
                .andExpect(status().isForbidden());
    }
}
