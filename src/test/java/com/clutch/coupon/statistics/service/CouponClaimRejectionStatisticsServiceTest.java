package com.clutch.coupon.statistics.service;

import com.clutch.coupon.contract.kafka.CouponClaimRejectedEvent;
import com.clutch.coupon.statistics.repository.CouponClaimRejectionStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponClaimRejectionStatisticsServiceTest {

    @Mock
    private CouponClaimRejectionStatisticsRepository repository;

    private CouponClaimRejectionStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new CouponClaimRejectionStatisticsService(repository);
    }

    @Test
    void 유효한_거절_이벤트를_저장한다() {
        CouponClaimRejectedEvent event = event(1, "message-1");
        when(repository.record(event)).thenReturn(true);

        assertThat(service.record(event)).isTrue();
    }

    @Test
    void 지원하지_않는_버전은_거절한다() {
        assertThatThrownBy(() -> service.record(event(2, "message-2")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CouponClaimRejectedEvent event(int version, String messageId) {
        return new CouponClaimRejectedEvent(
                version,
                messageId,
                10L,
                20L,
                "COUPON_STOCK_EXHAUSTED",
                Instant.parse("2026-08-29T05:00:00Z")
        );
    }
}
