package com.clutch.wallet.service;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.web.dto.CouponResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponQueryServiceTest {

    private static final Instant REFERENCE_TIME =
            Instant.parse("2026-08-21T12:00:00Z");

    @Mock
    private UserCouponRepository userCouponRepository;

    private CouponQueryService service;

    @BeforeEach
    void setUp() {
        service = new CouponQueryService(
                userCouponRepository,
                Clock.fixed(REFERENCE_TIME, ZoneOffset.UTC)
        );
    }

    @Test
    void 단건응답은_시간상_만료된_ISSUED를_EXPIRED로_반환한다() {
        UserCoupon coupon = new UserCoupon(
                1L,
                2L,
                10L,
                null,
                100L,
                "CPN-EXPIRED-RESPONSE",
                "RATE",
                new BigDecimal("50.00"),
                REFERENCE_TIME
        );
        when(userCouponRepository.findByIdAndUserId(3L, 2L))
                .thenReturn(Optional.of(coupon));

        CouponResponse response = service.getMyCoupon(2L, 3L);

        assertEquals(UserCouponStatus.EXPIRED, response.status());
    }
}
