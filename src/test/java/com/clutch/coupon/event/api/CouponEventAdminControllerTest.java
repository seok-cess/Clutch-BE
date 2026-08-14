package com.clutch.coupon.event.api;

import com.clutch.coupon.event.api.dto.CouponEventCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventDetailResponse;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventListResponse;
import com.clutch.coupon.event.api.dto.CouponEventUpdateRequest;
import com.clutch.coupon.event.api.dto.CouponEventUpdateResponse;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;
import com.clutch.coupon.event.exception.CouponEventErrorCode;
import com.clutch.coupon.event.exception.CouponEventException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponEventAdminController.class)
class CouponEventAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponEventService couponEventService;

    @Test
    void 쿠폰_이벤트_상세를_조회하면_200을_응답한다() throws Exception {
        when(couponEventService.findById(1L)).thenReturn(
                new CouponEventDetailResponse(
                        1L,
                        10L,
                        "펜타킬 이벤트",
                        CouponIssueMode.PHASED_FIRST_COME,
                        "PENTA_KILL",
                        CouponEventStatus.READY,
                        90,
                        10_000,
                        1_000,
                        9_000,
                        null,
                        null,
                        List.of(),
                        null
                )
        );

        mockMvc.perform(get(
                        "/api/v1/admin/coupon-events/{eventId}",
                        1L
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponEventId").value(1))
                .andExpect(jsonPath("$.totalQuantity").value(10_000))
                .andExpect(jsonPath("$.remainingQuantity").value(9_000));

        verify(couponEventService).findById(1L);
    }

    @Test
    void 진행_중인_쿠폰_이벤트_수정은_409를_응답한다() throws Exception {
        org.mockito.Mockito.when(couponEventService.update(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(CouponEventUpdateRequest.class)
        )).thenThrow(new CouponEventException(
                CouponEventErrorCode.COUPON_EVENT_NOT_EDITABLE
        ));

        mockMvc.perform(patch("/api/v1/admin/coupon-events/{eventId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "esportsMatchId": 2,
                                  "eventName": "진행 이벤트",
                                  "issueMode": "PHASED_FIRST_COME",
                                  "triggerType": "FIRST_BLOOD",
                                  "claimWindowSeconds": 60,
                                  "items": [
                                    {
                                      "couponTypeId": 10,
                                      "quantity": 5000,
                                      "openOffsetSeconds": 0
                                    },
                                    {
                                      "couponTypeId": 20,
                                      "quantity": 1000,
                                      "openOffsetSeconds": 30
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_EVENT_NOT_EDITABLE"));
    }

    @Test
    void 존재하지_않는_쿠폰_이벤트_삭제는_404를_응답한다() throws Exception {
        org.mockito.Mockito.doThrow(new CouponEventException(
                CouponEventErrorCode.COUPON_EVENT_NOT_FOUND
        )).when(couponEventService).delete(999L);

        mockMvc.perform(delete(
                        "/api/v1/admin/coupon-events/{eventId}",
                        999L
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_EVENT_NOT_FOUND"));
    }

    @Test
    void 쿠폰_수량이_0이면_등록_요청은_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/coupon-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventName": "예약 이벤트",
                                  "issueMode": "SINGLE_FIRST_COME",
                                  "esportsMatchId": 1,
                                  "triggerType": "FIRST_BLOOD",
                                  "claimWindowSeconds": 60,
                                  "items": [
                                    {
                                      "couponTypeId": 1,
                                      "quantity": 0,
                                      "openOffsetSeconds": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_EVENT_CONFIGURATION"));
    }

    @Test
    void 쿠폰_이벤트를_물리_삭제하면_204를_응답한다() throws Exception {
        mockMvc.perform(delete(
                        "/api/v1/admin/coupon-events/{eventId}",
                        1L
                ))
                .andExpect(status().isNoContent());

        verify(couponEventService).delete(1L);
    }

    @Test
    void 삭제할_수_없는_이벤트는_409를_응답한다() throws Exception {
        org.mockito.Mockito.doThrow(new CouponEventException(
                CouponEventErrorCode.COUPON_EVENT_NOT_DELETABLE
        )).when(couponEventService).delete(1L);

        mockMvc.perform(delete(
                        "/api/v1/admin/coupon-events/{eventId}",
                        1L
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_EVENT_NOT_DELETABLE"));
    }

    @Test
    void 쿠폰_이벤트_설정을_수정한다() throws Exception {
        CouponEventUpdateRequest request = new CouponEventUpdateRequest(
                2L,
                "퍼블 이벤트",
                CouponIssueMode.PHASED_FIRST_COME,
                "FIRST_BLOOD",
                60,
                List.of(
                        new CouponEventItemCreateRequest(10L, 5_000, 0),
                        new CouponEventItemCreateRequest(20L, 1_000, 30)
                )
        );
        CouponEventUpdateResponse response = new CouponEventUpdateResponse(
                1L,
                2L,
                "퍼블 이벤트",
                CouponIssueMode.PHASED_FIRST_COME,
                "FIRST_BLOOD",
                CouponEventStatus.READY,
                60,
                null,
                List.of()
        );
        when(couponEventService.update(1L, request)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/coupon-events/{eventId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "esportsMatchId": 2,
                                  "eventName": "퍼블 이벤트",
                                  "issueMode": "PHASED_FIRST_COME",
                                  "triggerType": "FIRST_BLOOD",
                                  "claimWindowSeconds": 60,
                                  "items": [
                                    {
                                      "couponTypeId": 10,
                                      "quantity": 5000,
                                      "openOffsetSeconds": 0
                                    },
                                    {
                                      "couponTypeId": 20,
                                      "quantity": 1000,
                                      "openOffsetSeconds": 30
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponEventId").value(1))
                .andExpect(jsonPath("$.eventName").value("퍼블 이벤트"))
                .andExpect(jsonPath("$.triggerType").value("FIRST_BLOOD"));

        verify(couponEventService).update(1L, request);
    }

    @Test
    void 이벤트_목록을_상태와_커서로_조회한다() throws Exception {
        when(couponEventService.findAll(CouponEventStatus.READY, 100L, 10))
                .thenReturn(new CouponEventListResponse(
                        List.of(),
                        null,
                        false
                ));

        mockMvc.perform(get("/api/v1/admin/coupon-events")
                        .param("status", "READY")
                        .param("cursor", "100")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(couponEventService).findAll(
                CouponEventStatus.READY,
                100L,
                10
        );
    }

    @Test
    void 존재하지_않는_이벤트_상세는_404를_응답한다() throws Exception {
        when(couponEventService.findById(999L)).thenThrow(
                new CouponEventException(
                        CouponEventErrorCode.COUPON_EVENT_NOT_FOUND
                )
        );

        mockMvc.perform(get("/api/v1/admin/coupon-events/{eventId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_EVENT_NOT_FOUND"));
    }

    @Test
    void 쿠폰_이벤트를_등록하면_201을_응답한다() throws Exception {
        CouponEventCreateRequest request = new CouponEventCreateRequest(
                1L,
                "펜타킬 이벤트",
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                90,
                List.of(
                        new CouponEventItemCreateRequest(1L, 5_000, 0),
                        new CouponEventItemCreateRequest(2L, 2_500, 30)
                )
        );
        CouponEventCreateResponse response = new CouponEventCreateResponse(
                10L,
                1L,
                "펜타킬 이벤트",
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                CouponEventStatus.READY,
                90,
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
