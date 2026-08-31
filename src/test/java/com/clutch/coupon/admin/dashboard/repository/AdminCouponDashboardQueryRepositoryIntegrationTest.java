package com.clutch.coupon.admin.dashboard.repository;

import com.clutch.coupon.contract.kafka.CouponClaimRejectedEvent;
import com.clutch.coupon.statistics.repository.CouponClaimRejectionStatisticsRepository;
import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false",
        "coupon.success-count-sync.enabled=false"
})
class AdminCouponDashboardQueryRepositoryIntegrationTest {

    private static final long REJECTION_EVENT_ID = 9_900_001L;

    private static final LocalDateTime START_UTC =
            LocalDateTime.of(2088, 3, 14, 15, 0);
    private static final LocalDateTime END_UTC =
            LocalDateTime.of(2088, 3, 15, 15, 0);
    private static final LocalDate TARGET_DATE =
            LocalDate.of(2088, 3, 15);

    @Autowired
    private CouponClaimRejectionStatisticsRepository rejectionRepository;

    @Autowired
    private AdminCouponDashboardQueryRepository dashboardRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    private String messageId;

    @AfterEach
    void tearDown() {
        if (messageId != null) {
            jdbcTemplate.update("""
                    DELETE FROM coupon_claim_rejection_message
                     WHERE message_id = :messageId
                    """, new MapSqlParameterSource(
                    "messageId",
                    messageId
            ));
            jdbcTemplate.update("""
                    DELETE FROM coupon_issue_daily_statistics
                     WHERE coupon_event_id = :couponEventId
                       AND statistics_date = :statisticsDate
                    """, new MapSqlParameterSource()
                    .addValue("couponEventId", REJECTION_EVENT_ID)
                    .addValue("statisticsDate", TARGET_DATE));
        }
    }

    @Test
    void Redis에서_거절된_요청을_전체요청과_실패추이에_포함한다() {
        CouponDashboardAggregateRow before = dashboardRepository
                .findAggregate(START_UTC, END_UTC);
        long dailyFailuresBefore = dailyFailures();

        messageId = UUID.randomUUID().toString();
        CouponClaimRejectedEvent event = new CouponClaimRejectedEvent(
                1,
                messageId,
                REJECTION_EVENT_ID,
                9_900_002L,
                "COUPON_STOCK_EXHAUSTED",
                Instant.parse("2088-03-15T01:00:00Z")
        );

        assertThat(rejectionRepository.record(event)).isTrue();
        assertThat(rejectionRepository.record(event)).isFalse();

        CouponDashboardAggregateRow aggregate = dashboardRepository
                .findAggregate(START_UTC, END_UTC);
        assertThat(aggregate.requestCount())
                .isEqualTo(before.requestCount() + 1);
        assertThat(aggregate.issuedCount())
                .isEqualTo(before.issuedCount());
        assertThat(aggregate.failedCount())
                .isEqualTo(before.failedCount() + 1);
        assertThat(aggregate.pendingCount())
                .isEqualTo(before.pendingCount());
        assertThat(dailyFailures()).isEqualTo(dailyFailuresBefore + 1);
    }

    private long dailyFailures() {
        return dashboardRepository.findDailyIssuance(START_UTC, END_UTC)
                .stream()
                .filter(row -> row.date().equals(TARGET_DATE))
                .mapToLong(DailyIssuanceRow::failedCount)
                .sum();
    }
}
