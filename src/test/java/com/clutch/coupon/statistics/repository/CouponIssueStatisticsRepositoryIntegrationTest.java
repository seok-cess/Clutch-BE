package com.clutch.coupon.statistics.repository;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultStatus;
import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false",
        "coupon.success-count-sync.enabled=false"
})
class CouponIssueStatisticsRepositoryIntegrationTest {

    @Autowired
    private CouponIssueStatisticsRepository statisticsRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    private long couponEventId;

    @BeforeEach
    void setUp() {
        couponEventId = ThreadLocalRandom.current().nextLong(
                8_100_000L,
                8_900_000L
        );
        jdbcTemplate.update("""
                INSERT INTO coupon_event (
                    coupon_event_id,
                    esports_match_id,
                    event_name,
                    issue_mode,
                    trigger_type,
                    event_status,
                    claim_window_seconds
                ) VALUES (
                    :couponEventId,
                    -1,
                    '통계 통합 테스트',
                    'SINGLE_FIRST_COME',
                    :triggerType,
                    'OPEN',
                    300
                )
                """, new MapSqlParameterSource()
                .addValue("couponEventId", couponEventId)
                .addValue("triggerType", "STATISTICS_" + UUID.randomUUID()));
    }

    @AfterEach
    void tearDown() {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource("couponEventId", couponEventId);
        jdbcTemplate.update("""
                DELETE FROM coupon_kafka_processing_error
                 WHERE coupon_event_id = :couponEventId
                """, parameters);
        jdbcTemplate.update("""
                DELETE FROM coupon_issue_daily_statistics
                 WHERE coupon_event_id = :couponEventId
                """, parameters);
        jdbcTemplate.update("""
                DELETE FROM coupon_event
                 WHERE coupon_event_id = :couponEventId
                """, parameters);
    }

    @Test
    void 결과와_DLT를_원본_식별자별로_한_번만_집계한다() {
        CouponIssueStatisticsSummaryRow baseline =
                statisticsRepository.findSummary();
        CouponClaimRequest claimRequest = mock(CouponClaimRequest.class);
        when(claimRequest.getId()).thenReturn(100L);
        when(claimRequest.getCouponEventId()).thenReturn(couponEventId);
        CouponIssueResultEvent event = new CouponIssueResultEvent(
                1,
                UUID.randomUUID().toString(),
                100L,
                200L,
                CouponIssueResultStatus.SUCCEEDED,
                null,
                Instant.parse("2026-08-28T05:00:00Z")
        );

        assertThat(statisticsRepository.recordResult(event, claimRequest))
                .isTrue();
        assertThat(statisticsRepository.recordResult(event, claimRequest))
                .isFalse();

        CouponIssueProcessingError error = new CouponIssueProcessingError(
                "clutch-coupon-issue-result",
                "coupon.issue.result",
                1,
                20L,
                event.messageId(),
                event.claimId(),
                couponEventId,
                IllegalStateException.class.getName(),
                "통계 저장 실패",
                "payload",
                LocalDateTime.of(2026, 8, 28, 14, 0)
        );
        assertThat(statisticsRepository.recordProcessingError(error)).isTrue();
        assertThat(statisticsRepository.recordProcessingError(error)).isFalse();

        CouponIssueStatisticsSummaryRow summary =
                statisticsRepository.findSummary();
        assertThat(summary.successCount())
                .isEqualTo(baseline.successCount() + 1);
        assertThat(summary.failureCount())
                .isEqualTo(baseline.failureCount());
        assertThat(summary.processingErrorCount())
                .isEqualTo(baseline.processingErrorCount() + 1);
        assertThat(summary.unassignedErrorCount())
                .isEqualTo(baseline.unassignedErrorCount());

        CouponIssueStatisticsEventRow eventRow = statisticsRepository
                .findEvents(100)
                .stream()
                .filter(row -> row.couponEventId().equals(couponEventId))
                .findFirst()
                .orElseThrow();
        assertThat(eventRow.eventName()).isEqualTo("통계 통합 테스트");
        assertThat(eventRow.successCount()).isEqualTo(1);
        assertThat(eventRow.failureCount()).isZero();
        assertThat(eventRow.processingErrorCount()).isEqualTo(1);

        MapSqlParameterSource dailyParameters = new MapSqlParameterSource()
                .addValue("couponEventId", couponEventId)
                .addValue("statisticsDate", LocalDate.of(2026, 8, 28));
        Long dailySuccessCount = jdbcTemplate.queryForObject("""
                SELECT success_count
                  FROM coupon_issue_daily_statistics
                 WHERE coupon_event_id = :couponEventId
                   AND statistics_date = :statisticsDate
                """, dailyParameters, Long.class);
        assertThat(dailySuccessCount).isEqualTo(1L);
    }
}
