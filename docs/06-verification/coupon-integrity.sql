-- CLUTCH 쿠폰 300만 건 정합성 검증
--
-- 실행 전제
-- 1. MySQL 8에서 실행한다.
-- 2. k6와 데이터 적재를 중단한 안정 상태에서 실행한다.
-- 3. 데이터는 변경하지 않으며, 일관된 읽기 전용 스냅샷만 사용한다.
-- 4. 같은 세션에서 다시 실행하면 @as_of_utc가 유지되어 같은 기준 시각으로 검증한다.

SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET @as_of_utc = COALESCE(@as_of_utc, UTC_TIMESTAMP(6));

START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;

-- 실행 정보와 과제 최소 적재량을 먼저 확인한다.
SELECT
    @as_of_utc AS as_of_utc,
    DATABASE() AS database_name,
    @@hostname AS database_host,
    @@transaction_isolation AS transaction_isolation;

SELECT
    (SELECT COUNT(*) FROM `user`) AS user_count,
    (SELECT COUNT(*) FROM `coupon_claim_request`) AS claim_request_count,
    (SELECT COUNT(*) FROM `user_coupon`) AS user_coupon_count,
    (SELECT COUNT(*) FROM `coupon_event`) AS coupon_event_count,
    (SELECT COUNT(*) FROM `coupon_event_occurrence`) AS occurrence_count,
    (SELECT COUNT(*) FROM `coupon_event_item`) AS event_item_count;

