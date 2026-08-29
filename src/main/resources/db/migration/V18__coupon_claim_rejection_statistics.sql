CREATE TABLE `coupon_claim_rejection_message` (
    `message_id` VARCHAR(100) NOT NULL COMMENT 'Kafka메시지식별자',
    `coupon_event_id` BIGINT NOT NULL COMMENT '요청한쿠폰이벤트식별자',
    `coupon_event_occurrence_id` BIGINT NOT NULL COMMENT '요청한쿠폰이벤트회차식별자',
    `rejection_reason` VARCHAR(100) NOT NULL COMMENT '신청거절오류코드',
    `occurred_at` DATETIME(6) NOT NULL COMMENT '거절발생시각UTC',
    `processed_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '통계처리시각',

    PRIMARY KEY (`message_id`),
    KEY `idx_coupon_claim_rejection_occurred`
        (`occurred_at`),
    KEY `idx_coupon_claim_rejection_event_occurred`
        (`coupon_event_id`, `occurred_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
