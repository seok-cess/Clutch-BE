CREATE TABLE `team_stat` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `team_id` BIGINT NOT NULL COMMENT '팀식별자',
    `kill` INT NOT NULL COMMENT '킬',
    `total_gold` INT NOT NULL COMMENT '골드',
    `tower_count` INT NOT NULL COMMENT '부순 타워',
    `inhibitor_count` INT NOT NULL COMMENT '부순 억제기',
    `dragon_count` INT NOT NULL COMMENT '드래곤수',
    `baron_count` INT NOT NULL COMMENT '바론수',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    KEY `idx_team_stat_team_id` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_claim_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `coupon_event_id` BIGINT NOT NULL COMMENT '쿠폰이벤트식별자',
    `coupon_event_item_id` BIGINT NOT NULL COMMENT '쿠폰식별자',
    `user_id` BIGINT NOT NULL COMMENT '유저식별자',
    `request_status` VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '쿠폰발급상태',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_claim_request_user_item` (`user_id`, `coupon_event_item_id`),
    KEY `idx_claim_request_event_status` (`coupon_event_id`, `request_status`),
    KEY `idx_claim_request_item` (`coupon_event_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `match_id` BIGINT NOT NULL COMMENT '경기식별자',
    `event_type` VARCHAR(50) NOT NULL COMMENT '이벤트타입',
    `description` VARCHAR(500) NOT NULL COMMENT '이벤트설명',
    `started_at` DATETIME(6) NOT NULL COMMENT '이벤트시작시각',
    `closed_at` DATETIME(6) NOT NULL COMMENT '이벤트종료시각',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    KEY `idx_coupon_event_match_id` (`match_id`),
    KEY `idx_coupon_event_period` (`started_at`, `closed_at`),
    CONSTRAINT `chk_coupon_event_period` CHECK (`closed_at` > `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_coupon` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `user_id` BIGINT NOT NULL COMMENT '유저식별자',
    `coupon_event_id` BIGINT NOT NULL COMMENT '쿠폰이벤트식별자',
    `coupon_event_item_id` BIGINT NOT NULL COMMENT '쿠폰식별자',
    `coupon_code` VARCHAR(100) NOT NULL COMMENT '쿠폰코드',
    `coupon_status` VARCHAR(30) NOT NULL DEFAULT 'ISSUED' COMMENT '쿠폰상태',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_coupon_code` (`coupon_code`),
    UNIQUE KEY `uk_user_coupon_user_item` (`user_id`, `coupon_event_item_id`),
    KEY `idx_user_coupon_user_status` (`user_id`, `coupon_status`),
    KEY `idx_user_coupon_event` (`coupon_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_type` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `coupon_name` VARCHAR(100) NOT NULL COMMENT '쿠폰이름',
    `discount_value` INT NOT NULL COMMENT '할인율',
    `status` VARCHAR(30) NOT NULL COMMENT '쿠폰상태',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_coupon_type_discount` CHECK (`discount_value` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_event_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `coupon_event_id` BIGINT NOT NULL COMMENT '쿠폰이벤트식별자',
    `coupon_type_id` BIGINT NOT NULL COMMENT '쿠폰타입식별자',
    `quantity` INT NOT NULL COMMENT '수량',
    `success_count` INT NOT NULL DEFAULT 0 COMMENT '발급수량',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_coupon_event_item_event_type` (`coupon_event_id`, `coupon_type_id`),
    KEY `idx_coupon_event_item_type` (`coupon_type_id`),
    CONSTRAINT `chk_coupon_event_item_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `chk_coupon_event_item_success_count` CHECK (`success_count` >= 0 AND `success_count` <= `quantity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `match_view_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `user_id` BIGINT NOT NULL COMMENT '유저식별자',
    `match_id` BIGINT NOT NULL COMMENT '경기식별자',
    `started_at` DATETIME(6) NOT NULL COMMENT '시청시작시각',
    `ended_at` DATETIME(6) NULL COMMENT '시청종료시각',
    `session_status` VARCHAR(30) NOT NULL COMMENT '세션상태',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    KEY `idx_match_view_session_user` (`user_id`),
    KEY `idx_match_view_session_match_status` (`match_id`, `session_status`),
    CONSTRAINT `chk_match_view_session_period` CHECK (`ended_at` IS NULL OR `ended_at` >= `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `dragon` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `team_stat_id` BIGINT NOT NULL COMMENT '팀상황판식별자',
    `dragon_type` VARCHAR(30) NOT NULL COMMENT '드래곤종류',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    KEY `idx_dragon_team_stat_id` (`team_stat_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `match` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `team_id` BIGINT NOT NULL COMMENT '팀식별자',
    `started_at` DATETIME(6) NOT NULL COMMENT '시작시각',
    `ended_at` DATETIME(6) NULL COMMENT '종료시각',
    `match_status` VARCHAR(30) NOT NULL COMMENT '매치상태',
    `match_result` VARCHAR(30) NULL COMMENT '매치결과',
    `video_url` VARCHAR(2048) NOT NULL COMMENT '매치url',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    KEY `idx_match_team_id` (`team_id`),
    KEY `idx_match_status_started_at` (`match_status`, `started_at`),
    CONSTRAINT `chk_match_period` CHECK (`ended_at` IS NULL OR `ended_at` >= `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `team_name` VARCHAR(100) NOT NULL COMMENT '팀이름',
    `status` VARCHAR(30) NOT NULL COMMENT '팀상태',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_team_name` (`team_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `role` VARCHAR(20) NOT NULL COMMENT '권한(ADMIN, USER)',
    `email` VARCHAR(255) NOT NULL COMMENT '이메일',
    `point` BIGINT NOT NULL DEFAULT 0 COMMENT '포인트',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_email` (`email`),
    CONSTRAINT `chk_user_point` CHECK (`point` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `player` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `team_id` BIGINT NOT NULL COMMENT '팀식별자',
    `player_name` VARCHAR(100) NOT NULL COMMENT '선수명',
    `position` VARCHAR(30) NOT NULL COMMENT '포지션',
    `champion_name` VARCHAR(100) NOT NULL COMMENT '챔피언이름',
    `champion_level` INT NOT NULL COMMENT '챔피언레벨',
    `kill` INT NOT NULL COMMENT '킬',
    `death` INT NOT NULL COMMENT '데스',
    `assist` INT NOT NULL COMMENT '어시스트',
    `gold` INT NOT NULL COMMENT '골드',
    `cs` INT NOT NULL COMMENT '크립스코어',
    `damage_share` DECIMAL(5,2) NOT NULL COMMENT '딜량 비중',
    `kill_participation` DECIMAL(5,2) NOT NULL COMMENT '킬 관여율',
    `wards_placed` INT NOT NULL COMMENT '설치 와드 수',
    `wards_destroyed` INT NOT NULL COMMENT '파괴 와드 수',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`id`),
    KEY `idx_player_team_id` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
