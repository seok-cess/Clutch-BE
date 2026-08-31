CREATE TABLE `coupon_issue_daily_statistics` (
    `statistics_date` DATE NOT NULL COMMENT 'KST통계기준일',
    `coupon_event_id` BIGINT NOT NULL COMMENT '쿠폰이벤트식별자',
    `success_count` BIGINT NOT NULL DEFAULT 0 COMMENT '발급성공수',
    `failure_count` BIGINT NOT NULL DEFAULT 0 COMMENT '발급처리실패수',
    `rejection_count` BIGINT NOT NULL DEFAULT 0 COMMENT '발급전신청거절수',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',

    PRIMARY KEY (`statistics_date`, `coupon_event_id`),

    CONSTRAINT `chk_coupon_issue_daily_success_count`
        CHECK (`success_count` >= 0),
    CONSTRAINT `chk_coupon_issue_daily_failure_count`
        CHECK (`failure_count` >= 0),
    CONSTRAINT `chk_coupon_issue_daily_rejection_count`
        CHECK (`rejection_count` >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

-- 배포 전에 멱등 처리된 Kafka 결과를 기존 운영 홈과 동일하게 Claim 생성일
-- KST 기준으로 한 번만 집계한다. Flyway 완료 후 Consumer가 신규 결과를 누적한다.
INSERT INTO `coupon_issue_daily_statistics` (
    `statistics_date`,
    `coupon_event_id`,
    `success_count`,
    `failure_count`,
    `rejection_count`
)
SELECT DATE(DATE_ADD(claim.created_at, INTERVAL 9 HOUR)),
       message.coupon_event_id,
       SUM(message.result_status = 'SUCCEEDED'),
       SUM(message.result_status = 'FAILED'),
       0
  FROM coupon_issue_statistics_message message
  JOIN coupon_claim_request claim
    ON claim.coupon_claim_request_id = message.claim_id
 GROUP BY DATE(DATE_ADD(claim.created_at, INTERVAL 9 HOUR)),
          message.coupon_event_id;

-- Kafka 멱등 원본이 없는 이전 완료 Claim도 기존 운영 홈 통계가 유지되도록
-- 최초 백필에만 포함한다.
INSERT INTO `coupon_issue_daily_statistics` (
    `statistics_date`,
    `coupon_event_id`,
    `success_count`,
    `failure_count`,
    `rejection_count`
)
SELECT DATE(DATE_ADD(claim.created_at, INTERVAL 9 HOUR)),
       claim.coupon_event_id,
       SUM(claim.request_status = 'SUCCEEDED'),
       SUM(claim.request_status = 'FAILED'),
       0
  FROM coupon_claim_request claim
 WHERE claim.request_status IN ('SUCCEEDED', 'FAILED')
   AND NOT EXISTS (
       SELECT 1
         FROM coupon_issue_statistics_message message
        WHERE message.claim_id = claim.coupon_claim_request_id
   )
 GROUP BY DATE(DATE_ADD(claim.created_at, INTERVAL 9 HOUR)),
          claim.coupon_event_id
ON DUPLICATE KEY UPDATE
    success_count = success_count + VALUES(success_count),
    failure_count = failure_count + VALUES(failure_count);

-- Claim 생성 전에 종료된 품절·중복·장애 거절도 같은 KST 일별 통계에
-- 포함한다. 거절 이벤트는 대상 이벤트가 정리된 뒤에도 도착할 수 있어 FK를 두지 않는다.
INSERT INTO `coupon_issue_daily_statistics` (
    `statistics_date`,
    `coupon_event_id`,
    `success_count`,
    `failure_count`,
    `rejection_count`
)
SELECT DATE(DATE_ADD(rejection.occurred_at, INTERVAL 9 HOUR)),
       rejection.coupon_event_id,
       0,
       0,
       COUNT(*)
  FROM coupon_claim_rejection_message rejection
 GROUP BY DATE(DATE_ADD(rejection.occurred_at, INTERVAL 9 HOUR)),
          rejection.coupon_event_id
ON DUPLICATE KEY UPDATE
    rejection_count = rejection_count + VALUES(rejection_count);
