package com.clutch.wallet.service;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.web.exception.CouponExpiredException;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCouponServiceTest {

    private static final Instant REFERENCE_TIME =
            Instant.parse("2026-08-21T12:00:00Z");

    @Mock
    private UserCouponRepository userCouponRepository;

    private AdminCouponService service;

    @BeforeEach
    void setUp() {
        service = new AdminCouponService(
                userCouponRepository,
                Clock.fixed(REFERENCE_TIME, ZoneOffset.UTC)
        );
    }

    @Test
    void 만료된_쿠폰은_취소하지_않고_만료예외를_반환한다() {
        UserCoupon expired = new UserCoupon(
                1L,
                2L,
                10L,
                null,
                100L,
                "CPN-EXPIRED-CANCEL",
                "RATE",
                new BigDecimal("50.00"),
                REFERENCE_TIME
        );
        when(userCouponRepository.cancel(
                3L,
                REFERENCE_TIME,
                "운영 취소"
        )).thenReturn(0);
        when(userCouponRepository.findById(3L))
                .thenReturn(Optional.of(expired));

        assertThrows(
                CouponExpiredException.class,
                () -> service.cancel(3L, "운영 취소")
        );

        verify(userCouponRepository).cancel(
                3L,
                REFERENCE_TIME,
                "운영 취소"
        );
    }
}
