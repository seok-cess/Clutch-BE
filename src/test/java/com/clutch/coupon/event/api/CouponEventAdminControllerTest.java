package com.clutch.coupon.event.api;

import com.clutch.coupon.event.api.dto.CouponEventCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateResponse;
import com.clutch.coupon.event.domain.CouponEventOpenMode;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;
import com.clutch.coupon.event.service.CouponEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
    private CouponEventService couponEventService;

    @Test
    void 쿠폰_이벤트를_등록하면_201을_응답한다() throws Exception {
        CouponEventCreateRequest request = new CouponEventCreateRequest(
                1L,
                "펜타킬 이벤트",
                CouponEventOpenMode.GAME_TRIGGERED,
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                90,
                null,
                List.of(
                        new CouponEventItemCreateRequest(1L, 5_000, 0),
                        new CouponEventItemCreateRequest(2L, 2_500, 30)
                )
        );
        CouponEventCreateResponse response = new CouponEventCreateResponse(
                10L,
                1L,
                "펜타킬 이벤트",
                CouponEventOpenMode.GAME_TRIGGERED,
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                CouponEventStatus.READY,
                90,
                null,
                null,
                List.of(
                        new CouponEventItemCreateResponse(
                                100L, 20L, 1L, 5_000, 0, 1, 0
                        ),
                        new CouponEventItemCreateResponse(
                                101L, 21L, 2L, 2_500, 0, 2, 30
                        )
                )
        );
        when(couponEventService.create(request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/coupon-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "esportsMatchId": 1,
                                  "eventName": "펜타킬 이벤트",
                                  "openMode": "GAME_TRIGGERED",
                                  "issueMode": "PHASED_FIRST_COME",
                                  "triggerType": "PENTA_KILL",
                                  "claimWindowSeconds": 90,
                                  "items": [
                                    {
                                      "couponTypeId": 1,
                                      "quantity": 5000,
                                      "openOffsetSeconds": 0
                                    },
                                    {
                                      "couponTypeId": 2,
                                      "quantity": 2500,
                                      "openOffsetSeconds": 30
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponEventId").value(10))
                .andExpect(jsonPath("$.eventStatus").value("READY"))
                .andExpect(jsonPath("$.items[1].openOffsetSeconds").value(30));

        verify(couponEventService).create(request);
    }

    @Test
    void 필수값이_없으면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/coupon-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_EVENT_CONFIGURATION"));
    }
}
