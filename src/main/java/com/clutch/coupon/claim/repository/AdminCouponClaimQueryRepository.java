package com.clutch.coupon.claim.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 발급 내역의 동적 필터 조합과 커서 조회를 담당한다.
 *
 * <p>먼저 발급 요청 테이블을 기준으로 현재 페이지의 ID만 조회한 뒤,
 * 해당 ID에 한해서 이벤트, 쿠폰 종류, 사용자 및 실제 발급 쿠폰을
 * 조인한다. 상세 조인 대상을 페이지 크기로 제한해 대량 데이터에서도
 * 첫 페이지 조회 시 전체 조인과 정렬이 발생하지 않도록 한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class AdminCouponClaimQueryRepository {

    private static final String BASE_QUERY = """
            SELECT claim.coupon_claim_request_id AS claim_request_id,
                   claim.created_at AS requested_at,
                   claim.completed_at,
                   claim.coupon_event_id,
                   event.event_name,
                   event.trigger_type,
                   claim.coupon_event_occurrence_id,
                   claim.user_id,
                   member.name AS user_name,
                   member.email AS user_email,
                   member.phone_number AS user_phone_number,
                   item.coupon_type_id,
                   coupon_type.coupon_name,
                   COALESCE(issued_coupon.discount_type,
                            coupon_type.discount_type)
                       AS discount_type,
                   COALESCE(issued_coupon.discount_value,
                            coupon_type.discount_value)
                       AS discount_value,
                   claim.request_status,
                   claim.failure_reason,
                   issued_coupon.user_coupon_id,
                   issued_coupon.coupon_status
              FROM coupon_claim_request claim
              JOIN coupon_event event
                ON event.coupon_event_id = claim.coupon_event_id
              JOIN coupon_event_item item
                ON item.coupon_event_item_id = claim.coupon_event_item_id
              JOIN coupon_type coupon_type
                ON coupon_type.coupon_type_id = item.coupon_type_id
              LEFT JOIN `user` member
                ON member.user_id = claim.user_id
              LEFT JOIN user_coupon issued_coupon
                ON issued_coupon.claim_id = claim.coupon_claim_request_id
             WHERE 1 = 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 필터에 맞는 발급 요청 ID를 먼저 조회하고 해당 ID의 상세 내역만 조인한다.
     *
     * @param condition 조회 조건
     * @param limit 다음 페이지 확인용 값을 포함한 최대 조회 건수
     * @return 조인된 발급 내역
     */
    public List<AdminCouponClaimRow> findAll(
            AdminCouponClaimSearchCondition condition,
            int limit
    ) {
        List<Long> claimRequestIds = findClaimRequestIds(condition, limit);
        if (claimRequestIds.isEmpty()) {
            return List.of();
        }

        String query = BASE_QUERY
                + " AND claim.coupon_claim_request_id IN (:claimRequestIds)"
                + " ORDER BY claim.coupon_claim_request_id DESC";
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("claimRequestIds", claimRequestIds);

        return jdbcTemplate.query(query, parameters, this::mapRow);
    }

    /**
     * 동적 필터를 적용해 현재 페이지에 포함할 발급 요청 ID만 조회한다.
     *
     * <p>필터 처리에 필요한 테이블만 선택적으로 조인한다. 필터가 없는
     * 첫 페이지는 발급 요청 기본 키를 역순으로 읽다가 {@code limit}에서
     * 중단할 수 있다.</p>
     *
     * @param condition 조회 조건
     * @param limit 다음 페이지 확인용 값을 포함한 최대 조회 건수
     * @return 최신순으로 정렬된 발급 요청 ID
     */
    private List<Long> findClaimRequestIds(
            AdminCouponClaimSearchCondition condition,
            int limit
    ) {
        StringBuilder query = new StringBuilder("""
                SELECT claim.coupon_claim_request_id
                  FROM coupon_claim_request claim
                """);

        if (condition.eventNameKeyword() != null
                || condition.triggerKeyword() != null) {
            query.append(" JOIN coupon_event event")
                    .append(" ON event.coupon_event_id")
                    .append(" = claim.coupon_event_id");
        }
        if (condition.couponStatus() != null) {
            query.append(" JOIN user_coupon filtered_coupon")
                    .append(" ON filtered_coupon.claim_id")
                    .append(" = claim.coupon_claim_request_id");
        }
        if (condition.couponTypeId() != null) {
            query.append(" JOIN coupon_event_item filtered_item")
                    .append(" ON filtered_item.coupon_event_item_id")
                    .append(" = claim.coupon_event_item_id");
        }

        query.append(" WHERE 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();

        if (condition.eventIdKeyword() != null) {
            query.append(" AND claim.coupon_event_id = :eventIdKeyword");
            parameters.addValue("eventIdKeyword", condition.eventIdKeyword());
        }
        if (condition.eventNameKeyword() != null) {
            query.append(" AND event.event_name LIKE :eventNameKeyword ESCAPE '!'");
            parameters.addValue(
                    "eventNameKeyword",
                    containsPattern(condition.eventNameKeyword())
            );
        }
        if (condition.triggerKeyword() != null) {
            query.append(" AND event.trigger_type LIKE :triggerKeyword ESCAPE '!'");
            parameters.addValue(
                    "triggerKeyword",
                    containsPattern(condition.triggerKeyword())
            );
        }
        if (condition.userId() != null) {
            query.append(" AND claim.user_id = :userId");
            parameters.addValue("userId", condition.userId());
        }
        if (condition.requestStatus() != null) {
            query.append(" AND claim.request_status = :requestStatus");
            parameters.addValue(
                    "requestStatus",
                    condition.requestStatus().name()
            );
        }
        if (condition.couponStatus() != null) {
            query.append(" AND filtered_coupon.coupon_status = :couponStatus");
            parameters.addValue(
                    "couponStatus",
                    condition.couponStatus().name()
            );
        }
        if (condition.couponTypeId() != null) {
            query.append(" AND filtered_item.coupon_type_id = :couponTypeId");
            parameters.addValue("couponTypeId", condition.couponTypeId());
        }
        if (condition.from() != null) {
            query.append(" AND claim.created_at >= :from");
            parameters.addValue("from", condition.from());
        }
        if (condition.to() != null) {
            query.append(" AND claim.created_at <= :to");
            parameters.addValue("to", condition.to());
        }
        if (condition.cursor() != null) {
            query.append(" AND claim.coupon_claim_request_id < :cursor");
            parameters.addValue("cursor", condition.cursor());
        }

        query.append(" ORDER BY claim.coupon_claim_request_id DESC");
        query.append(" LIMIT :limit");
        parameters.addValue("limit", limit);

        return jdbcTemplate.queryForList(
                query.toString(),
                parameters,
                Long.class
        );
    }

    /**
     * JDBC 조회 결과 한 행을 내부 발급 내역 조회 모델로 변환한다.
     *
     * @param resultSet 현재 조회 결과 행
     * @param rowNumber 현재 행 번호
     * @return 발급 내역 조인 조회 결과
     * @throws SQLException 조회 결과 변환 중 SQL 오류가 발생한 경우
     */
    private AdminCouponClaimRow mapRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new AdminCouponClaimRow(
                resultSet.getLong("claim_request_id"),
                localDateTime(resultSet, "requested_at"),
                localDateTime(resultSet, "completed_at"),
                resultSet.getLong("coupon_event_id"),
                resultSet.getString("event_name"),
                resultSet.getString("trigger_type"),
                resultSet.getObject("coupon_event_occurrence_id", Long.class),
                resultSet.getLong("user_id"),
                resultSet.getString("user_name"),
                resultSet.getString("user_email"),
                resultSet.getString("user_phone_number"),
                resultSet.getLong("coupon_type_id"),
                resultSet.getString("coupon_name"),
                resultSet.getString("discount_type"),
                resultSet.getBigDecimal("discount_value"),
                resultSet.getString("request_status"),
                resultSet.getString("failure_reason"),
                resultSet.getObject("user_coupon_id", Long.class),
                resultSet.getString("coupon_status")
        );
    }

    /**
     * nullable SQL 타임스탬프를 {@link LocalDateTime}으로 변환한다.
     *
     * @param resultSet 현재 조회 결과 행
     * @param columnName 변환할 컬럼 이름
     * @return 변환된 시각, 데이터베이스 값이 없으면 {@code null}
     * @throws SQLException 컬럼을 읽을 수 없는 경우
     */
    private LocalDateTime localDateTime(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * LIKE 부분 검색에서 와일드카드 문자가 입력값으로 취급되도록 이스케이프한다.
     *
     * @param keyword 부분 검색할 문자열
     * @return 앞뒤에 와일드카드를 추가한 안전한 LIKE 패턴
     */
    private String containsPattern(String keyword) {
        return "%" + keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }
}
