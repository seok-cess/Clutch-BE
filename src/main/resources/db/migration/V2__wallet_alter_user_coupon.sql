ALTER TABLE `user_coupon`
    ADD COLUMN `claim_id`       BIGINT        NOT NULL COMMENT '발급요청식별자',
    ADD COLUMN `discount_type`  VARCHAR(20)   NOT NULL COMMENT '할인타입(RATE 또는 AMOUNT)',
    ADD COLUMN `discount_value` DECIMAL(10,2) NOT NULL COMMENT '할인값(발급시점스냅샷)',
    ADD COLUMN `expires_at`     DATETIME(6)   NOT NULL COMMENT '만료시각',
    ADD COLUMN `used_at`        DATETIME(6)   NULL     COMMENT '사용시각',
    ADD COLUMN `cancelled_at`   DATETIME(6)   NULL     COMMENT '취소시각',
    ADD COLUMN `cancel_reason`  VARCHAR(255)  NULL     COMMENT '취소사유';

ALTER TABLE `user_coupon`
    ADD CONSTRAINT `uk_user_coupon_claim_id` UNIQUE (`claim_id`);

CREATE INDEX `idx_user_coupon_user_status_expires`
    ON `user_coupon` (`user_id`, `coupon_status`, `expires_at`, `id`);

CREATE TABLE `wallet_outbox` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `aggregate_id` BIGINT       NOT NULL COMMENT '집계식별자(userCouponId 또는 claimId)',
    `topic`        VARCHAR(100) NOT NULL COMMENT '발행토픽명',
    `payload`      JSON         NOT NULL COMMENT '이벤트페이로드',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '발행상태(PENDING, SENT)',
    `retry_count`  INT          NOT NULL DEFAULT 0 COMMENT '재시도횟수',
    `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `sent_at`      DATETIME(6)  NULL COMMENT '발행시각',
    PRIMARY KEY (`id`),
    KEY `idx_wallet_outbox_status_id` (`status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE coupon_event
    ADD CONSTRAINT uk_coupon_event_match UNIQUE (match_id);