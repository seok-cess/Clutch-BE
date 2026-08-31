package com.clutch.coupon.admin.dashboard.repository;

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
import java.util.List;

/**
 * 관리자 페이지 운영 홈에 필요한 쿠폰 통계와 이벤트 표를 전용 SQL로 조회한다.
 *
 * <p>목록 API의 커서 페이지를 반복 호출하지 않고 운영 홈에 필요한 전체 집계를
 * 데이터베이스에서 직접 계산한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class AdminCouponDashboardQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** 관리자 운영 홈에 표시할 전체 진행 중 쿠폰 이벤트 수를 조회한다. */
    @Transactional(readOnly = true)
    public long countOpenEvents() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM coupon_event
                 WHERE event_status = 'OPEN'
                """, new MapSqlParameterSource(), Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 관리자 운영 홈의 지정 UTC 범위 발급 요청 상태를 조회한다.
     *
     * <p>완료된 발급과 사전 거절은 Kafka Consumer가 누적한
     * KST 일별 통계를 사용하고, 아직 완료되지 않은 Claim만 원본에서
     * 조회한다.</p>
     */
    @Transactional(readOnly = true)
    public CouponDashboardAggregateRow findAggregate(
            LocalDateTime startUtc,
            LocalDateTime endUtc
    ) {
        MapSqlParameterSource parameters = rangeParameters(startUtc, endUtc);
        return jdbcTemplate.queryForObject("""
                SELECT daily.success_count
                           + daily.failure_count
                           + daily.rejection_count
                           + claim.unfinished_count AS request_count,
                       daily.success_count AS issued_count,
                       daily.failure_count + daily.rejection_count
                           AS failed_count,
                       claim.pending_count AS pending_count
                  FROM (
                      SELECT COALESCE(SUM(CASE
                                 WHEN request_status = 'PENDING'
                                 THEN 1 ELSE 0
                             END), 0) AS pending_count,
                             COUNT(*) AS unfinished_count
                        FROM coupon_claim_request claim
                        LEFT JOIN coupon_issue_statistics_message message
                          ON message.claim_id = claim.coupon_claim_request_id
                       WHERE claim.created_at >= :startUtc
                         AND claim.created_at < :endUtc
                         AND claim.request_status NOT IN (
                             'SUCCEEDED', 'FAILED'
                         )
                         AND message.message_id IS NULL
                  ) claim
                  CROSS JOIN (
                      SELECT COALESCE(SUM(success_count), 0)
                                 AS success_count,
                             COALESCE(SUM(failure_count), 0)
                                 AS failure_count,
                             COALESCE(SUM(rejection_count), 0)
                                 AS rejection_count
                        FROM coupon_issue_daily_statistics
                       WHERE statistics_date = DATE(
                           DATE_ADD(:startUtc, INTERVAL 9 HOUR)
                       )
                  ) daily
                """, parameters, this::mapAggregateRow);
    }

    /** Kafka Consumer가 누적한 KST 날짜별 성공·실패 발급 수를 조회한다. */
    @Transactional(readOnly = true)
    public List<DailyIssuanceRow> findDailyIssuance(
            LocalDateTime startUtc,
            LocalDateTime endUtc
    ) {
        return jdbcTemplate.query("""
                SELECT statistics_date AS issuance_date,
                       SUM(success_count) AS issued_count,
                       SUM(failure_count + rejection_count) AS failed_count
                  FROM coupon_issue_daily_statistics
                 WHERE statistics_date >= DATE(
                           DATE_ADD(:startUtc, INTERVAL 9 HOUR)
                       )
                   AND statistics_date < DATE(
                           DATE_ADD(:endUtc, INTERVAL 9 HOUR)
                       )
                 GROUP BY statistics_date
                 ORDER BY statistics_date
                """, rangeParameters(startUtc, endUtc), this::mapDailyRow);
    }

    /** 관리자 운영 홈의 재고 소진 판정을 위해 진행 중 이벤트 항목을 조회한다. */
    @Transactional(readOnly = true)
    public List<OpenEventItemRow> findOpenEventItems() {
        return jdbcTemplate.query("""
                SELECT event.coupon_event_id,
                       item.coupon_event_item_id
                  FROM coupon_event event
                  JOIN coupon_event_item item
                    ON item.coupon_event_id = event.coupon_event_id
                 WHERE event.event_status = 'OPEN'
                 ORDER BY event.coupon_event_id,
                          item.coupon_event_item_id
                """, new MapSqlParameterSource(), (resultSet, rowNumber) ->
                new OpenEventItemRow(
                        resultSet.getLong("coupon_event_id"),
                        resultSet.getLong("coupon_event_item_id")
                ));
    }

    /**
     * 관리자 운영 홈의 이벤트 표에 표시할 이벤트를 진행 중 우선·최신순으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<AdminDashboardEventRow> findDashboardEvents(int size) {
        return jdbcTemplate.query("""
                SELECT event.coupon_event_id,
                       event.event_name,
                       event.event_status,
                       matches.scheduled_at,
                       MAX(CASE WHEN team.display_order = 1
                           THEN COALESCE(team.team_name, team.team_code)
                       END) AS first_team_name,
                       MAX(CASE WHEN team.display_order = 2
                           THEN COALESCE(team.team_name, team.team_code)
                       END) AS second_team_name,
                       COALESCE(item_total.total_quantity, 0)
                           AS total_quantity,
                       COALESCE(item_total.issued_quantity, 0)
                           AS issued_quantity
                  FROM coupon_event event
                  JOIN esports_match matches
                    ON matches.esports_match_id = event.esports_match_id
                  LEFT JOIN match_team team
                    ON team.match_id = matches.esports_match_id
                  LEFT JOIN (
                      SELECT coupon_event_id,
                             SUM(quantity) AS total_quantity,
                             SUM(success_count) AS issued_quantity
                        FROM coupon_event_item
                       GROUP BY coupon_event_id
                  ) item_total
                    ON item_total.coupon_event_id = event.coupon_event_id
                 GROUP BY event.coupon_event_id,
                          event.event_name,
                          event.event_status,
                          matches.scheduled_at,
                          item_total.total_quantity,
                          item_total.issued_quantity
                 ORDER BY CASE WHEN event.event_status = 'OPEN' THEN 0 ELSE 1 END,
                          event.coupon_event_id DESC
                 LIMIT :size
                """, new MapSqlParameterSource("size", size), this::mapEventRow);
    }

    private MapSqlParameterSource rangeParameters(
            LocalDateTime startUtc,
            LocalDateTime endUtc
    ) {
        return new MapSqlParameterSource()
                .addValue("startUtc", startUtc)
                .addValue("endUtc", endUtc);
    }

    private CouponDashboardAggregateRow mapAggregateRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new CouponDashboardAggregateRow(
                resultSet.getLong("request_count"),
                resultSet.getLong("issued_count"),
                resultSet.getLong("failed_count"),
                resultSet.getLong("pending_count")
        );
    }

    private DailyIssuanceRow mapDailyRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new DailyIssuanceRow(
                resultSet.getObject("issuance_date", LocalDate.class),
                resultSet.getLong("issued_count"),
                resultSet.getLong("failed_count")
        );
    }

    private AdminDashboardEventRow mapEventRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        Timestamp scheduledAt = resultSet.getTimestamp("scheduled_at");
        return new AdminDashboardEventRow(
                resultSet.getLong("coupon_event_id"),
                resultSet.getString("event_name"),
                CouponEventStatus.valueOf(resultSet.getString("event_status")),
                scheduledAt == null ? null : scheduledAt.toLocalDateTime(),
                resultSet.getString("first_team_name"),
                resultSet.getString("second_team_name"),
                resultSet.getLong("total_quantity"),
                resultSet.getLong("issued_quantity")
        );
    }
}
