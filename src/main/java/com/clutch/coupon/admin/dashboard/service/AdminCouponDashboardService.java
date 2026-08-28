package com.clutch.coupon.admin.dashboard.service;

import com.clutch.coupon.admin.dashboard.dto.AdminCouponDashboardResponse;
import com.clutch.coupon.admin.dashboard.dto.AdminDashboardEventResponse;
import com.clutch.coupon.admin.dashboard.dto.CouponDashboardSummaryResponse;
import com.clutch.coupon.admin.dashboard.dto.DailyIssuanceResponse;
import com.clutch.coupon.admin.dashboard.dto.DashboardAlertResponse;
import com.clutch.coupon.admin.dashboard.repository.AdminCouponDashboardQueryRepository;
import com.clutch.coupon.admin.dashboard.repository.AdminCouponDashboardStockRepository;
import com.clutch.coupon.admin.dashboard.repository.AdminCouponDashboardStockException;
import com.clutch.coupon.admin.dashboard.repository.AdminDashboardEventRow;
import com.clutch.coupon.admin.dashboard.repository.CouponDashboardAggregateRow;
import com.clutch.coupon.admin.dashboard.repository.DailyIssuanceRow;
import com.clutch.coupon.admin.dashboard.repository.OpenEventItemRow;
import com.clutch.coupon.event.domain.CouponEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 페이지 운영 홈의 KST 날짜 범위, 통계, 차트, 알림과 이벤트 표를 조립한다.
 */
@Service
@RequiredArgsConstructor
public class AdminCouponDashboardService {

    private static final ZoneId OPERATION_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 30;
    private static final int DASHBOARD_EVENT_SIZE = 5;

    private final AdminCouponDashboardQueryRepository queryRepository;
    private final AdminCouponDashboardStockRepository stockRepository;
    private final Clock clock;

    /**
     * 관리자 운영 홈의 기준일 통계와 최근 발급 추이를 조회한다.
     *
     * @param requestedDate 운영 기준일, 값이 없으면 KST 오늘
     * @param requestedTrendDays 추이 일수, 값이 없으면 7일
     * @return 관리자 운영 홈 전용 대시보드 응답
     */
    @Transactional(readOnly = true)
    public AdminCouponDashboardResponse findDashboard(
            LocalDate requestedDate,
            Integer requestedTrendDays
    ) {
        LocalDate operationDate = requestedDate == null
                ? LocalDate.now(clock.withZone(OPERATION_ZONE))
                : requestedDate;
        int trendDays = requestedTrendDays == null
                ? DEFAULT_TREND_DAYS
                : requestedTrendDays;
        validateTrendDays(trendDays);

        LocalDateTime todayStartUtc = utcStartOf(operationDate);
        LocalDateTime tomorrowStartUtc = utcStartOf(operationDate.plusDays(1));
        LocalDate trendStartDate = operationDate.minusDays(trendDays - 1L);
        LocalDateTime trendStartUtc = utcStartOf(trendStartDate);

        CouponDashboardAggregateRow aggregate = queryRepository.findAggregate(
                todayStartUtc,
                tomorrowStartUtc
        );
        long openEventCount = queryRepository.countOpenEvents();
        List<OpenEventItemRow> openItems =
                queryRepository.findOpenEventItems();
        Set<Long> soldOutEventIds;
        try {
            soldOutEventIds = stockRepository.findSoldOutEventIds(openItems);
        } catch (AdminCouponDashboardStockException exception) {
            throw new AdminCouponDashboardUnavailableException(
                    exception.getMessage(),
                    exception
            );
        }

        CouponDashboardSummaryResponse summary =
                new CouponDashboardSummaryResponse(
                        openEventCount,
                        soldOutEventIds.size(),
                        aggregate.requestCount(),
                        aggregate.issuedCount(),
                        aggregate.failedCount(),
                        aggregate.pendingCount(),
                        successRate(aggregate)
                );
        List<DailyIssuanceResponse> trend = fillMissingDates(
                trendStartDate,
                operationDate,
                queryRepository.findDailyIssuance(
                        trendStartUtc,
                        tomorrowStartUtc
                )
        );
        List<AdminDashboardEventResponse> events = queryRepository
                .findDashboardEvents(DASHBOARD_EVENT_SIZE)
                .stream()
                .map(this::toEventResponse)
                .toList();

        return new AdminCouponDashboardResponse(
                LocalDateTime.ofInstant(clock.instant(), OPERATION_ZONE),
                summary,
                trend,
                alerts(summary),
                events
        );
    }

