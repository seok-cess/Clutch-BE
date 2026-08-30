package com.clutch.coupon.integrity.repository;

import com.clutch.coupon.integrity.domain.CouponIntegrityFingerprint;
import com.clutch.coupon.integrity.domain.CouponIntegrityResult;
import com.clutch.coupon.integrity.domain.CouponIntegritySnapshot;
import com.clutch.coupon.integrity.domain.IntegrityVerdict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class CouponIntegrityQueryRepository {
    private static final String SCALE_SQL = """
            SELECT
                (SELECT COUNT(*) FROM `user`) user_count,
                (SELECT COUNT(*) FROM coupon_claim_request) claim_request_count,
                (SELECT COUNT(*) FROM user_coupon) user_coupon_count,
                (SELECT COUNT(*) FROM coupon_event) coupon_event_count,
                (SELECT COUNT(*) FROM coupon_event_occurrence) occurrence_count,
                (SELECT COUNT(*) FROM coupon_event_item) event_item_count
            """;

    private static final String FINGERPRINT_SQL = """
            SELECT 'coupon_claim_request' table_name, COUNT(*) row_count,
                   MIN(coupon_claim_request_id) min_id, MAX(coupon_claim_request_id) max_id,
                   BIT_XOR(CRC32(CONCAT_WS('#', coupon_claim_request_id, user_id,
                       coupon_event_id, COALESCE(coupon_event_occurrence_id, ''),
                       coupon_event_item_id, request_status,
                       DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f')))) data_fingerprint
            FROM coupon_claim_request
            UNION ALL
            SELECT 'user_coupon', COUNT(*), MIN(user_coupon_id), MAX(user_coupon_id),
                   BIT_XOR(CRC32(CONCAT_WS('#', user_coupon_id, claim_id, user_id,
                       coupon_event_id, COALESCE(coupon_event_occurrence_id, ''),
                       coupon_event_item_id, coupon_status,
                       DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f'))))
            FROM user_coupon
            """;

    private static final String CHECK_SQL = """
            WITH
            user_coupon_audit AS (
                SELECT COUNT(*) total_count,
                    COALESCE(SUM(u.user_id IS NULL), 0) orphan_user_count,
                    COALESCE(SUM(ce.coupon_event_id IS NULL), 0) orphan_event_count,
                    COALESCE(SUM(cei.coupon_event_item_id IS NULL), 0) orphan_item_count,
                    COALESCE(SUM(uc.coupon_event_occurrence_id IS NULL
                        OR ceo.coupon_event_occurrence_id IS NULL), 0) missing_or_orphan_occurrence_count,
                    COALESCE(SUM(cr.coupon_claim_request_id IS NULL), 0) orphan_claim_count,
                    COALESCE(SUM(cei.coupon_event_item_id IS NOT NULL
                        AND cei.coupon_event_id <> uc.coupon_event_id), 0) item_event_mismatch_count,
                    COALESCE(SUM(ceo.coupon_event_occurrence_id IS NOT NULL
                        AND ceo.coupon_event_id <> uc.coupon_event_id), 0) occurrence_event_mismatch_count,
                    COALESCE(SUM(cr.coupon_claim_request_id IS NOT NULL AND (
                        cr.user_id <> uc.user_id OR cr.coupon_event_id <> uc.coupon_event_id
                        OR NOT (cr.coupon_event_occurrence_id <=> uc.coupon_event_occurrence_id)
                        OR cr.coupon_event_item_id <> uc.coupon_event_item_id)), 0) claim_coupon_mismatch_count,
                    COALESCE(SUM(cr.coupon_claim_request_id IS NOT NULL
                        AND cr.request_status <> 'SUCCEEDED'), 0) coupon_without_succeeded_claim_count,
                    COALESCE(SUM(uc.coupon_status NOT IN ('ISSUED','USED','EXPIRED','CANCELLED')), 0) invalid_status_count,
                    COALESCE(SUM(uc.coupon_status = 'ISSUED'
                        AND (uc.used_at IS NOT NULL OR uc.cancelled_at IS NOT NULL)), 0) issued_timestamp_mismatch_count,
                    COALESCE(SUM(uc.coupon_status = 'USED'
                        AND (uc.used_at IS NULL OR uc.cancelled_at IS NOT NULL)), 0) used_timestamp_mismatch_count,
                    COALESCE(SUM(uc.coupon_status = 'CANCELLED' AND (
                        uc.cancelled_at IS NULL OR uc.used_at IS NOT NULL
                        OR uc.cancel_reason IS NULL OR TRIM(uc.cancel_reason) = '')), 0) cancelled_timestamp_mismatch_count,
                    COALESCE(SUM(uc.coupon_status = 'EXPIRED' AND (
                        uc.expires_at > :asOfUtc OR uc.used_at IS NOT NULL
                        OR uc.cancelled_at IS NOT NULL)), 0) expired_state_mismatch_count,
                    COALESCE(SUM(uc.coupon_status = 'ISSUED'
                        AND uc.expires_at <= :asOfUtc), 0) logically_expired_issued_count,
                    COALESCE(SUM(uc.expires_at <= uc.created_at), 0) invalid_validity_period_count
                FROM user_coupon uc
                LEFT JOIN `user` u ON u.user_id = uc.user_id
                LEFT JOIN coupon_event ce ON ce.coupon_event_id = uc.coupon_event_id
                LEFT JOIN coupon_event_item cei ON cei.coupon_event_item_id = uc.coupon_event_item_id
                LEFT JOIN coupon_event_occurrence ceo ON ceo.coupon_event_occurrence_id = uc.coupon_event_occurrence_id
                LEFT JOIN coupon_claim_request cr ON cr.coupon_claim_request_id = uc.claim_id
            ),
            claim_audit AS (
                SELECT COUNT(*) total_count,
                    COALESCE(SUM(u.user_id IS NULL), 0) orphan_user_count,
                    COALESCE(SUM(ce.coupon_event_id IS NULL), 0) orphan_event_count,
                    COALESCE(SUM(cei.coupon_event_item_id IS NULL), 0) orphan_item_count,
                    COALESCE(SUM(cr.coupon_event_occurrence_id IS NULL
                        OR ceo.coupon_event_occurrence_id IS NULL), 0) missing_or_orphan_occurrence_count,
                    COALESCE(SUM(cei.coupon_event_item_id IS NOT NULL
                        AND cei.coupon_event_id <> cr.coupon_event_id), 0) item_event_mismatch_count,
                    COALESCE(SUM(ceo.coupon_event_occurrence_id IS NOT NULL
                        AND ceo.coupon_event_id <> cr.coupon_event_id), 0) occurrence_event_mismatch_count,
                    COALESCE(SUM(cr.request_status NOT IN ('PENDING','SUCCEEDED','FAILED','CANCELLED')), 0) invalid_status_count,
                    COALESCE(SUM(cr.request_status = 'PENDING'
                        AND (cr.completed_at IS NOT NULL OR cr.failure_reason IS NOT NULL)), 0) pending_state_mismatch_count,
                    COALESCE(SUM(cr.request_status = 'SUCCEEDED'
                        AND (cr.completed_at IS NULL OR cr.failure_reason IS NOT NULL)), 0) succeeded_state_mismatch_count,
                    COALESCE(SUM(cr.request_status = 'FAILED' AND (
                        cr.completed_at IS NULL OR cr.failure_reason IS NULL
                        OR TRIM(cr.failure_reason) = '')), 0) failed_state_mismatch_count,
                    COALESCE(SUM(cr.request_status = 'PENDING'
                        AND cr.created_at < :asOfUtc - INTERVAL 10 MINUTE), 0) stale_pending_count,
                    COALESCE(SUM(cr.request_status = 'SUCCEEDED'
                        AND uc.user_coupon_id IS NULL), 0) succeeded_without_coupon_count
                FROM coupon_claim_request cr
                LEFT JOIN `user` u ON u.user_id = cr.user_id
                LEFT JOIN coupon_event ce ON ce.coupon_event_id = cr.coupon_event_id
                LEFT JOIN coupon_event_item cei ON cei.coupon_event_item_id = cr.coupon_event_item_id
                LEFT JOIN coupon_event_occurrence ceo ON ceo.coupon_event_occurrence_id = cr.coupon_event_occurrence_id
                LEFT JOIN user_coupon uc ON uc.claim_id = cr.coupon_claim_request_id
            ),
            duplicate_claims AS (
                SELECT COALESCE(SUM(duplicate_count - 1), 0) violation_count FROM (
                    SELECT COUNT(*) duplicate_count FROM coupon_claim_request
                    WHERE coupon_event_occurrence_id IS NOT NULL
                    GROUP BY user_id, coupon_event_occurrence_id HAVING COUNT(*) > 1
                ) duplicated
            ),
            duplicate_coupons AS (
                SELECT COALESCE(SUM(duplicate_count - 1), 0) violation_count FROM (
                    SELECT COUNT(*) duplicate_count FROM user_coupon
                    WHERE coupon_event_occurrence_id IS NOT NULL
                    GROUP BY user_id, coupon_event_occurrence_id HAVING COUNT(*) > 1
                ) duplicated
            ),
            issued_by_item AS (
                SELECT coupon_event_item_id, COUNT(*) issued_count
                FROM user_coupon GROUP BY coupon_event_item_id
            ),
            item_audit AS (
                SELECT COALESCE(SUM(GREATEST(COALESCE(issued.issued_count, 0) - item.quantity, 0)), 0) over_issued_coupon_count,
                    COALESCE(SUM(item.success_count <> COALESCE(issued.issued_count, 0)), 0) success_count_mismatch_item_count,
                    COALESCE(SUM(item.success_count < 0 OR item.success_count > item.quantity
                        OR item.quantity <= 0), 0) invalid_stock_item_count
                FROM coupon_event_item item LEFT JOIN issued_by_item issued
                    ON issued.coupon_event_item_id = item.coupon_event_item_id
            ),
            occurrence_audit AS (
                SELECT COALESCE(SUM(expires_at <= opened_at), 0) invalid_period_count,
                    COALESCE(SUM(occurrence_status = 'OPEN' AND closed_at IS NOT NULL), 0) open_with_closed_at_count,
                    COALESCE(SUM(occurrence_status IN ('CLOSED','CANCELLED') AND closed_at IS NULL), 0) closed_without_closed_at_count,
                    COALESCE(SUM(occurrence_status = 'OPEN'
                        AND expires_at < :asOfUtc - INTERVAL 10 SECOND), 0) stale_open_count
                FROM coupon_event_occurrence
            ),
            checks AS (
                SELECT 1 display_order, 'FAIL' severity, 'USER_COUNT_MINIMUM' check_code,
                    GREATEST(CAST(1000000 AS SIGNED) - CAST((SELECT COUNT(*) FROM `user`) AS SIGNED), 0) violation_count,
                    '가상 사용자가 100만 명 이상이어야 한다.' description
                UNION ALL SELECT 2, 'FAIL','CLAIM_REQUEST_COUNT_MINIMUM',GREATEST(CAST(3000000 AS SIGNED)-CAST(total_count AS SIGNED),0),'쿠폰 발급 요청 이력이 300만 건 이상이어야 한다.' FROM claim_audit
                UNION ALL SELECT 3,'FAIL','USER_COUPON_ORPHAN_USER',orphan_user_count,'존재하지 않는 사용자를 참조하는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 4,'FAIL','USER_COUPON_ORPHAN_EVENT',orphan_event_count,'존재하지 않는 이벤트를 참조하는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 5,'FAIL','USER_COUPON_ORPHAN_ITEM',orphan_item_count,'존재하지 않는 이벤트 항목을 참조하는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 6,'FAIL','USER_COUPON_MISSING_OR_ORPHAN_OCCURRENCE',missing_or_orphan_occurrence_count,'회차가 없거나 존재하지 않는 회차를 참조하는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 7,'FAIL','USER_COUPON_ORPHAN_CLAIM',orphan_claim_count,'존재하지 않는 발급 요청을 참조하는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 8,'FAIL','USER_COUPON_ITEM_EVENT_MISMATCH',item_event_mismatch_count,'쿠폰과 이벤트 항목의 이벤트가 다른 건수' FROM user_coupon_audit
                UNION ALL SELECT 9,'FAIL','USER_COUPON_OCCURRENCE_EVENT_MISMATCH',occurrence_event_mismatch_count,'쿠폰과 회차의 이벤트가 다른 건수' FROM user_coupon_audit
                UNION ALL SELECT 10,'FAIL','CLAIM_USER_COUPON_MISMATCH',claim_coupon_mismatch_count,'발급 요청과 실제 쿠폰의 사용자·이벤트·회차·항목이 다른 건수' FROM user_coupon_audit
                UNION ALL SELECT 11,'FAIL','COUPON_WITHOUT_SUCCEEDED_CLAIM',coupon_without_succeeded_claim_count,'성공하지 않은 요청에 실제 쿠폰이 연결된 건수' FROM user_coupon_audit
                UNION ALL SELECT 12,'FAIL','USER_COUPON_INVALID_STATUS',invalid_status_count,'DB 계약에 없는 쿠폰 상태 수' FROM user_coupon_audit
                UNION ALL SELECT 13,'FAIL','ISSUED_TIMESTAMP_MISMATCH',issued_timestamp_mismatch_count,'ISSUED인데 사용·취소 시각이 존재하는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 14,'FAIL','USED_TIMESTAMP_MISMATCH',used_timestamp_mismatch_count,'USED 상태와 사용·취소 시각이 맞지 않는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 15,'FAIL','CANCELLED_TIMESTAMP_MISMATCH',cancelled_timestamp_mismatch_count,'CANCELLED 상태와 취소 시각·사유가 맞지 않는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 16,'FAIL','EXPIRED_STATE_MISMATCH',expired_state_mismatch_count,'EXPIRED 상태와 만료·사용·취소 정보가 맞지 않는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 17,'INFO','LOGICALLY_EXPIRED_ISSUED',logically_expired_issued_count,'저장 상태는 ISSUED지만 API에서 계산형 EXPIRED로 해석되는 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 18,'FAIL','INVALID_COUPON_VALIDITY_PERIOD',invalid_validity_period_count,'만료 시각이 생성 시각보다 늦지 않은 쿠폰 수' FROM user_coupon_audit
                UNION ALL SELECT 19,'FAIL','CLAIM_ORPHAN_USER',orphan_user_count,'존재하지 않는 사용자의 발급 요청 수' FROM claim_audit
                UNION ALL SELECT 20,'FAIL','CLAIM_ORPHAN_EVENT',orphan_event_count,'존재하지 않는 이벤트의 발급 요청 수' FROM claim_audit
                UNION ALL SELECT 21,'FAIL','CLAIM_ORPHAN_ITEM',orphan_item_count,'존재하지 않는 이벤트 항목의 발급 요청 수' FROM claim_audit
                UNION ALL SELECT 22,'FAIL','CLAIM_MISSING_OR_ORPHAN_OCCURRENCE',missing_or_orphan_occurrence_count,'회차가 없거나 존재하지 않는 회차의 발급 요청 수' FROM claim_audit
                UNION ALL SELECT 23,'FAIL','CLAIM_ITEM_EVENT_MISMATCH',item_event_mismatch_count,'발급 요청과 이벤트 항목의 이벤트가 다른 건수' FROM claim_audit
                UNION ALL SELECT 24,'FAIL','CLAIM_OCCURRENCE_EVENT_MISMATCH',occurrence_event_mismatch_count,'발급 요청과 회차의 이벤트가 다른 건수' FROM claim_audit
                UNION ALL SELECT 25,'FAIL','CLAIM_INVALID_STATUS',invalid_status_count,'DB 계약에 없는 발급 요청 상태 수' FROM claim_audit
                UNION ALL SELECT 26,'FAIL','PENDING_STATE_MISMATCH',pending_state_mismatch_count,'PENDING 상태와 완료·실패 정보가 맞지 않는 요청 수' FROM claim_audit
                UNION ALL SELECT 27,'FAIL','SUCCEEDED_STATE_MISMATCH',succeeded_state_mismatch_count,'SUCCEEDED 상태와 완료·실패 정보가 맞지 않는 요청 수' FROM claim_audit
                UNION ALL SELECT 28,'FAIL','FAILED_STATE_MISMATCH',failed_state_mismatch_count,'FAILED 상태와 완료·실패 정보가 맞지 않는 요청 수' FROM claim_audit
                UNION ALL SELECT 29,'WARN','STALE_PENDING_CLAIM',stale_pending_count,'10분 넘게 PENDING인 발급 요청 수' FROM claim_audit
                UNION ALL SELECT 30,'FAIL','SUCCEEDED_CLAIM_WITHOUT_COUPON',succeeded_without_coupon_count,'성공했지만 실제 쿠폰이 없는 발급 요청 수' FROM claim_audit
                UNION ALL SELECT 31,'FAIL','DUPLICATE_CLAIM_PER_USER_OCCURRENCE',violation_count,'동일 사용자·회차의 중복 발급 요청 초과 건수' FROM duplicate_claims
                UNION ALL SELECT 32,'FAIL','DUPLICATE_COUPON_PER_USER_OCCURRENCE',violation_count,'동일 사용자·회차의 중복 쿠폰 초과 건수' FROM duplicate_coupons
                UNION ALL SELECT 33,'FAIL','OVER_ISSUED_COUPON',over_issued_coupon_count,'이벤트 항목 수량을 초과해 발급된 쿠폰 수' FROM item_audit
                UNION ALL SELECT 34,'FAIL','SUCCESS_COUNT_MISMATCH_ITEM',success_count_mismatch_item_count,'success_count와 실제 쿠폰 수가 다른 이벤트 항목 수' FROM item_audit
                UNION ALL SELECT 35,'FAIL','INVALID_STOCK_ITEM',invalid_stock_item_count,'수량 또는 success_count 범위가 잘못된 이벤트 항목 수' FROM item_audit
                UNION ALL SELECT 36,'FAIL','INVALID_OCCURRENCE_PERIOD',invalid_period_count,'오픈·만료 시각 순서가 잘못된 회차 수' FROM occurrence_audit
                UNION ALL SELECT 37,'FAIL','OPEN_OCCURRENCE_WITH_CLOSED_AT',open_with_closed_at_count,'OPEN인데 종료 시각이 존재하는 회차 수' FROM occurrence_audit
                UNION ALL SELECT 38,'FAIL','CLOSED_OCCURRENCE_WITHOUT_CLOSED_AT',closed_without_closed_at_count,'CLOSED/CANCELLED인데 종료 시각이 없는 회차 수' FROM occurrence_audit
                UNION ALL SELECT 39,'WARN','STALE_OPEN_OCCURRENCE',stale_open_count,'만료 후 10초가 지났지만 OPEN인 회차 수' FROM occurrence_audit
            )
            SELECT display_order, severity, check_code, violation_count, description
            FROM checks ORDER BY display_order
            """;

    private static final String INDEX_SQL = """
            SELECT COUNT(*) matching_index_count
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'user_coupon'
              AND column_name = 'coupon_event_item_id'
              AND seq_in_index = 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public CouponIntegrityQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CouponIntegritySnapshot execute() {
        LocalDateTime asOfUtc = jdbcTemplate.queryForObject(
                "SELECT UTC_TIMESTAMP(6)", LocalDateTime.class
        );
        Map<String, Object> scale = jdbcTemplate.queryForMap(SCALE_SQL);
        List<CouponIntegrityResult> results = namedJdbcTemplate.query(
                CHECK_SQL,
                new MapSqlParameterSource("asOfUtc", asOfUtc),
                (resultSet, rowNumber) -> mapResult(resultSet)
        );
        long matchingIndexCount = jdbcTemplate.queryForObject(INDEX_SQL, Long.class);
        results = new ArrayList<>(results);
        results.add(new CouponIntegrityResult(
                "USER_COUPON_ITEM_LEADING_INDEX",
                IntegrityVerdict.WARN,
                matchingIndexCount > 0 ? IntegrityVerdict.PASS : IntegrityVerdict.WARN,
                matchingIndexCount > 0 ? 0 : 1,
                "user_coupon(coupon_event_item_id) 선두 인덱스가 없으면 300만 건 집계 비용이 커진다.",
                40
        ));
        Map<String, CouponIntegrityFingerprint> fingerprints = jdbcTemplate.query(
                FINGERPRINT_SQL,
                resultSet -> {
                    java.util.HashMap<String, CouponIntegrityFingerprint> mapped = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        mapped.put(resultSet.getString("table_name"), new CouponIntegrityFingerprint(
                                nullableLong(resultSet, "min_id"),
                                nullableLong(resultSet, "max_id"),
                                nullableLong(resultSet, "data_fingerprint")
                        ));
                    }
                    return mapped;
                }
        );
        IntegrityVerdict overall = overallVerdict(results);
        return new CouponIntegritySnapshot(
                asOfUtc,
                number(scale, "user_count"), number(scale, "claim_request_count"),
                number(scale, "user_coupon_count"), number(scale, "coupon_event_count"),
                number(scale, "occurrence_count"), number(scale, "event_item_count"),
                fingerprints.get("coupon_claim_request"), fingerprints.get("user_coupon"),
                List.copyOf(results), overall
        );
    }

    private CouponIntegrityResult mapResult(ResultSet resultSet) throws SQLException {
        IntegrityVerdict severity = IntegrityVerdict.valueOf(resultSet.getString("severity"));
        long count = resultSet.getLong("violation_count");
        IntegrityVerdict verdict = verdict(severity, count);
        return new CouponIntegrityResult(
                resultSet.getString("check_code"), severity, verdict, count,
                resultSet.getString("description"), resultSet.getInt("display_order")
        );
    }

    static IntegrityVerdict overallVerdict(List<CouponIntegrityResult> results) {
        if (results.stream().anyMatch(result -> result.verdict() == IntegrityVerdict.FAIL)) {
            return IntegrityVerdict.FAIL;
        }
        if (results.stream().anyMatch(result -> result.verdict() == IntegrityVerdict.WARN)) {
            return IntegrityVerdict.WARN;
        }
        return IntegrityVerdict.PASS;
    }

    static IntegrityVerdict verdict(IntegrityVerdict severity, long violationCount) {
        if (severity == IntegrityVerdict.INFO) {
            return IntegrityVerdict.INFO;
        }
        return violationCount == 0 ? IntegrityVerdict.PASS : severity;
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

}
