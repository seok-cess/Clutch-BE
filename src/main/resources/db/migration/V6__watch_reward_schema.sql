-- Flyway V6: 기존 경기 시청 세션을 시청시간 기반 포인트 지급 구조로 확장한다.
--
-- match_view_session은 watch_session으로 이름을 바꾸고 기존 행을 보존한다.
-- 기존 started_at은 entered_at으로, last_seen_at의 초기값으로 사용한다.

RENAME TABLE `match_view_session` TO `watch_session`;

ALTER TABLE `watch_session`
    DROP CHECK `chk_match_view_session_period`,
    DROP FOREIGN KEY `fk_match_view_session_esports_match`;

ALTER TABLE `watch_session`
    CHANGE COLUMN `match_view_session_id` `watch_session_id`
        BIGINT NOT NULL AUTO_INCREMENT COMMENT '시청세션식별자',
    ADD COLUMN `session_key` VARCHAR(36) NULL COMMENT '시청세션 외부식별자'
        AFTER `watch_session_id`,
    CHANGE COLUMN `started_at` `entered_at`
        DATETIME(6) NOT NULL COMMENT '시청입장시각',
    ADD COLUMN `last_seen_at` DATETIME(6) NULL COMMENT '마지막 heartbeat 확인시각'
        AFTER `entered_at`,
    ADD COLUMN `eligible_milliseconds` BIGINT NOT NULL DEFAULT 0 COMMENT '누적 유효 시청시간(ms)'
        AFTER `last_seen_at`,
    CHANGE COLUMN `session_status` `status`
        VARCHAR(20) NOT NULL COMMENT '시청세션상태(WATCHING, COMPLETED)';

-- 기존 데이터가 있다면 세션 키와 마지막 확인 시각을 채우고 상태값을 신규 정책에 맞춘다.
UPDATE `watch_session`
SET `session_key` = UUID(),
    `last_seen_at` = `entered_at`,
    `status` = CASE
        WHEN `ended_at` IS NULL AND UPPER(`status`) IN ('WATCHING', 'ACTIVE') THEN 'WATCHING'
        ELSE 'COMPLETED'
    END;

ALTER TABLE `watch_session`
    MODIFY COLUMN `session_key` VARCHAR(36) NOT NULL COMMENT '시청세션 외부식별자',
    MODIFY COLUMN `last_seen_at` DATETIME(6) NOT NULL COMMENT '마지막 heartbeat 확인시각',
    DROP COLUMN `ended_at`,
    DROP INDEX `idx_match_view_session_user`,
    RENAME INDEX `idx_match_view_session_esports_match_status`
        TO `idx_watch_session_esports_match_status`,
    ADD UNIQUE KEY `uk_watch_session_session_key` (`session_key`),
    ADD KEY `idx_watch_session_user_status` (`user_id`, `status`),
    ADD CONSTRAINT `fk_watch_session_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT `fk_watch_session_esports_match`
        FOREIGN KEY (`esports_match_id`) REFERENCES `esports_match` (`esports_match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT `chk_watch_session_status`
        CHECK (`status` IN ('WATCHING', 'COMPLETED')),
    ADD CONSTRAINT `chk_watch_session_eligible_milliseconds`
        CHECK (`eligible_milliseconds` >= 0);

CREATE TABLE `watch_point_transaction` (
    `watch_point_transaction_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '시청포인트거래식별자',
    `user_id` BIGINT NOT NULL COMMENT '유저식별자',
    `watch_session_id` BIGINT NOT NULL COMMENT '시청세션식별자',
    `esports_match_id` BIGINT NOT NULL COMMENT 'esports_match 식별자',
    `awarded_point` BIGINT NOT NULL COMMENT '최종지급포인트',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`watch_point_transaction_id`),
    UNIQUE KEY `uk_watch_point_transaction_session` (`watch_session_id`),
    KEY `idx_watch_point_transaction_user_created` (`user_id`, `created_at`),
    KEY `idx_watch_point_transaction_esports_match` (`esports_match_id`),
    CONSTRAINT `fk_watch_point_transaction_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_watch_point_transaction_session`
        FOREIGN KEY (`watch_session_id`) REFERENCES `watch_session` (`watch_session_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_watch_point_transaction_esports_match`
        FOREIGN KEY (`esports_match_id`) REFERENCES `esports_match` (`esports_match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_watch_point_transaction_awarded_point`
        CHECK (`awarded_point` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
