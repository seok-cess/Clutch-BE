package com.clutch.coupon.admin.dashboard.service;

import com.clutch.coupon.admin.dashboard.dto.AdminCouponDashboardResponse;
import com.clutch.coupon.admin.dashboard.repository.AdminCouponDashboardQueryRepository;
import com.clutch.coupon.admin.dashboard.repository.AdminCouponDashboardStockRepository;
import com.clutch.coupon.admin.dashboard.repository.AdminDashboardEventRow;
import com.clutch.coupon.admin.dashboard.repository.CouponDashboardAggregateRow;
import com.clutch.coupon.admin.dashboard.repository.DailyIssuanceRow;
import com.clutch.coupon.admin.dashboard.repository.OpenEventItemRow;
import com.clutch.coupon.event.domain.CouponEventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 관리자 페이지 운영 홈 서비스의 날짜·집계·표시 계약을 검증한다. */
@ExtendWith(MockitoExtension.class)
class AdminCouponDashboardServiceTest {

    private static final Instant REFERENCE_TIME =
            Instant.parse("2026-08-28T02:30:00Z");

    @Mock
    private AdminCouponDashboardQueryRepository queryRepository;

    @Mock
    private AdminCouponDashboardStockRepository stockRepository;

    private AdminCouponDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminCouponDashboardService(
                queryRepository,
                stockRepository,
                Clock.fixed(REFERENCE_TIME, ZoneOffset.UTC)
        );
    }

    @Test
    void KST_기준일과_빈_날짜를_포함한_운영_홈을_반환한다() {
        LocalDateTime todayStartUtc =
                LocalDateTime.of(2026, 8, 27, 15, 0);
        LocalDateTime tomorrowStartUtc =
                LocalDateTime.of(2026, 8, 28, 15, 0);
        LocalDateTime trendStartUtc =
                LocalDateTime.of(2026, 8, 21, 15, 0);
        List<OpenEventItemRow> openItems = List.of(
                new OpenEventItemRow(10L, 100L),
                new OpenEventItemRow(11L, 101L)
        );

        when(queryRepository.findAggregate(
                todayStartUtc,
                tomorrowStartUtc
        )).thenReturn(new CouponDashboardAggregateRow(200, 142, 12, 4));
        when(queryRepository.countOpenEvents()).thenReturn(3L);
        when(queryRepository.findOpenEventItems()).thenReturn(openItems);
        when(stockRepository.findSoldOutEventIds(openItems))
                .thenReturn(Set.of(10L));
        when(queryRepository.findDailyIssuance(
                trendStartUtc,
                tomorrowStartUtc
        )).thenReturn(List.of(
                new DailyIssuanceRow(LocalDate.of(2026, 8, 22), 84, 5),
                new DailyIssuanceRow(LocalDate.of(2026, 8, 28), 142, 12)
        ));
        when(queryRepository.findDashboardEvents(5)).thenReturn(List.of(
                new AdminDashboardEventRow(
                        12L,
                        "결승전 쿠폰",
                        CouponEventStatus.READY,
                        LocalDateTime.of(2026, 8, 28, 15, 30),
                        "T1",
                        "Gen.G",
                        1000,
                        100
                )
        ));

        AdminCouponDashboardResponse response =
                service.findDashboard(LocalDate.of(2026, 8, 28), 7);

        assertThat(response.generatedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 28, 11, 30));
        assertThat(response.summary().openEventCount()).isEqualTo(3);
        assertThat(response.summary().soldOutEventCount()).isEqualTo(1);
        assertThat(response.summary().todayRequestCount()).isEqualTo(200);
        assertThat(response.summary().todaySuccessRate())
                .isEqualByComparingTo(new BigDecimal("92.21"));
        assertThat(response.issuanceTrend()).hasSize(7);
        assertThat(response.issuanceTrend().get(1).date())
                .isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(response.issuanceTrend().get(1).issuedCount()).isZero();
        assertThat(response.alerts()).extracting("type")
                .containsExactly("SOLD_OUT", "ISSUANCE_FAILURE");
        assertThat(response.events().getFirst().matchDate())
                .isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(response.events().getFirst().matchName())
                .isEqualTo("T1 vs Gen.G");
        assertThat(response.events().getFirst().statusLabel())
                .isEqualTo("발동 대기");

        verify(queryRepository).findAggregate(
                todayStartUtc,
                tomorrowStartUtc
        );
        verify(queryRepository).findDailyIssuance(
                trendStartUtc,
                tomorrowStartUtc
        );
    }

    @Test
    void 완료된_요청이_없으면_성공률은_null이다() {
        when(queryRepository.findAggregate(
                LocalDateTime.of(2026, 8, 27, 15, 0),
                LocalDateTime.of(2026, 8, 28, 15, 0)
        )).thenReturn(new CouponDashboardAggregateRow(4, 0, 0, 4));
        when(queryRepository.countOpenEvents()).thenReturn(0L);
        when(queryRepository.findOpenEventItems()).thenReturn(List.of());
        when(stockRepository.findSoldOutEventIds(List.of()))
                .thenReturn(Set.of());
        when(queryRepository.findDailyIssuance(
                LocalDateTime.of(2026, 8, 21, 15, 0),
                LocalDateTime.of(2026, 8, 28, 15, 0)
        )).thenReturn(List.of());
        when(queryRepository.findDashboardEvents(5)).thenReturn(List.of());

        AdminCouponDashboardResponse response =
                service.findDashboard(null, null);

        assertThat(response.summary().todaySuccessRate()).isNull();
        assertThat(response.issuanceTrend()).hasSize(7);
        assertThat(response.alerts()).isEmpty();
    }

    @Test
    void 추이_기간은_1일부터_30일까지만_허용한다() {
        assertThatThrownBy(() -> service.findDashboard(null, 0))
                .isInstanceOf(AdminCouponDashboardBadRequestException.class);
        assertThatThrownBy(() -> service.findDashboard(null, 31))
                .isInstanceOf(AdminCouponDashboardBadRequestException.class);
    }
}
