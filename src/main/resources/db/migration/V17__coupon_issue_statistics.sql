CREATE TABLE `coupon_issue_statistics` (
    `coupon_event_id` BIGINT NOT NULL COMMENT '쿠폰이벤트식별자',
    `success_count` BIGINT NOT NULL DEFAULT 0 COMMENT '발급성공수',
    `failure_count` BIGINT NOT NULL DEFAULT 0 COMMENT '발급실패수',
    `processing_error_count` BIGINT NOT NULL DEFAULT 0 COMMENT 'Kafka처리오류수',
    `last_result_at` DATETIME(6) NULL COMMENT '최근발급결과시각',
    `last_error_at` DATETIME(6) NULL COMMENT '최근Kafka처리오류시각',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',

    PRIMARY KEY (`coupon_event_id`),

    CONSTRAINT `fk_coupon_issue_statistics_event`
        FOREIGN KEY (`coupon_event_id`)
        REFERENCES `coupon_event` (`coupon_event_id`)
        ON DELETE CASCADE,
    CONSTRAINT `chk_coupon_issue_statistics_success_count`
        CHECK (`success_count` >= 0),
    CONSTRAINT `chk_coupon_issue_statistics_failure_count`
        CHECK (`failure_count` >= 0),
    CONSTRAINT `chk_coupon_issue_statistics_error_count`
        CHECK (`processing_error_count` >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_issue_statistics_message` (
    `message_id` VARCHAR(100) NOT NULL COMMENT 'Kafka메시지식별자',
    `claim_id` BIGINT NOT NULL COMMENT '쿠폰발급요청식별자',
    `coupon_event_id` BIGINT NOT NULL COMMENT '쿠폰이벤트식별자',
    `result_status` VARCHAR(20) NOT NULL COMMENT '발급결과상태',
    `occurred_at` DATETIME(6) NOT NULL COMMENT '발급결과발생시각',
    `processed_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '집계처리시각',

    PRIMARY KEY (`message_id`),
    KEY `idx_coupon_issue_statistics_message_event_status`
        (`coupon_event_id`, `result_status`),
    KEY `idx_coupon_issue_statistics_message_claim`
        (`claim_id`),

    CONSTRAINT `fk_coupon_issue_statistics_message_event`
        FOREIGN KEY (`coupon_event_id`)
        REFERENCES `coupon_event` (`coupon_event_id`)
        ON DELETE CASCADE,
    CONSTRAINT `chk_coupon_issue_statistics_message_status`
        CHECK (`result_status` IN ('SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_kafka_processing_error` (
    `coupon_kafka_processing_error_id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '식별자',
    `original_consumer_group` VARCHAR(200) NOT NULL COMMENT '원본Consumer그룹',
    `original_topic` VARCHAR(200) NOT NULL COMMENT '원본토픽',
    `original_partition` INT NOT NULL COMMENT '원본파티션',
    `original_offset` BIGINT NOT NULL COMMENT '원본오프셋',
    `message_id` VARCHAR(100) NULL COMMENT 'Kafka메시지식별자',
    `claim_id` BIGINT NULL COMMENT '쿠폰발급요청식별자',
    `coupon_event_id` BIGINT NULL COMMENT '쿠폰이벤트식별자',
    `exception_type` VARCHAR(500) NULL COMMENT '예외타입',
    `exception_message` VARCHAR(1000) NULL COMMENT '예외메시지',
    `payload` VARCHAR(4000) NULL COMMENT '오류메시지페이로드',
    `original_occurred_at` DATETIME(6) NULL COMMENT '원본메시지발생시각',
    `recorded_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '오류기록시각',

    PRIMARY KEY (`coupon_kafka_processing_error_id`),
    UNIQUE KEY `uk_coupon_kafka_processing_error_origin`
        (`original_consumer_group`, `original_topic`,
         `original_partition`, `original_offset`),
    KEY `idx_coupon_kafka_processing_error_event_recorded`
        (`coupon_event_id`, `recorded_at`),
    KEY `idx_coupon_kafka_processing_error_recorded`
        (`recorded_at`),

    CONSTRAINT `fk_coupon_kafka_processing_error_event`
        FOREIGN KEY (`coupon_event_id`)
        REFERENCES `coupon_event` (`coupon_event_id`)
        ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

-- 배포 전에 생성된 결과 Outbox를 처리 완료 메시지로 선등록해 재전송 시
-- 통계가 중복 증가하지 않도록 한다.
INSERT IGNORE INTO `coupon_issue_statistics_message` (
    `message_id`,
    `claim_id`,
    `coupon_event_id`,
    `result_status`,
    `occurred_at`,
    `processed_at`
)
SELECT JSON_UNQUOTE(JSON_EXTRACT(outbox.payload, '$.messageId')),
       claim.coupon_claim_request_id,
       claim.coupon_event_id,
       JSON_UNQUOTE(JSON_EXTRACT(outbox.payload, '$.status')),
       outbox.created_at,
       outbox.created_at
  FROM wallet_outbox outbox
  JOIN coupon_claim_request claim
    ON claim.coupon_claim_request_id = outbox.aggregate_id
 WHERE outbox.topic = 'coupon.issue.result'
   AND JSON_UNQUOTE(JSON_EXTRACT(outbox.payload, '$.messageId')) IS NOT NULL
   AND JSON_UNQUOTE(JSON_EXTRACT(outbox.payload, '$.status'))
       IN ('SUCCEEDED', 'FAILED');

INSERT INTO `coupon_issue_statistics` (
    `coupon_event_id`,
    `success_count`,
    `failure_count`,
    `processing_error_count`,
    `last_result_at`
)
SELECT message.coupon_event_id,
       SUM(message.result_status = 'SUCCEEDED'),
       SUM(message.result_status = 'FAILED'),
       0,
       MAX(message.occurred_at)
  FROM coupon_issue_statistics_message message
 GROUP BY message.coupon_event_id;
