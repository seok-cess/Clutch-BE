-- Flyway V7: 한 시청 세션에서 여러 포인트 수령 회차를 기록할 수 있도록 거래 유일성 기준을 변경한다.

ALTER TABLE `watch_point_transaction`
    ADD COLUMN `reward_sequence` BIGINT NOT NULL COMMENT '세션 내 포인트 수령 회차'
        AFTER `watch_session_id`,
    DROP INDEX `uk_watch_point_transaction_session`,
    ADD UNIQUE KEY `uk_watch_point_transaction_session_reward`
        (`watch_session_id`, `reward_sequence`),
    ADD CONSTRAINT `chk_watch_point_transaction_reward_sequence`
        CHECK (`reward_sequence` >= 1);