    private void validateTrendDays(int trendDays) {
        if (trendDays < 1 || trendDays > MAX_TREND_DAYS) {
            throw new AdminCouponDashboardBadRequestException(
                    "trendDays는 1 이상 30 이하여야 합니다."
            );
        }
    }

    private LocalDateTime utcStartOf(LocalDate date) {
        return LocalDateTime.ofInstant(
                date.atStartOfDay(OPERATION_ZONE).toInstant(),
                ZoneOffset.UTC
        );
    }

    private BigDecimal successRate(CouponDashboardAggregateRow aggregate) {
        long completedCount = aggregate.issuedCount()
                + aggregate.failedCount();
        if (completedCount == 0L) {
            return null;
        }
        return BigDecimal.valueOf(aggregate.issuedCount())
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(completedCount),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private List<DailyIssuanceResponse> fillMissingDates(
            LocalDate startDate,
            LocalDate endDate,
            List<DailyIssuanceRow> rows
    ) {
        Map<LocalDate, DailyIssuanceRow> rowByDate = new LinkedHashMap<>();
        for (DailyIssuanceRow row : rows) {
            rowByDate.put(row.date(), row);
        }

        List<DailyIssuanceResponse> result = new ArrayList<>();
        for (LocalDate date = startDate;
             !date.isAfter(endDate);
             date = date.plusDays(1)) {
            DailyIssuanceRow row = rowByDate.get(date);
            result.add(new DailyIssuanceResponse(
                    date,
                    row == null ? 0L : row.issuedCount(),
                    row == null ? 0L : row.failedCount()
            ));
        }
        return List.copyOf(result);
    }

    private List<DashboardAlertResponse> alerts(
            CouponDashboardSummaryResponse summary
    ) {
        List<DashboardAlertResponse> alerts = new ArrayList<>();
        if (summary.soldOutEventCount() > 0L) {
            alerts.add(new DashboardAlertResponse(
                    "SOLD_OUT",
                    "WARNING",
                    "재고가 소진된 이벤트가 있습니다.",
                    summary.soldOutEventCount(),
                    "/admin/coupon-events"
            ));
        }
        if (summary.todayFailedCount() > 0L) {
            alerts.add(new DashboardAlertResponse(
                    "ISSUANCE_FAILURE",
                    "ERROR",
                    "오늘 발급 실패가 발생했습니다.",
                    summary.todayFailedCount(),
                    "/admin/coupon-claims?requestStatus=FAILED"
            ));
        }
        return List.copyOf(alerts);
    }

    private AdminDashboardEventResponse toEventResponse(
            AdminDashboardEventRow row
    ) {
        String firstTeamName = teamName(row.firstTeamName());
        String secondTeamName = teamName(row.secondTeamName());
        LocalDate matchDate = row.scheduledAt() == null
                ? null
                : row.scheduledAt()
                        .atZone(ZoneOffset.UTC)
                        .withZoneSameInstant(OPERATION_ZONE)
                        .toLocalDate();
        long remainingQuantity = Math.max(
                0L,
                row.totalQuantity() - row.issuedQuantity()
        );
        return new AdminDashboardEventResponse(
                row.couponEventId(),
                row.eventName(),
                matchDate,
                firstTeamName + " vs " + secondTeamName,
                row.totalQuantity(),
                row.issuedQuantity(),
                remainingQuantity,
                row.eventStatus(),
                statusLabel(row.eventStatus())
        );
    }

    private String teamName(String teamName) {
        return teamName == null || teamName.isBlank()
                ? "미정"
                : teamName;
    }

    private String statusLabel(CouponEventStatus status) {
        return switch (status) {
            case READY -> "발동 대기";
            case OPEN -> "진행 중";
            case CLOSED -> "종료";
            case CANCELLED -> "취소";
        };
    }
}
