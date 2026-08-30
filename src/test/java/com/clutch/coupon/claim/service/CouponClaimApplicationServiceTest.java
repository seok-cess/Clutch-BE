package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.exception.CouponClaimException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_EXHAUSTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponClaimApplicationServiceTest {

    @Mock
    private CouponClaimService couponClaimService;

    @Mock
    private CouponClaimRejectionPublisher rejectionPublisher;

    private CouponClaimApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new CouponClaimApplicationService(
                couponClaimService,
                rejectionPublisher
        );
    }

    @Test
    void 성공한_신청은_거절_통계를_발행하지_않는다() {
        CouponClaimCreateResponse response = org.mockito.Mockito.mock(
                CouponClaimCreateResponse.class
        );
        when(couponClaimService.claim(1L, 10L, 20L))
                .thenReturn(response);

        assertThat(applicationService.claim(1L, 10L, 20L))
                .isSameAs(response);
        verifyNoInteractions(rejectionPublisher);
    }

    @Test
    void 품절_거절을_통계로_전달하고_기존_예외를_유지한다() {
        CouponClaimException rejection = new CouponClaimException(
                COUPON_STOCK_EXHAUSTED
        );
        when(couponClaimService.claim(1L, 10L, 20L))
                .thenThrow(rejection);

        assertThatThrownBy(() -> applicationService.claim(1L, 10L, 20L))
                .isSameAs(rejection);
        verify(rejectionPublisher).publish(
                10L,
                20L,
                COUPON_STOCK_EXHAUSTED
        );
    }

    @Test
    void 통계_발행이_실패해도_사용자_거절_응답은_바뀌지_않는다() {
        CouponClaimException rejection = new CouponClaimException(
                COUPON_STOCK_EXHAUSTED
        );
        when(couponClaimService.claim(1L, 10L, 20L))
                .thenThrow(rejection);
        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(rejectionPublisher)
                .publish(10L, 20L, COUPON_STOCK_EXHAUSTED);

        assertThatThrownBy(() -> applicationService.claim(1L, 10L, 20L))
                .isSameAs(rejection);
    }
}
