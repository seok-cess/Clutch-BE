-- Flyway V10: 세트별 배팅 이벤트, 사용자 배팅과 포인트 거래 원장을 생성한다.

CREATE TABLE `betting_event` (
    `betting_event_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '배팅이벤트식별자',
    `external_match_id` VARCHAR(32) NOT NULL COMMENT 'LoL Esports 외부 매치 식별자',
    `external_game_id` VARCHAR(32) NULL COMMENT 'LoL Esports 외부 세트 식별자',
    `set_number` INT NOT NULL COMMENT '매치 내 세트 번호',
    `first_external_team_id` VARCHAR(32) NOT NULL COMMENT '첫 번째 배팅 선택지 팀 외부 식별자',
    `second_external_team_id` VARCHAR(32) NOT NULL COMMENT '두 번째 배팅 선택지 팀 외부 식별자',
    `winner_external_team_id` VARCHAR(32) NULL COMMENT '확정된 승리 팀 외부 식별자',
    `status` VARCHAR(20) NOT NULL COMMENT '배팅이벤트상태(OPEN, CLOSED, SETTLED, CANCELLED)',
    `opened_at` DATETIME(6) NOT NULL COMMENT '배팅오픈시각(UTC)',
    `closes_at` DATETIME(6) NULL COMMENT '배팅마감예정시각(UTC)',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`betting_event_id`),
    UNIQUE KEY `uk_betting_event_match_set` (`external_match_id`, `set_number`),
    UNIQUE KEY `uk_betting_event_game` (`external_game_id`),
    KEY `idx_betting_event_match_status` (`external_match_id`, `status`),
    KEY `idx_betting_event_status_closes` (`status`, `closes_at`),
    CONSTRAINT `chk_betting_event_set_number`
        CHECK (`set_number` > 0),
    CONSTRAINT `chk_betting_event_different_teams`
        CHECK (`first_external_team_id` <> `second_external_team_id`),
    CONSTRAINT `chk_betting_event_status`
        CHECK (`status` IN ('OPEN', 'CLOSED', 'SETTLED', 'CANCELLED')),
    CONSTRAINT `chk_betting_event_winner`
        CHECK (`winner_external_team_id` IS NULL
            OR `winner_external_team_id` IN (`first_external_team_id`, `second_external_team_id`)),
    CONSTRAINT `chk_betting_event_settled_winner`
        CHECK (`status` <> 'SETTLED' OR `winner_external_team_id` IS NOT NULL),
    CONSTRAINT `chk_betting_event_period`
        CHECK (`closes_at` IS NULL OR `closes_at` >= `opened_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_bet` (
    `user_bet_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자배팅식별자',
    `betting_event_id` BIGINT NOT NULL COMMENT '배팅이벤트식별자',
    `user_id` BIGINT NOT NULL COMMENT '유저식별자',
    `selected_external_team_id` VARCHAR(32) NOT NULL COMMENT '사용자가 선택한 팀 외부 식별자',
    `amount` BIGINT NOT NULL COMMENT '배팅포인트',
    `status` VARCHAR(20) NOT NULL COMMENT '배팅정산상태(PLACED, WON, LOST, REFUNDED)',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`user_bet_id`),
    UNIQUE KEY `uk_user_bet_event_user` (`betting_event_id`, `user_id`),
    KEY `idx_user_bet_event_status` (`betting_event_id`, `status`),
    KEY `idx_user_bet_user_created` (`user_id`, `created_at`),
    CONSTRAINT `fk_user_bet_event`
        FOREIGN KEY (`betting_event_id`) REFERENCES `betting_event` (`betting_event_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_user_bet_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_user_bet_amount`
        CHECK (`amount` BETWEEN 1000 AND 100000),
    CONSTRAINT `chk_user_bet_status`
        CHECK (`status` IN ('PLACED', 'WON', 'LOST', 'REFUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `bet_point_transaction` (
    `bet_point_transaction_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '배팅포인트거래식별자',
    `user_bet_id` BIGINT NOT NULL COMMENT '사용자배팅식별자',
    `transaction_type` VARCHAR(20) NOT NULL COMMENT '거래유형(STAKE, PAYOUT, REFUND)',
    `point_delta` BIGINT NOT NULL COMMENT '실제 포인트 증감값',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    PRIMARY KEY (`bet_point_transaction_id`),
    UNIQUE KEY `uk_bet_point_transaction_bet_type` (`user_bet_id`, `transaction_type`),
    KEY `idx_bet_point_transaction_bet_created` (`user_bet_id`, `created_at`),
    CONSTRAINT `fk_bet_point_transaction_user_bet`
        FOREIGN KEY (`user_bet_id`) REFERENCES `user_bet` (`user_bet_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_bet_point_transaction_type`
        CHECK (`transaction_type` IN ('STAKE', 'PAYOUT', 'REFUND')),
    CONSTRAINT `chk_bet_point_transaction_delta`
        CHECK ((`transaction_type` = 'STAKE' AND `point_delta` < 0)
            OR (`transaction_type` IN ('PAYOUT', 'REFUND') AND `point_delta` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