-- 한 번의 대량 조인으로 사용자 쿠폰의 참조, 상태와 발급 요청 일치 여부를 집계한다.
WITH
user_coupon_audit AS (
    SELECT
        COUNT(*) AS total_count,
        COALESCE(SUM(u.user_id IS NULL), 0) AS orphan_user_count,
        COALESCE(SUM(ce.coupon_event_id IS NULL), 0) AS orphan_event_count,
        COALESCE(SUM(cei.coupon_event_item_id IS NULL), 0) AS orphan_item_count,
        COALESCE(SUM(
            uc.coupon_event_occurrence_id IS NULL
            OR ceo.coupon_event_occurrence_id IS NULL
        ), 0) AS missing_or_orphan_occurrence_count,
        COALESCE(SUM(cr.coupon_claim_request_id IS NULL), 0) AS orphan_claim_count,
        COALESCE(SUM(
            cei.coupon_event_item_id IS NOT NULL
            AND cei.coupon_event_id <> uc.coupon_event_id
        ), 0) AS item_event_mismatch_count,
        COALESCE(SUM(
            ceo.coupon_event_occurrence_id IS NOT NULL
            AND ceo.coupon_event_id <> uc.coupon_event_id
        ), 0) AS occurrence_event_mismatch_count,
        COALESCE(SUM(
            cr.coupon_claim_request_id IS NOT NULL
            AND (
                cr.user_id <> uc.user_id
                OR cr.coupon_event_id <> uc.coupon_event_id
                OR NOT (cr.coupon_event_occurrence_id <=> uc.coupon_event_occurrence_id)
                OR cr.coupon_event_item_id <> uc.coupon_event_item_id
            )
        ), 0) AS claim_coupon_mismatch_count,
        COALESCE(SUM(
            cr.coupon_claim_request_id IS NOT NULL
            AND cr.request_status <> 'SUCCEEDED'
        ), 0) AS coupon_without_succeeded_claim_count,
        COALESCE(SUM(
            uc.coupon_status NOT IN ('ISSUED', 'USED', 'EXPIRED', 'CANCELLED')
        ), 0) AS invalid_status_count,
        COALESCE(SUM(
            uc.coupon_status = 'ISSUED'
            AND (uc.used_at IS NOT NULL OR uc.cancelled_at IS NOT NULL)
        ), 0) AS issued_timestamp_mismatch_count,
        COALESCE(SUM(
            uc.coupon_status = 'USED'
            AND (uc.used_at IS NULL OR uc.cancelled_at IS NOT NULL)
        ), 0) AS used_timestamp_mismatch_count,
        COALESCE(SUM(
            uc.coupon_status = 'CANCELLED'
            AND (
                uc.cancelled_at IS NULL
                OR uc.used_at IS NOT NULL
                OR uc.cancel_reason IS NULL
                OR TRIM(uc.cancel_reason) = ''
            )
        ), 0) AS cancelled_timestamp_mismatch_count,
        COALESCE(SUM(
            uc.coupon_status = 'EXPIRED'
            AND (
                uc.expires_at > @as_of_utc
                OR uc.used_at IS NOT NULL
                OR uc.cancelled_at IS NOT NULL
            )
        ), 0) AS expired_state_mismatch_count,
        COALESCE(SUM(
            uc.coupon_status = 'ISSUED'
            AND uc.expires_at <= @as_of_utc
        ), 0) AS logically_expired_issued_count,
        COALESCE(SUM(uc.expires_at <= uc.created_at), 0) AS invalid_validity_period_count
    FROM `user_coupon` uc
    LEFT JOIN `user` u
        ON u.user_id = uc.user_id
    LEFT JOIN `coupon_event` ce
        ON ce.coupon_event_id = uc.coupon_event_id
    LEFT JOIN `coupon_event_item` cei
        ON cei.coupon_event_item_id = uc.coupon_event_item_id
    LEFT JOIN `coupon_event_occurrence` ceo
        ON ceo.coupon_event_occurrence_id = uc.coupon_event_occurrence_id
    LEFT JOIN `coupon_claim_request` cr
        ON cr.coupon_claim_request_id = uc.claim_id
),
claim_audit AS (
    SELECT
        COUNT(*) AS total_count,
        COALESCE(SUM(u.user_id IS NULL), 0) AS orphan_user_count,
        COALESCE(SUM(ce.coupon_event_id IS NULL), 0) AS orphan_event_count,
        COALESCE(SUM(cei.coupon_event_item_id IS NULL), 0) AS orphan_item_count,
        COALESCE(SUM(
            cr.coupon_event_occurrence_id IS NULL
            OR ceo.coupon_event_occurrence_id IS NULL
        ), 0) AS missing_or_orphan_occurrence_count,
        COALESCE(SUM(
            cei.coupon_event_item_id IS NOT NULL
            AND cei.coupon_event_id <> cr.coupon_event_id
        ), 0) AS item_event_mismatch_count,
        COALESCE(SUM(
            ceo.coupon_event_occurrence_id IS NOT NULL
            AND ceo.coupon_event_id <> cr.coupon_event_id
        ), 0) AS occurrence_event_mismatch_count,
        COALESCE(SUM(
            cr.request_status NOT IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
        ), 0) AS invalid_status_count,
        COALESCE(SUM(
            cr.request_status = 'PENDING'
            AND (
                cr.completed_at IS NOT NULL
                OR cr.failure_reason IS NOT NULL
            )
        ), 0) AS pending_state_mismatch_count,
        COALESCE(SUM(
            cr.request_status = 'SUCCEEDED'
            AND (
                cr.completed_at IS NULL
                OR cr.failure_reason IS NOT NULL
            )
        ), 0) AS succeeded_state_mismatch_count,
        COALESCE(SUM(
            cr.request_status = 'FAILED'
            AND (
                cr.completed_at IS NULL
                OR cr.failure_reason IS NULL
                OR TRIM(cr.failure_reason) = ''
            )
        ), 0) AS failed_state_mismatch_count,
        COALESCE(SUM(
            cr.request_status = 'PENDING'
            AND cr.created_at < @as_of_utc - INTERVAL 10 MINUTE
        ), 0) AS stale_pending_count,
        COALESCE(SUM(
            cr.request_status = 'SUCCEEDED'
            AND uc.user_coupon_id IS NULL
        ), 0) AS succeeded_without_coupon_count
    FROM `coupon_claim_request` cr
    LEFT JOIN `user` u
        ON u.user_id = cr.user_id
    LEFT JOIN `coupon_event` ce
        ON ce.coupon_event_id = cr.coupon_event_id
    LEFT JOIN `coupon_event_item` cei
        ON cei.coupon_event_item_id = cr.coupon_event_item_id
    LEFT JOIN `coupon_event_occurrence` ceo
        ON ceo.coupon_event_occurrence_id = cr.coupon_event_occurrence_id
    LEFT JOIN `user_coupon` uc
        ON uc.claim_id = cr.coupon_claim_request_id
),
duplicate_claims AS (
    SELECT COALESCE(SUM(duplicate_count - 1), 0) AS violation_count
    FROM (
        SELECT COUNT(*) AS duplicate_count
        FROM `coupon_claim_request`
        WHERE coupon_event_occurrence_id IS NOT NULL
        GROUP BY user_id, coupon_event_occurrence_id
        HAVING COUNT(*) > 1
    ) duplicated
),
duplicate_coupons AS (
    SELECT COALESCE(SUM(duplicate_count - 1), 0) AS violation_count
    FROM (
        SELECT COUNT(*) AS duplicate_count
        FROM `user_coupon`
        WHERE coupon_event_occurrence_id IS NOT NULL
        GROUP BY user_id, coupon_event_occurrence_id
        HAVING COUNT(*) > 1
    ) duplicated
),
issued_by_item AS (
    SELECT
        coupon_event_item_id,
        COUNT(*) AS issued_count
    FROM `user_coupon`
    GROUP BY coupon_event_item_id
),
item_audit AS (
    SELECT
        COALESCE(SUM(
            GREATEST(COALESCE(issued.issued_count, 0) - item.quantity, 0)
        ), 0) AS over_issued_coupon_count,
        COALESCE(SUM(
            item.success_count <> COALESCE(issued.issued_count, 0)
        ), 0) AS success_count_mismatch_item_count,
        COALESCE(SUM(
            item.success_count < 0
            OR item.success_count > item.quantity
            OR item.quantity <= 0
        ), 0) AS invalid_stock_item_count
    FROM `coupon_event_item` item
    LEFT JOIN issued_by_item issued
        ON issued.coupon_event_item_id = item.coupon_event_item_id
),
occurrence_audit AS (
    SELECT
        COALESCE(SUM(expires_at <= opened_at), 0) AS invalid_period_count,
        COALESCE(SUM(
            occurrence_status = 'OPEN'
            AND closed_at IS NOT NULL
        ), 0) AS open_with_closed_at_count,
        COALESCE(SUM(
            occurrence_status IN ('CLOSED', 'CANCELLED')
            AND closed_at IS NULL
        ), 0) AS closed_without_closed_at_count,
        COALESCE(SUM(
            occurrence_status = 'OPEN'
            AND expires_at < @as_of_utc - INTERVAL 10 SECOND
        ), 0) AS stale_open_count
    FROM `coupon_event_occurrence`
),
checks AS (
    SELECT 'FAIL' AS severity, 'USER_COUNT_MINIMUM' AS check_name,
           GREATEST(
               CAST(1000000 AS SIGNED) - CAST((SELECT COUNT(*) FROM `user`) AS SIGNED),
               0
           ) AS violation_count,
           '가상 사용자가 100만 명 이상이어야 한다.' AS description
    UNION ALL
    SELECT 'FAIL', 'CLAIM_REQUEST_COUNT_MINIMUM',
           GREATEST(
               CAST(3000000 AS SIGNED) - CAST(total_count AS SIGNED),
               0
           ),
           '쿠폰 발급 요청 이력이 300만 건 이상이어야 한다.'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'USER_COUPON_ORPHAN_USER', orphan_user_count,
           '존재하지 않는 사용자를 참조하는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'USER_COUPON_ORPHAN_EVENT', orphan_event_count,
           '존재하지 않는 이벤트를 참조하는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'USER_COUPON_ORPHAN_ITEM', orphan_item_count,
           '존재하지 않는 이벤트 항목을 참조하는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'USER_COUPON_MISSING_OR_ORPHAN_OCCURRENCE',
           missing_or_orphan_occurrence_count,
           '회차가 없거나 존재하지 않는 회차를 참조하는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'USER_COUPON_ORPHAN_CLAIM', orphan_claim_count,
           '존재하지 않는 발급 요청을 참조하는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'USER_COUPON_ITEM_EVENT_MISMATCH', item_event_mismatch_count,
           '쿠폰과 이벤트 항목의 이벤트가 다른 건수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'USER_COUPON_OCCURRENCE_EVENT_MISMATCH',
           occurrence_event_mismatch_count,
           '쿠폰과 회차의 이벤트가 다른 건수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'CLAIM_USER_COUPON_MISMATCH', claim_coupon_mismatch_count,
           '발급 요청과 실제 쿠폰의 사용자·이벤트·회차·항목이 다른 건수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'COUPON_WITHOUT_SUCCEEDED_CLAIM',
           coupon_without_succeeded_claim_count,
           '성공하지 않은 요청에 실제 쿠폰이 연결된 건수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'USER_COUPON_INVALID_STATUS', invalid_status_count,
           'DB 계약에 없는 쿠폰 상태 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'ISSUED_TIMESTAMP_MISMATCH', issued_timestamp_mismatch_count,
           'ISSUED인데 사용·취소 시각이 존재하는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'USED_TIMESTAMP_MISMATCH', used_timestamp_mismatch_count,
           'USED 상태와 사용·취소 시각이 맞지 않는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'CANCELLED_TIMESTAMP_MISMATCH', cancelled_timestamp_mismatch_count,
           'CANCELLED 상태와 취소 시각·사유가 맞지 않는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'EXPIRED_STATE_MISMATCH', expired_state_mismatch_count,
           'EXPIRED 상태와 만료·사용·취소 정보가 맞지 않는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'INFO', 'LOGICALLY_EXPIRED_ISSUED', logically_expired_issued_count,
           '저장 상태는 ISSUED지만 API에서 계산형 EXPIRED로 해석되는 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'INVALID_COUPON_VALIDITY_PERIOD', invalid_validity_period_count,
           '만료 시각이 생성 시각보다 늦지 않은 쿠폰 수'
    FROM user_coupon_audit
    UNION ALL
    SELECT 'FAIL', 'CLAIM_ORPHAN_USER', orphan_user_count,
           '존재하지 않는 사용자의 발급 요청 수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'CLAIM_ORPHAN_EVENT', orphan_event_count,
           '존재하지 않는 이벤트의 발급 요청 수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'CLAIM_ORPHAN_ITEM', orphan_item_count,
           '존재하지 않는 이벤트 항목의 발급 요청 수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'CLAIM_MISSING_OR_ORPHAN_OCCURRENCE',
           missing_or_orphan_occurrence_count,
           '회차가 없거나 존재하지 않는 회차의 발급 요청 수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'CLAIM_ITEM_EVENT_MISMATCH', item_event_mismatch_count,
           '발급 요청과 이벤트 항목의 이벤트가 다른 건수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'CLAIM_OCCURRENCE_EVENT_MISMATCH', occurrence_event_mismatch_count,
           '발급 요청과 회차의 이벤트가 다른 건수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'CLAIM_INVALID_STATUS', invalid_status_count,
           'DB 계약에 없는 발급 요청 상태 수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'PENDING_STATE_MISMATCH', pending_state_mismatch_count,
           'PENDING 상태와 완료·실패 정보가 맞지 않는 요청 수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'SUCCEEDED_STATE_MISMATCH', succeeded_state_mismatch_count,
           'SUCCEEDED 상태와 완료·실패 정보가 맞지 않는 요청 수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'FAILED_STATE_MISMATCH', failed_state_mismatch_count,
           'FAILED 상태와 완료·실패 정보가 맞지 않는 요청 수'
    FROM claim_audit
    UNION ALL
    SELECT 'WARN', 'STALE_PENDING_CLAIM', stale_pending_count,
           '10분 넘게 PENDING인 발급 요청 수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'SUCCEEDED_CLAIM_WITHOUT_COUPON', succeeded_without_coupon_count,
           '성공했지만 실제 쿠폰이 없는 발급 요청 수'
    FROM claim_audit
    UNION ALL
    SELECT 'FAIL', 'DUPLICATE_CLAIM_PER_USER_OCCURRENCE', violation_count,
           '동일 사용자·회차의 중복 발급 요청 초과 건수'
    FROM duplicate_claims
    UNION ALL
    SELECT 'FAIL', 'DUPLICATE_COUPON_PER_USER_OCCURRENCE', violation_count,
           '동일 사용자·회차의 중복 쿠폰 초과 건수'
    FROM duplicate_coupons
    UNION ALL
    SELECT 'FAIL', 'OVER_ISSUED_COUPON', over_issued_coupon_count,
           '이벤트 항목 수량을 초과해 발급된 쿠폰 수'
    FROM item_audit
    UNION ALL
    SELECT 'FAIL', 'SUCCESS_COUNT_MISMATCH_ITEM', success_count_mismatch_item_count,
           'success_count와 실제 쿠폰 수가 다른 이벤트 항목 수'
    FROM item_audit
    UNION ALL
    SELECT 'FAIL', 'INVALID_STOCK_ITEM', invalid_stock_item_count,
           '수량 또는 success_count 범위가 잘못된 이벤트 항목 수'
    FROM item_audit
    UNION ALL
    SELECT 'FAIL', 'INVALID_OCCURRENCE_PERIOD', invalid_period_count,
           '오픈·만료 시각 순서가 잘못된 회차 수'
    FROM occurrence_audit
    UNION ALL
    SELECT 'FAIL', 'OPEN_OCCURRENCE_WITH_CLOSED_AT', open_with_closed_at_count,
           'OPEN인데 종료 시각이 존재하는 회차 수'
    FROM occurrence_audit
    UNION ALL
    SELECT 'FAIL', 'CLOSED_OCCURRENCE_WITHOUT_CLOSED_AT', closed_without_closed_at_count,
           'CLOSED/CANCELLED인데 종료 시각이 없는 회차 수'
    FROM occurrence_audit
    UNION ALL
    SELECT 'WARN', 'STALE_OPEN_OCCURRENCE', stale_open_count,
           '만료 후 10초가 지났지만 OPEN인 회차 수'
    FROM occurrence_audit
)
SELECT
    CASE
        WHEN violation_count = 0 THEN 'PASS'
        WHEN severity = 'WARN' THEN 'WARN'
        ELSE 'FAIL'
    END AS result,
    severity,
    check_name,
    violation_count,
    description
