package com.clutch.coupon.statistics.repository;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultStatus;
import com.clutch.coupon.event.domain.CouponEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/** 쿠폰 발급 결과 통계의 멱등 저장과 관리자 집계 조회를 담당한다. */
@Repository
@RequiredArgsConstructor
public class CouponIssueStatisticsRepository {

    private static final ZoneId OPERATION_ZONE = ZoneId.of("Asia/Seoul");

    private static final String UPSERT_STATISTICS = """
            INSERT INTO coupon_issue_statistics (
                coupon_event_id,
                success_count,
                failure_count,
                processing_error_count,
                last_result_at,
                last_error_at
            ) VALUES (
                :couponEventId,
                :successIncrement,
                :failureIncrement,
                :errorIncrement,
                :lastResultAt,
                :lastErrorAt
            )
            ON DUPLICATE KEY UPDATE
                success_count = success_count + VALUES(success_count),
                failure_count = failure_count + VALUES(failure_count),
                processing_error_count = processing_error_count
                    + VALUES(processing_error_count),
                last_result_at = CASE
                    WHEN VALUES(last_result_at) IS NULL THEN last_result_at
                    WHEN last_result_at IS NULL THEN VALUES(last_result_at)
                    ELSE GREATEST(last_result_at, VALUES(last_result_at))
                END,
                last_error_at = CASE
                    WHEN VALUES(last_error_at) IS NULL THEN last_error_at
                    WHEN last_error_at IS NULL THEN VALUES(last_error_at)
                    ELSE GREATEST(last_error_at, VALUES(last_error_at))
                END
            """;

