package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.service.CouponClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_EVENT_NOT_FOUND;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_EXHAUSTED;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쿠폰 발급 요청 컨트롤러 테스트
 */
@WebMvcTest(CouponClaimController.class)
class CouponClaimControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long COUPON_EVENT_ID = 10L;
    private static final Long COUPON_EVENT_OCCURRENCE_ID = 15L;
    private static final Long COUPON_EVENT_ITEM_ID = 20L;

    /**
     * MVC 요청 실행기
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 쿠폰 발급 요청 서비스 모의 객체
     */
    @MockitoBean
    private CouponClaimService couponClaimService;

    /**
     * 정상 쿠폰 발급 요청 응답 검증
     */
    @Test
    void claimSucceeds() throws Exception {
        // given
        CouponClaimCreateResponse response =
                new CouponClaimCreateResponse(
                        100L,
                        200L,
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        COUPON_EVENT_ITEM_ID,
                        ClaimRequestStatus.SUCCEEDED,
                        LocalDateTime.of(
                                2026,
                                8,
                                12,
                                17,
                                0
                        )
                );

        when(couponClaimService.claim(
                USER_ID,
                COUPON_EVENT_ID,
                COUPON_EVENT_OCCURRENCE_ID
        )).thenReturn(response);

        // when, then
        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}"
                                        + "/occurrences/{occurrenceId}/claims",
                                COUPON_EVENT_ID,
                                COUPON_EVENT_OCCURRENCE_ID
                        )
                                .header("X-User-Id", USER_ID)

                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponId").value(200))
                .andExpect(jsonPath("$.claimId").value(100))
                .andExpect(jsonPath("$.couponEventId").value(10))
                .andExpect(
                        jsonPath("$.couponEventOccurrenceId")
                                .value(15)
                )
                .andExpect(jsonPath("$.couponEventItemId").value(20))
                .andExpect(
                        jsonPath("$.requestStatus")
                                .value("SUCCEEDED")
                );

        verify(couponClaimService).claim(
                USER_ID,
                COUPON_EVENT_ID,
                COUPON_EVENT_OCCURRENCE_ID
        );
    }

    /**
     * 사용자 식별 헤더 누락 응답 검증
     */
    @Test
    void claimFailsWhenUserIdHeaderIsMissing() throws Exception {
        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}"
                                        + "/occurrences/{occurrenceId}/claims",
                                COUPON_EVENT_ID,
                                COUPON_EVENT_OCCURRENCE_ID
                        )

                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                );
    }

    /**
     * 미존재 쿠폰 이벤트 응답 검증
     */
    @Test
    void claimFailsWhenEventDoesNotExist() throws Exception {
        when(couponClaimService.claim(
                USER_ID,
                COUPON_EVENT_ID,
                COUPON_EVENT_OCCURRENCE_ID
        )).thenThrow(
                new CouponClaimException(
                        COUPON_EVENT_NOT_FOUND
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}"
                                        + "/occurrences/{occurrenceId}/claims",
                                COUPON_EVENT_ID,
                                COUPON_EVENT_OCCURRENCE_ID
                        )
                                .header("X-User-Id", USER_ID)

                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("COUPON_EVENT_NOT_FOUND")
                );
    }

    /**
     * 쿠폰 재고 소진 응답 검증
     */
    @Test
    void claimFailsWhenStockIsExhausted() throws Exception {
        when(couponClaimService.claim(
                USER_ID,
                COUPON_EVENT_ID,
                COUPON_EVENT_OCCURRENCE_ID
        )).thenThrow(
                new CouponClaimException(
                        COUPON_STOCK_EXHAUSTED
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}"
                                        + "/occurrences/{occurrenceId}/claims",
                                COUPON_EVENT_ID,
                                COUPON_EVENT_OCCURRENCE_ID
                        )
                                .header("X-User-Id", USER_ID)

                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("COUPON_STOCK_EXHAUSTED")
                );
    }
}
