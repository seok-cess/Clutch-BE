package com.clutch.coupon.type.api;

import com.clutch.coupon.type.api.dto.CouponTypeResponse;
import com.clutch.coupon.type.api.dto.CouponTypeUpdateRequest;
import com.clutch.coupon.type.domain.CouponDiscountType;
import com.clutch.coupon.type.domain.CouponTypeStatus;
import com.clutch.coupon.type.exception.CouponTypeErrorCode;
import com.clutch.coupon.type.exception.CouponTypeException;
import com.clutch.coupon.type.service.CouponTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponTypeAdminController.class)
class CouponTypeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponTypeService couponTypeService;

    @Test
    void 쿠폰_종류를_등록하면_201을_응답한다() throws Exception {
        when(couponTypeService.create(any())).thenReturn(response(
                1L,
                "20% 할인 쿠폰",
                CouponTypeStatus.ACTIVE,
                false
        ));

        mockMvc.perform(post("/api/v1/admin/coupon-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponName": "20% 할인 쿠폰",
                                  "discountType": "RATE",
                                  "discountValue": 20
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponTypeId").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 활성_쿠폰_종류_목록을_조회한다() throws Exception {
        when(couponTypeService.findAll(CouponTypeStatus.ACTIVE))
                .thenReturn(List.of(response(
                        1L,
                        "10% 할인 쿠폰",
                        CouponTypeStatus.ACTIVE,
                        true
                )));

        mockMvc.perform(get("/api/v1/admin/coupon-types")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].couponName")
                        .value("10% 할인 쿠폰"))
                .andExpect(jsonPath("$[0].used").value(true));
    }

    @Test
    void 사용된_쿠폰_종류의_혜택_수정은_409를_응답한다() throws Exception {
        when(couponTypeService.update(
                eq(1L),
                any(CouponTypeUpdateRequest.class)
        )).thenThrow(new CouponTypeException(
                CouponTypeErrorCode.COUPON_TYPE_NOT_EDITABLE
        ));

        mockMvc.perform(patch("/api/v1/admin/coupon-types/{couponTypeId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponName": "20% 할인 쿠폰",
                                  "discountType": "RATE",
                                  "discountValue": 20
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_TYPE_NOT_EDITABLE"));
    }

    @Test
    void 쿠폰_종류를_비활성화한다() throws Exception {
        when(couponTypeService.changeStatus(
                1L,
                CouponTypeStatus.INACTIVE
        )).thenReturn(response(
                1L,
                "10% 할인 쿠폰",
                CouponTypeStatus.INACTIVE,
                true
        ));

        mockMvc.perform(patch(
                        "/api/v1/admin/coupon-types/{couponTypeId}/status",
                        1L
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void 이름이_없으면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/coupon-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponName": " ",
                                  "discountType": "RATE",
                                  "discountValue": 20
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_COUPON_TYPE_CONFIGURATION"));
    }

    @Test
    void 쿠폰_종류를_삭제하면_204를_응답한다() throws Exception {
        mockMvc.perform(delete(
                        "/api/v1/admin/coupon-types/{couponTypeId}",
                        1L
                ))
                .andExpect(status().isNoContent());

        verify(couponTypeService).delete(1L);
    }

    private CouponTypeResponse response(
            Long id,
            String name,
            CouponTypeStatus status,
            boolean used
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 12, 0);
        return new CouponTypeResponse(
                id,
                name,
                CouponDiscountType.RATE,
                BigDecimal.TEN,
                status,
                used,
                now,
                now
        );
    }
}
