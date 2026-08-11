-- Flyway V2: 기존 경기 도메인을 신규 반정규화 경기 도메인으로 교체한다.
-- 기존 경기 도메인을 신규 반정규화 경기 도메인으로 교체한다.
-- 전제: 기존 match/team/player/team_stat/dragon 및 match 참조 컬럼에 보존할 데이터가 없다.
-- 순서: 신규 테이블 생성 -> 기존 match 참조 재연결 -> 레거시 경기 테이블 삭제.

CREATE TABLE `esports_match` (
    `esports_match_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '내부 매치 식별자',
    `external_match_id` VARCHAR(32) NOT NULL COMMENT 'LoL Esports matchId',
    `league_external_id` VARCHAR(32) NOT NULL COMMENT '수집 대상 리그 외부 식별자',
    `league_name` VARCHAR(100) NULL COMMENT '경기 당시 리그 표시명',
    `league_slug` VARCHAR(100) NULL COMMENT '경기 당시 리그 slug',
    `season_key` VARCHAR(50) NOT NULL COMMENT 'H2H 집계 시즌 키(예: 2026)',
    `tournament_external_id` VARCHAR(32) NULL COMMENT '확인된 경우 대회 외부 식별자',
    `block_name` VARCHAR(100) NULL COMMENT '주차/라운드 표시명',
    `scheduled_at` DATETIME(6) NOT NULL COMMENT '예정 시작 시각(UTC)',
    `started_at` DATETIME(6) NULL COMMENT '실제 매치 시작 시각(UTC)',
    `ended_at` DATETIME(6) NULL COMMENT '매치 종료 시각(UTC)',
    `lifecycle_status` VARCHAR(20) NOT NULL COMMENT 'unstarted/inProgress/completed 원본 상태',
    `best_of` INT NULL COMMENT '다전제 수(1/3/5 등)',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (`esports_match_id`),
    UNIQUE KEY `uk_esports_match_external_id` (`external_match_id`),
    KEY `idx_esports_match_season_schedule` (`league_external_id`, `season_key`, `scheduled_at`),
    KEY `idx_esports_match_tournament_schedule` (`tournament_external_id`, `scheduled_at`),
    KEY `idx_esports_match_status_schedule` (`lifecycle_status`, `scheduled_at`),
    CONSTRAINT `chk_esports_match_season_key`
        CHECK (CHAR_LENGTH(TRIM(`season_key`)) > 0),
    CONSTRAINT `chk_esports_match_best_of`
        CHECK (`best_of` IS NULL OR `best_of` > 0),
    CONSTRAINT `chk_esports_match_period`
        CHECK (`ended_at` IS NULL OR `started_at` IS NULL OR `ended_at` >= `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `match_team` (
    `match_team_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '매치 참가 팀 식별자',
    `match_id` BIGINT NOT NULL COMMENT '내부 매치 식별자',
    `external_team_id` VARCHAR(32) NULL COMMENT 'LoL Esports 팀 식별자; TBD이면 NULL',
    `display_order` INT NOT NULL COMMENT '매치 헤더 표시 순서(1/2)',
    `team_code` VARCHAR(16) NULL COMMENT '경기 당시 팀 코드',
    `team_name` VARCHAR(100) NULL COMMENT '경기 당시 팀 이름',
    `team_image_url` VARCHAR(2048) NULL COMMENT '경기 당시 팀 로고 URL',
    `outcome` VARCHAR(10) NULL COMMENT '매치 기준 win/loss',
    `game_wins` INT NULL COMMENT '해당 매치 세트 승수',
    `record_wins_snapshot` INT NULL COMMENT '일정 응답 당시 전적 승수',
    `record_losses_snapshot` INT NULL COMMENT '일정 응답 당시 전적 패수',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (`match_team_id`),
    UNIQUE KEY `uk_match_team_match_order` (`match_id`, `display_order`),
    UNIQUE KEY `uk_match_team_match_external` (`match_id`, `external_team_id`),
    UNIQUE KEY `uk_match_team_id_match` (`match_team_id`, `match_id`),
    KEY `idx_match_team_external_match` (`external_team_id`, `match_id`),
    KEY `idx_match_team_match_outcome` (`match_id`, `outcome`),
    CONSTRAINT `fk_match_team_match`
        FOREIGN KEY (`match_id`) REFERENCES `esports_match` (`esports_match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_match_team_display_order`
        CHECK (`display_order` IN (1, 2)),
    CONSTRAINT `chk_match_team_outcome`
        CHECK (`outcome` IS NULL OR `outcome` IN ('win', 'loss')),
    CONSTRAINT `chk_match_team_game_wins`
        CHECK (`game_wins` IS NULL OR `game_wins` >= 0),
    CONSTRAINT `chk_match_team_record_wins`
        CHECK (`record_wins_snapshot` IS NULL OR `record_wins_snapshot` >= 0),
    CONSTRAINT `chk_match_team_record_losses`
        CHECK (`record_losses_snapshot` IS NULL OR `record_losses_snapshot` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `esports_game` (
    `esports_game_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '내부 세트 식별자',
    `external_game_id` VARCHAR(32) NOT NULL COMMENT 'LoL Esports gameId',
    `match_id` BIGINT NOT NULL COMMENT '내부 매치 식별자',
    `game_number` INT NOT NULL COMMENT '매치 내 세트 번호',
    `blue_match_team_id` BIGINT NULL COMMENT '이 세트의 블루 진영 match_team 식별자',
    `red_match_team_id` BIGINT NULL COMMENT '이 세트의 레드 진영 match_team 식별자',
    `lifecycle_status` VARCHAR(20) NOT NULL COMMENT 'unstarted/inProgress/completed 원본 상태',
    `telemetry_state` VARCHAR(30) NULL COMMENT '마지막 Window frame gameState 원본',
    `patch_version` VARCHAR(50) NULL COMMENT '피드 patch/build 버전',
    `data_dragon_version` VARCHAR(20) NULL COMMENT '아이콘 조회용 Data Dragon 버전',
    `started_at` DATETIME(6) NULL COMMENT '게임 시작 시각 및 경과 시간 계산 기준(UTC)',
    `ended_at` DATETIME(6) NULL COMMENT '게임 종료 시각(UTC)',
    `duration_seconds` INT NULL COMMENT '최종 게임 경과 초',
    `final_window_frame_at` DATETIME(6) NULL COMMENT '최종 Window 통계 frame 시각(UTC)',
    `final_details_frame_at` DATETIME(6) NULL COMMENT '최종 Details 통계 frame 시각(UTC)',
    `blue_total_gold` BIGINT NULL COMMENT '블루 최종 총 골드',
    `red_total_gold` BIGINT NULL COMMENT '레드 최종 총 골드',
    `blue_total_kills` INT NULL COMMENT '블루 최종 킬',
    `red_total_kills` INT NULL COMMENT '레드 최종 킬',
    `blue_towers` INT NULL COMMENT '블루 최종 포탑 수',
    `red_towers` INT NULL COMMENT '레드 최종 포탑 수',
    `blue_inhibitors` INT NULL COMMENT '블루 최종 억제기 수',
    `red_inhibitors` INT NULL COMMENT '레드 최종 억제기 수',
    `blue_barons` INT NULL COMMENT '블루 최종 바론 수',
    `red_barons` INT NULL COMMENT '레드 최종 바론 수',
    `blue_dragons_json` JSON NULL COMMENT '블루가 획득한 용 종류의 순서 배열',
    `red_dragons_json` JSON NULL COMMENT '레드가 획득한 용 종류의 순서 배열',
    `objectives_json` JSON NULL COMMENT '그래프용 오브젝트 이벤트 배열',
    `window_collection_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Window 수집 상태',
    `details_collection_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Details 수집 상태',
    `timeline_collection_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Timeline 수집 상태',
    `timeline_covered_from_seconds` INT NULL COMMENT '수집된 timeline 시작 경과 초',
    `timeline_covered_to_seconds` INT NULL COMMENT '수집된 timeline 종료 경과 초',
    `finalized_at` DATETIME(6) NULL COMMENT '필수 데이터 최종 확정 시각',
    `last_collection_error` VARCHAR(1000) NULL COMMENT '마지막 수집 오류 요약',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (`esports_game_id`),
    UNIQUE KEY `uk_esports_game_external_id` (`external_game_id`),
    UNIQUE KEY `uk_esports_game_match_number` (`match_id`, `game_number`),
    KEY `idx_esports_game_match_status` (`match_id`, `lifecycle_status`),
    KEY `idx_esports_game_blue_match_team` (`blue_match_team_id`, `match_id`),
    KEY `idx_esports_game_red_match_team` (`red_match_team_id`, `match_id`),
    CONSTRAINT `fk_esports_game_match`
        FOREIGN KEY (`match_id`) REFERENCES `esports_match` (`esports_match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_esports_game_blue_match_team`
        FOREIGN KEY (`blue_match_team_id`, `match_id`)
        REFERENCES `match_team` (`match_team_id`, `match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_esports_game_red_match_team`
        FOREIGN KEY (`red_match_team_id`, `match_id`)
        REFERENCES `match_team` (`match_team_id`, `match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_esports_game_number`
        CHECK (`game_number` > 0),
    CONSTRAINT `chk_esports_game_different_sides`
        CHECK (`blue_match_team_id` IS NULL OR `red_match_team_id` IS NULL
            OR `blue_match_team_id` <> `red_match_team_id`),
    CONSTRAINT `chk_esports_game_period`
        CHECK (`ended_at` IS NULL OR `started_at` IS NULL OR `ended_at` >= `started_at`),
    CONSTRAINT `chk_esports_game_duration`
        CHECK (`duration_seconds` IS NULL OR `duration_seconds` >= 0),
    CONSTRAINT `chk_esports_game_team_stats`
        CHECK ((`blue_total_gold` IS NULL OR `blue_total_gold` >= 0)
            AND (`red_total_gold` IS NULL OR `red_total_gold` >= 0)
            AND (`blue_total_kills` IS NULL OR `blue_total_kills` >= 0)
            AND (`red_total_kills` IS NULL OR `red_total_kills` >= 0)
            AND (`blue_towers` IS NULL OR `blue_towers` >= 0)
            AND (`red_towers` IS NULL OR `red_towers` >= 0)
            AND (`blue_inhibitors` IS NULL OR `blue_inhibitors` >= 0)
            AND (`red_inhibitors` IS NULL OR `red_inhibitors` >= 0)
            AND (`blue_barons` IS NULL OR `blue_barons` >= 0)
            AND (`red_barons` IS NULL OR `red_barons` >= 0)),
    CONSTRAINT `chk_esports_game_blue_dragons_json`
        CHECK (`blue_dragons_json` IS NULL OR JSON_TYPE(`blue_dragons_json`) = 'ARRAY'),
    CONSTRAINT `chk_esports_game_red_dragons_json`
        CHECK (`red_dragons_json` IS NULL OR JSON_TYPE(`red_dragons_json`) = 'ARRAY'),
    CONSTRAINT `chk_esports_game_objectives_json`
        CHECK (`objectives_json` IS NULL OR JSON_TYPE(`objectives_json`) = 'ARRAY'),
    CONSTRAINT `chk_esports_game_window_status`
        CHECK (`window_collection_status` IN ('PENDING', 'PARTIAL', 'COMPLETE', 'UNAVAILABLE')),
    CONSTRAINT `chk_esports_game_details_status`
        CHECK (`details_collection_status` IN ('PENDING', 'PARTIAL', 'COMPLETE', 'UNAVAILABLE')),
    CONSTRAINT `chk_esports_game_timeline_status`
        CHECK (`timeline_collection_status` IN ('PENDING', 'PARTIAL', 'COMPLETE', 'UNAVAILABLE')),
    CONSTRAINT `chk_esports_game_timeline_coverage`
        CHECK ((`timeline_covered_from_seconds` IS NULL OR `timeline_covered_from_seconds` >= 0)
            AND (`timeline_covered_to_seconds` IS NULL OR `timeline_covered_to_seconds` >= 0)
            AND (`timeline_covered_from_seconds` IS NULL OR `timeline_covered_to_seconds` IS NULL
                OR `timeline_covered_to_seconds` >= `timeline_covered_from_seconds`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `game_player_stat` (
    `game_player_stat_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '세트별 선수 기록 식별자',
    `game_id` BIGINT NOT NULL COMMENT '내부 세트 식별자',
    `match_team_id` BIGINT NULL COMMENT '선수가 속한 match_team 식별자',
    `participant_no` INT NOT NULL COMMENT '세트 내부 참가 번호(1~10)',
    `side` VARCHAR(4) NOT NULL COMMENT 'blue/red',
    `external_player_id` VARCHAR(32) NULL COMMENT 'upstream esportsPlayerId',
    `summoner_name` VARCHAR(100) NULL COMMENT '경기 당시 소환사명',
    `champion_id` VARCHAR(50) NULL COMMENT '챔피언 식별자',
    `role` VARCHAR(20) NULL COMMENT 'top/jungle/mid/bottom/support',
    `level` INT NULL COMMENT '최종 레벨',
    `kills` INT NULL COMMENT '최종 킬',
    `deaths` INT NULL COMMENT '최종 데스',
    `assists` INT NULL COMMENT '최종 어시스트',
    `creep_score` INT NULL COMMENT '최종 CS',
    `total_gold` BIGINT NULL COMMENT 'Window scoreboard 총 골드',
    `total_gold_earned` BIGINT NULL COMMENT 'Details 획득 골드',
    `kill_participation_ratio` DECIMAL(7,6) NULL COMMENT '킬 관여율 원본 비율(0~1)',
    `champion_damage_share_ratio` DECIMAL(7,6) NULL COMMENT '챔피언 피해 비중 원본 비율(0~1)',
    `wards_placed` INT NULL COMMENT '설치 와드 수',
    `wards_destroyed` INT NULL COMMENT '파괴 와드 수',
    `items_json` JSON NULL COMMENT '최종 아이템 ID 순서 배열',
    `perks_json` JSON NULL COMMENT 'style/subStyle/perks 객체',
    `window_snapshot_at` DATETIME(6) NULL COMMENT 'Window 선수 기록 frame 시각(UTC)',
    `details_snapshot_at` DATETIME(6) NULL COMMENT 'Details 선수 기록 frame 시각(UTC)',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (`game_player_stat_id`),
    UNIQUE KEY `uk_game_player_stat_game_participant` (`game_id`, `participant_no`),
    KEY `idx_game_player_stat_game_side` (`game_id`, `side`),
    KEY `idx_game_player_stat_match_team` (`match_team_id`),
    KEY `idx_game_player_stat_external_player` (`external_player_id`, `game_id`),
    CONSTRAINT `fk_game_player_stat_game`
        FOREIGN KEY (`game_id`) REFERENCES `esports_game` (`esports_game_id`)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT `fk_game_player_stat_match_team`
        FOREIGN KEY (`match_team_id`) REFERENCES `match_team` (`match_team_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_game_player_stat_participant`
        CHECK (`participant_no` BETWEEN 1 AND 10),
    CONSTRAINT `chk_game_player_stat_side`
        CHECK (`side` IN ('blue', 'red')),
    CONSTRAINT `chk_game_player_stat_counters`
        CHECK ((`level` IS NULL OR `level` >= 0)
            AND (`kills` IS NULL OR `kills` >= 0)
            AND (`deaths` IS NULL OR `deaths` >= 0)
            AND (`assists` IS NULL OR `assists` >= 0)
            AND (`creep_score` IS NULL OR `creep_score` >= 0)
            AND (`total_gold` IS NULL OR `total_gold` >= 0)
            AND (`total_gold_earned` IS NULL OR `total_gold_earned` >= 0)
            AND (`wards_placed` IS NULL OR `wards_placed` >= 0)
            AND (`wards_destroyed` IS NULL OR `wards_destroyed` >= 0)),
    CONSTRAINT `chk_game_player_stat_ratios`
        CHECK ((`kill_participation_ratio` IS NULL
                OR `kill_participation_ratio` BETWEEN 0 AND 1)
            AND (`champion_damage_share_ratio` IS NULL
                OR `champion_damage_share_ratio` BETWEEN 0 AND 1)),
    CONSTRAINT `chk_game_player_stat_items_json`
        CHECK (`items_json` IS NULL OR JSON_TYPE(`items_json`) = 'ARRAY'),
    CONSTRAINT `chk_game_player_stat_perks_json`
        CHECK (`perks_json` IS NULL OR JSON_TYPE(`perks_json`) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `game_timeline_point` (
    `game_timeline_point_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '게임 시계열 지점 식별자',
    `game_id` BIGINT NOT NULL COMMENT '내부 세트 식별자',
    `game_time_seconds` INT NOT NULL COMMENT '게임 시작 기준 경과 초',
    `source_frame_at` DATETIME(6) NOT NULL COMMENT '원본 Window frame 시각(UTC)',
    `blue_gold` BIGINT NOT NULL COMMENT '블루 총 골드',
    `red_gold` BIGINT NOT NULL COMMENT '레드 총 골드',
    `gold_diff` BIGINT GENERATED ALWAYS AS (`blue_gold` - `red_gold`) STORED COMMENT '블루-레드 골드 차',
    `blue_kills` INT NOT NULL COMMENT '블루 총 킬',
    `red_kills` INT NOT NULL COMMENT '레드 총 킬',
    `blue_towers` INT NOT NULL COMMENT '블루 포탑 수',
    `red_towers` INT NOT NULL COMMENT '레드 포탑 수',
    `blue_inhibitors` INT NOT NULL COMMENT '블루 억제기 수',
    `red_inhibitors` INT NOT NULL COMMENT '레드 억제기 수',
    `blue_barons` INT NOT NULL COMMENT '블루 바론 수',
    `red_barons` INT NOT NULL COMMENT '레드 바론 수',
    `blue_dragon_count` INT NOT NULL COMMENT '블루 드래곤 수',
    `red_dragon_count` INT NOT NULL COMMENT '레드 드래곤 수',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (`game_timeline_point_id`),
    UNIQUE KEY `uk_timeline_game_time` (`game_id`, `game_time_seconds`),
    UNIQUE KEY `uk_timeline_game_source_frame` (`game_id`, `source_frame_at`),
    CONSTRAINT `fk_game_timeline_point_game`
        FOREIGN KEY (`game_id`) REFERENCES `esports_game` (`esports_game_id`)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT `chk_game_timeline_point_nonnegative`
        CHECK (`game_time_seconds` >= 0
            AND `blue_gold` >= 0
            AND `red_gold` >= 0
            AND `blue_kills` >= 0
            AND `red_kills` >= 0
            AND `blue_towers` >= 0
            AND `red_towers` >= 0
            AND `blue_inhibitors` >= 0
            AND `red_inhibitors` >= 0
            AND `blue_barons` >= 0
            AND `red_barons` >= 0
            AND `blue_dragon_count` >= 0
            AND `red_dragon_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- V1에서 유지되는 테이블의 범용 id PK를 ERDCloud 규칙인 <table_name>_id로 변경한다.
ALTER TABLE `coupon_claim_request`
    RENAME COLUMN `id` TO `coupon_claim_request_id`;

ALTER TABLE `coupon_event`
    RENAME COLUMN `id` TO `coupon_event_id`;

ALTER TABLE `user_coupon`
    RENAME COLUMN `id` TO `user_coupon_id`;

ALTER TABLE `coupon_type`
    RENAME COLUMN `id` TO `coupon_type_id`;

ALTER TABLE `coupon_event_item`
    RENAME COLUMN `id` TO `coupon_event_item_id`;

ALTER TABLE `match_view_session`
    RENAME COLUMN `id` TO `match_view_session_id`;

ALTER TABLE `user`
    RENAME COLUMN `id` TO `user_id`;

-- 기존 쿠폰/시청 도메인의 match_id를 ERDCloud 명칭인 esports_match_id로 변경하고
-- 신규 esports_match.esports_match_id를 참조하도록 교체한다.
-- 예상과 달리 기존 행이 존재하고 신규 esports_match 와 매칭되지 않으면 FK 추가 단계에서 실패한다.
ALTER TABLE `coupon_event`
    RENAME COLUMN `match_id` TO `esports_match_id`,
    RENAME INDEX `idx_coupon_event_match_id` TO `idx_coupon_event_esports_match_id`;

ALTER TABLE `coupon_event`
    MODIFY COLUMN `esports_match_id` BIGINT NOT NULL COMMENT 'esports_match 식별자',
    ADD CONSTRAINT `fk_coupon_event_esports_match`
        FOREIGN KEY (`esports_match_id`) REFERENCES `esports_match` (`esports_match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `match_view_session`
    RENAME COLUMN `match_id` TO `esports_match_id`,
    RENAME INDEX `idx_match_view_session_match_status`
        TO `idx_match_view_session_esports_match_status`;

ALTER TABLE `match_view_session`
    MODIFY COLUMN `esports_match_id` BIGINT NOT NULL COMMENT 'esports_match 식별자',
    ADD CONSTRAINT `fk_match_view_session_esports_match`
        FOREIGN KEY (`esports_match_id`) REFERENCES `esports_match` (`esports_match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

-- MySQL DDL은 자동 커밋되므로 신규 구조 생성과 참조 재연결이 성공한 뒤 마지막에 삭제한다.
DROP TABLE `dragon`;
DROP TABLE `team_stat`;
DROP TABLE `player`;
DROP TABLE `match`;
DROP TABLE `team`;
