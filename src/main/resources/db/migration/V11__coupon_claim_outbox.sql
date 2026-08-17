CREATE TABLE `coupon_claim_outbox` (
                                       `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
                                       `message_id` VARCHAR(36) NOT NULL COMMENT '이벤트메시지식별자',
                                       `aggregate_id` BIGINT NOT NULL COMMENT '쿠폰발급요청식별자',
                                       `topic` VARCHAR(100) NOT NULL COMMENT '발행토픽명',
                                       `payload` JSON NOT NULL COMMENT '이벤트페이로드',
                                       `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                           COMMENT '발행상태(PENDING, SENT)',
                                       `retry_count` INT NOT NULL DEFAULT 0 COMMENT '재시도횟수',
                                       `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '생성시각',
                                       `sent_at` DATETIME(6) NULL COMMENT '발행완료시각',

                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_coupon_claim_outbox_message_id` (`message_id`),
                                       UNIQUE KEY `uk_coupon_claim_outbox_aggregate_topic`
                                           (`aggregate_id`, `topic`),
                                       KEY `idx_coupon_claim_outbox_status_id` (`status`, `id`),

                                       CONSTRAINT `chk_coupon_claim_outbox_status`
                                           CHECK (`status` IN ('PENDING', 'SENT')),
                                       CONSTRAINT `chk_coupon_claim_outbox_retry_count`
                                           CHECK (`retry_count` >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;