FROM checks
ORDER BY
    CASE
        WHEN violation_count > 0 AND severity = 'FAIL' THEN 0
        WHEN violation_count > 0 AND severity = 'WARN' THEN 1
        ELSE 2
    END,
    check_name;

-- 데이터가 변하지 않았음을 재실행 간 비교할 수 있는 비식별 지문이다.
-- 충돌 가능성이 있는 CRC32 기반이므로 정합성 검사를 대체하지 않고 재현성 확인에만 사용한다.
SELECT
    'coupon_claim_request' AS table_name,
    COUNT(*) AS row_count,
    MIN(coupon_claim_request_id) AS min_id,
    MAX(coupon_claim_request_id) AS max_id,
    BIT_XOR(CRC32(CONCAT_WS(
        '#',
        coupon_claim_request_id,
        user_id,
        coupon_event_id,
        COALESCE(coupon_event_occurrence_id, ''),
        coupon_event_item_id,
        request_status,
        DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f')
    ))) AS data_fingerprint
FROM `coupon_claim_request`
UNION ALL
SELECT
    'user_coupon',
    COUNT(*),
    MIN(user_coupon_id),
    MAX(user_coupon_id),
    BIT_XOR(CRC32(CONCAT_WS(
        '#',
        user_coupon_id,
        claim_id,
        user_id,
        coupon_event_id,
        COALESCE(coupon_event_occurrence_id, ''),
        coupon_event_item_id,
        coupon_status,
        DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f')
    )))
FROM `user_coupon`;

-- 대량 검증과 성공 수량 동기화에 필요한 선두 인덱스 존재 여부를 확인한다.
SELECT
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'WARN' END AS result,
    'USER_COUPON_ITEM_LEADING_INDEX' AS check_name,
    COUNT(*) AS matching_index_count,
    'user_coupon(coupon_event_item_id) 선두 인덱스가 없으면 300만 건 집계 비용이 커진다.'
        AS description
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'user_coupon'
  AND column_name = 'coupon_event_item_id'
  AND seq_in_index = 1;

COMMIT;
