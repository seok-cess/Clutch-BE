package com.clutch.coupon.test.event.api;

import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
import com.clutch.coupon.test.event.api.dto.CouponEventActivationResponse;
import com.clutch.coupon.test.event.exception.CouponEventErrorCode;
import com.clutch.coupon.test.event.exception.CouponEventException;
import com.clutch.coupon.test.event.service.CouponEventActivationService;
import com.clutch.coupon.test.event.service.CouponEventTestCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponEventAdminController.class)
class CouponEventAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponEventActivationService activationService;

    @MockitoBean
    private CouponEventTestCleanupService cleanupService;

    @Test
    void 관리자_수동_오픈은_201을_응답한다() throws Exception {
        when(activationService.manualOpen(1L)).thenReturn(
                new CouponEventActivationResponse(
                        1L,
                        20L,
                        "테스트 쿠폰",
                        LocalDateTime.of(2026, 8, 18, 12, 0),
                        LocalDateTime.of(2026, 8, 18, 12, 1),
                        CouponEventOccurrenceStatus.OPEN,
                        100L,
                        true
                )
        );

        mockMvc.perform(post(
                        "/api/v1/admin/coupon-events/{eventId}"
                                + "/occurrences/manual-open",
                        1L
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponEventId").value(1))
                .andExpect(jsonPath("$.couponEventOccurrenceId").value(20))
                .andExpect(jsonPath("$.occurrenceStatus").value("OPEN"));

        verify(activationService).manualOpen(1L);
    }

    @Test
    void 이미_열린_이벤트를_수동_오픈하면_409를_응답한다()
            throws Exception {
        when(activationService.manualOpen(1L)).thenThrow(
                new CouponEventException(
                        CouponEventErrorCode.COUPON_EVENT_ALREADY_OPEN
                )
        );

        mockMvc.perform(post(
                        "/api/v1/admin/coupon-events/{eventId}"
                                + "/occurrences/manual-open",
                        1L
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_EVENT_ALREADY_OPEN"));
    }
}
