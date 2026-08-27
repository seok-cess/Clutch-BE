package com.clutch.coupon.test.event.api;

import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
import com.clutch.coupon.test.event.api.dto.CouponEventActivationResponse;
import com.clutch.coupon.test.event.service.CouponEventActivationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponEventController.class)
class CouponEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponEventActivationService activationService;

    @Test
    void 활성_쿠폰_이벤트를_조회한다() throws Exception {
        when(activationService.findActive()).thenReturn(Optional.of(
                new CouponEventActivationResponse(
                        1L,
                        20L,
                        "테스트 쿠폰",
                        LocalDateTime.of(2026, 8, 18, 12, 0),
                        LocalDateTime.of(2026, 8, 18, 12, 1),
                        CouponEventOccurrenceStatus.OPEN,
                        99L,
                        true,
                        List.of()
                )
        ));

        mockMvc.perform(get("/api/v1/coupon-events/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponEventId").value(1))
                .andExpect(jsonPath("$.couponEventOccurrenceId").value(20))
                .andExpect(jsonPath("$.eventName").value("테스트 쿠폰"))
                .andExpect(jsonPath("$.occurrenceStatus").value("OPEN"))
                .andExpect(jsonPath("$.remainingQuantity").value(99))
                .andExpect(jsonPath("$.claimable").value(true));
    }

    @Test
    void 활성_쿠폰_이벤트가_없으면_204를_응답한다() throws Exception {
        when(activationService.findActive()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/coupon-events/active"))
                .andExpect(status().isNoContent());
    }
}
