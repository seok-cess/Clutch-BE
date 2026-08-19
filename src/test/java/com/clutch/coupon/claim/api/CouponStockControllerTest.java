package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.api.dto.CouponStockResponse;
import com.clutch.coupon.claim.exception.CouponClaimErrorCode;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.service.CouponStockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 쿠폰 재고 조회와 SSE 컨트롤러 테스트 */
@WebMvcTest(CouponStockController.class)
class CouponStockControllerTest {

    private static final Long ITEM_ID = 101L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponStockService couponStockService;

    @Test
    void returnsCurrentStock() throws Exception {
        when(couponStockService.getStock(ITEM_ID))
                .thenReturn(CouponStockResponse.of(ITEM_ID, 5L));

        mockMvc.perform(get(
                        "/api/v1/coupon-event-items/{itemId}/stock",
                        ITEM_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponEventItemId").value(ITEM_ID))
                .andExpect(jsonPath("$.remainingStock").value(5))
                .andExpect(jsonPath("$.exhausted").value(false));
    }

    @Test
    void returnsZeroAsNormalExhaustedResponse() throws Exception {
        when(couponStockService.getStock(ITEM_ID))
                .thenReturn(CouponStockResponse.of(ITEM_ID, 0L));

        mockMvc.perform(get(
                        "/api/v1/coupon-event-items/{itemId}/stock",
                        ITEM_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingStock").value(0))
                .andExpect(jsonPath("$.exhausted").value(true));
    }

    @Test
    void returnsServiceUnavailableWhenRedisReadFails() throws Exception {
        when(couponStockService.getStock(ITEM_ID))
                .thenThrow(new CouponClaimException(
                        CouponClaimErrorCode.COUPON_STOCK_READ_FAILED
                ));

        mockMvc.perform(get(
                        "/api/v1/coupon-event-items/{itemId}/stock",
                        ITEM_ID
                ))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_STOCK_READ_FAILED"));
    }

}
