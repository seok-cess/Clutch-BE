package com.clutch.coupon.integrity.api;

import com.clutch.coupon.integrity.api.dto.CouponIntegrityStartResponse;
import com.clutch.coupon.integrity.domain.IntegrityExecutionStatus;
import com.clutch.coupon.integrity.service.CouponIntegrityCheckService;
import com.clutch.coupon.integrity.service.CouponIntegrityErrorCode;
import com.clutch.coupon.integrity.service.CouponIntegrityException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponIntegrityAdminController.class)
class CouponIntegrityAdminControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CouponIntegrityCheckService checkService;

    @Test
    void 실행을_접수하면_202를_응답한다() throws Exception {
        when(checkService.start(nullable(Long.class))).thenReturn(new CouponIntegrityStartResponse(
                15L, IntegrityExecutionStatus.RUNNING, LocalDateTime.of(2026, 8, 30, 5, 32)
        ));
        mockMvc.perform(post("/api/v1/admin/integrity-checks"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.checkId").value(15))
                .andExpect(jsonPath("$.executionStatus").value("RUNNING"));
    }

    @Test
    void 실행_중이면_409를_응답한다() throws Exception {
        when(checkService.start(nullable(Long.class))).thenThrow(new CouponIntegrityException(
                CouponIntegrityErrorCode.INTEGRITY_CHECK_ALREADY_RUNNING
        ));
        mockMvc.perform(post("/api/v1/admin/integrity-checks"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTEGRITY_CHECK_ALREADY_RUNNING"));
    }

    @Test
    void 상세가_없으면_404를_응답한다() throws Exception {
        when(checkService.findById(999L)).thenThrow(new CouponIntegrityException(
                CouponIntegrityErrorCode.INTEGRITY_CHECK_NOT_FOUND
        ));
        mockMvc.perform(get("/api/v1/admin/integrity-checks/{checkId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTEGRITY_CHECK_NOT_FOUND"));
    }
}
