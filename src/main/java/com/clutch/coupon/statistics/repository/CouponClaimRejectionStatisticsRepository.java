package com.clutch.coupon.statistics.repository;

import com.clutch.coupon.contract.kafka.CouponClaimRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Kafka 재전달에 안전하게 쿠폰 신청 거절 원본을 저장한다. */
@Repository
@RequiredArgsConstructor
public class CouponClaimRejectionStatisticsRepository {

    private static final ZoneId OPERATION_ZONE = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter MYSQL_UTC_DATE_TIME =
            DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
                    .withZone(ZoneOffset.UTC);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** 같은 messageId는 원본과 KST 일별 통계에 한 번만 반영한다. */
    @Transactional
    public boolean record(CouponClaimRejectedEvent event) {
        int inserted = jdbcTemplate.update("""
                INSERT IGNORE INTO coupon_claim_rejection_message (
                    message_id,
                    coupon_event_id,
                    coupon_event_occurrence_id,
                    rejection_reason,
                    occurred_at
                ) VALUES (
                    :messageId,
                    :couponEventId,
                    :couponEventOccurrenceId,
                    :rejectionReason,
                    :occurredAt
                )
                """, new MapSqlParameterSource()
                .addValue("messageId", event.messageId())
                .addValue("couponEventId", event.couponEventId())
                .addValue(
                        "couponEventOccurrenceId",
                        event.couponEventOccurrenceId()
                )
                .addValue("rejectionReason", event.reason())
                .addValue(
                        "occurredAt",
                        MYSQL_UTC_DATE_TIME.format(event.occurredAt())
                ));
        if (inserted == 0) {
            return false;
        }

        LocalDate statisticsDate = LocalDate.ofInstant(
                event.occurredAt(),
                OPERATION_ZONE
        );
        jdbcTemplate.update("""
                INSERT INTO coupon_issue_daily_statistics (
                    statistics_date,
                    coupon_event_id,
                    success_count,
                    failure_count,
                    rejection_count
                ) VALUES (
                    :statisticsDate,
                    :couponEventId,
                    0,
                    0,
                    1
                )
                ON DUPLICATE KEY UPDATE
                    rejection_count = rejection_count + 1
                """, new MapSqlParameterSource()
                .addValue("statisticsDate", statisticsDate)
                .addValue("couponEventId", event.couponEventId()));
        return true;
    }
}