    private static final String UPSERT_DAILY_STATISTICS = """
            INSERT INTO coupon_issue_daily_statistics (
                statistics_date,
                coupon_event_id,
                success_count,
                failure_count,
                rejection_count
            ) VALUES (
                :statisticsDate,
                :couponEventId,
                :successIncrement,
                :failureIncrement,
                0
            )
            ON DUPLICATE KEY UPDATE
                success_count = success_count + VALUES(success_count),
                failure_count = failure_count + VALUES(failure_count)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** 메시지 ID를 먼저 저장한 새 결과 이벤트만 통계에 반영한다. */
    @Transactional
    public boolean recordResult(
            CouponIssueResultEvent event,
            CouponClaimRequest claimRequest
    ) {
        LocalDateTime occurredAt = LocalDateTime.ofInstant(
                event.occurredAt(),
                ZoneOffset.UTC
        );
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("messageId", event.messageId())
                .addValue("claimId", event.claimId())
                .addValue("couponEventId", claimRequest.getCouponEventId())
                .addValue("resultStatus", event.status().name())
                .addValue("occurredAt", occurredAt);

        int inserted = jdbcTemplate.update("""
                INSERT IGNORE INTO coupon_issue_statistics_message (
                    message_id,
                    claim_id,
                    coupon_event_id,
                    result_status,
                    occurred_at
                ) VALUES (
                    :messageId,
                    :claimId,
                    :couponEventId,
                    :resultStatus,
                    :occurredAt
                )
                """, parameters);
        if (inserted == 0) {
            return false;
        }

        boolean succeeded = event.status() == CouponIssueResultStatus.SUCCEEDED;
        upsertStatistics(
                claimRequest.getCouponEventId(),
                succeeded ? 1 : 0,
                succeeded ? 0 : 1,
                0,
                occurredAt,
                null
        );
        upsertDailyStatistics(
                statisticsDate(claimRequest, event),
                claimRequest.getCouponEventId(),
                succeeded ? 1 : 0,
                succeeded ? 0 : 1
        );
        return true;
    }

    /** 원본 토픽 좌표가 처음 기록된 처리 오류만 통계에 반영한다. */
    @Transactional
    public boolean recordProcessingError(CouponIssueProcessingError error) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("originalConsumerGroup", error.originalConsumerGroup())
                .addValue("originalTopic", error.originalTopic())
                .addValue("originalPartition", error.originalPartition())
                .addValue("originalOffset", error.originalOffset())
                .addValue("messageId", error.messageId())
                .addValue("claimId", error.claimId())
                .addValue("couponEventId", error.couponEventId())
                .addValue("exceptionType", error.exceptionType())
                .addValue("exceptionMessage", error.exceptionMessage())
                .addValue("payload", error.payload())
                .addValue("originalOccurredAt", error.originalOccurredAt());

        int inserted = jdbcTemplate.update("""
                INSERT IGNORE INTO coupon_kafka_processing_error (
                    original_consumer_group,
                    original_topic,
                    original_partition,
                    original_offset,
                    message_id,
                    claim_id,
                    coupon_event_id,
                    exception_type,
                    exception_message,
                    payload,
                    original_occurred_at
                ) VALUES (
                    :originalConsumerGroup,
                    :originalTopic,
                    :originalPartition,
                    :originalOffset,
                    :messageId,
                    :claimId,
                    :couponEventId,
                    :exceptionType,
                    :exceptionMessage,
                    :payload,
                    :originalOccurredAt
                )
                """, parameters);
        if (inserted == 0) {
            return false;
        }

        if (error.couponEventId() != null) {
            upsertStatistics(
                    error.couponEventId(),
                    0,
                    0,
                    1,
                    null,
                    LocalDateTime.now(ZoneOffset.UTC)
            );
        }
        return true;
    }

    /** 전체 성공·실패·오류 수와 마지막 처리 시각을 조회한다. */
    @Transactional(readOnly = true)
    public CouponIssueStatisticsSummaryRow findSummary() {
        return jdbcTemplate.queryForObject("""
                SELECT statistics_summary.success_count,
                       statistics_summary.failure_count,
                       statistics_summary.processing_error_count
                           + COALESCE(error_summary.unassigned_error_count, 0)
                           AS processing_error_count,
                       COALESCE(error_summary.unassigned_error_count, 0)
                           AS unassigned_error_count,
                       CASE
                           WHEN statistics_summary.last_processed_at IS NULL
                           THEN error_summary.last_unassigned_error_at
                           WHEN error_summary.last_unassigned_error_at IS NULL
                           THEN statistics_summary.last_processed_at
                           ELSE GREATEST(
                               statistics_summary.last_processed_at,
                               error_summary.last_unassigned_error_at
                           )
                       END AS last_processed_at
                  FROM (
                      SELECT COALESCE(SUM(success_count), 0) AS success_count,
                             COALESCE(SUM(failure_count), 0) AS failure_count,
                             COALESCE(SUM(processing_error_count), 0)
                                 AS processing_error_count,
                             MAX(
                                 CASE
                                     WHEN last_result_at IS NULL
                                     THEN last_error_at
                                     WHEN last_error_at IS NULL
                                     THEN last_result_at
                                     ELSE GREATEST(last_result_at, last_error_at)
                                 END
                             ) AS last_processed_at
                        FROM coupon_issue_statistics
                  ) statistics_summary
                  CROSS JOIN (
                      SELECT COUNT(*) AS unassigned_error_count,
                             MAX(recorded_at) AS last_unassigned_error_at
                        FROM coupon_kafka_processing_error
                       WHERE coupon_event_id IS NULL
                  ) error_summary
                """, new MapSqlParameterSource(), this::mapSummaryRow);
    }

    /** 최근 활동 순으로 쿠폰 이벤트별 통계를 조회한다. */
    @Transactional(readOnly = true)
    public List<CouponIssueStatisticsEventRow> findEvents(int size) {
        return jdbcTemplate.query("""
                SELECT event.coupon_event_id,
                       event.event_name,
                       event.trigger_type,
                       event.event_status,
                       COALESCE(statistics.success_count, 0) AS success_count,
                       COALESCE(statistics.failure_count, 0) AS failure_count,
                       COALESCE(statistics.processing_error_count, 0)
                           AS processing_error_count,
                       statistics.last_result_at,
                       statistics.last_error_at
                  FROM coupon_event event
                  LEFT JOIN coupon_issue_statistics statistics
                    ON statistics.coupon_event_id = event.coupon_event_id
                 ORDER BY CASE
                              WHEN statistics.last_result_at IS NULL
                              THEN COALESCE(
                                  statistics.last_error_at,
                                  event.created_at
                              )
                              WHEN statistics.last_error_at IS NULL
                              THEN statistics.last_result_at
                              ELSE GREATEST(
                                  statistics.last_result_at,
                                  statistics.last_error_at
                              )
                          END DESC,
                          event.coupon_event_id DESC
                 LIMIT :size
                """, new MapSqlParameterSource("size", size), this::mapEventRow);
    }

    private void upsertStatistics(
            Long couponEventId,
            long successIncrement,
            long failureIncrement,
            long errorIncrement,
            LocalDateTime lastResultAt,
            LocalDateTime lastErrorAt
    ) {
        jdbcTemplate.update(
                UPSERT_STATISTICS,
                new MapSqlParameterSource()
                        .addValue("couponEventId", couponEventId)
                        .addValue("successIncrement", successIncrement)
                        .addValue("failureIncrement", failureIncrement)
                        .addValue("errorIncrement", errorIncrement)
                        .addValue("lastResultAt", lastResultAt)
                        .addValue("lastErrorAt", lastErrorAt)
        );
    }

    private void upsertDailyStatistics(
            LocalDate statisticsDate,
            Long couponEventId,
            long successIncrement,
            long failureIncrement
    ) {
        jdbcTemplate.update(
                UPSERT_DAILY_STATISTICS,
                new MapSqlParameterSource()
                        .addValue("statisticsDate", statisticsDate)
                        .addValue("couponEventId", couponEventId)
                        .addValue("successIncrement", successIncrement)
                        .addValue("failureIncrement", failureIncrement)
        );
    }

    private LocalDate statisticsDate(
            CouponClaimRequest claimRequest,
            CouponIssueResultEvent event
    ) {
        if (claimRequest.getCreatedAt() == null) {
            return LocalDate.ofInstant(event.occurredAt(), OPERATION_ZONE);
        }
        return claimRequest.getCreatedAt()
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(OPERATION_ZONE)
                .toLocalDate();
    }

    private CouponIssueStatisticsSummaryRow mapSummaryRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new CouponIssueStatisticsSummaryRow(
                resultSet.getLong("success_count"),
                resultSet.getLong("failure_count"),
                resultSet.getLong("processing_error_count"),
                resultSet.getLong("unassigned_error_count"),
                localDateTime(resultSet, "last_processed_at")
        );
    }

    private CouponIssueStatisticsEventRow mapEventRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new CouponIssueStatisticsEventRow(
                resultSet.getLong("coupon_event_id"),
                resultSet.getString("event_name"),
                resultSet.getString("trigger_type"),
                CouponEventStatus.valueOf(resultSet.getString("event_status")),
                resultSet.getLong("success_count"),
                resultSet.getLong("failure_count"),
                resultSet.getLong("processing_error_count"),
                localDateTime(resultSet, "last_result_at"),
                localDateTime(resultSet, "last_error_at")
        );
    }

    private LocalDateTime localDateTime(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
