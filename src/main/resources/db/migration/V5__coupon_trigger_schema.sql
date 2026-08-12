-- Flyway V5: 쿠폰 트리거 도메인을 ERD 에 맞춘다.
--
-- 배경: 쿠폰 이벤트는 "경기 중 특정 사건(퍼블·22분 바론·펜타킬 등)이 발생하면 선착순 발급"
--       구조다. 기존 스키마는 이벤트 정의와 실제 발생을 구분하지 않았고,
--       매치당 이벤트가 1건으로 제약돼 있어 트리거 여러 종을 담지 못했다.
--
-- 이번 변경의 뼈대
--   esports_match_event      소스 피드에서 감지한 원본 사건 (판정 근거)
--   coupon_event             이벤트 "정의" — 어떤 트리거에 어떤 쿠폰을 걸지
--   coupon_event_occurrence  그 정의가 실제로 발동한 "회차" — 오픈·마감 시각
--   coupon_claim_request     회차별 사용자 발급 요청
--
-- 쿠폰 도메인 테이블은 현재 전부 비어 있어 데이터 손실이 없다.

-- ---------------------------------------------------------------------------
-- 1. 경기 중 발생 이벤트 — 트리거 판정의 원본 기록
-- ---------------------------------------------------------------------------

CREATE TABLE `esports_match_event` (
    `match_event_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `esports_match_id` BIGINT NOT NULL COMMENT '경기식별자',
    `external_event_id` VARCHAR(100) NULL COMMENT '외부이벤트식별자(중복 감지 방지용)',
    `event_type` VARCHAR(50) NOT NULL COMMENT '이벤트유형(FIRST_BLOOD/BARON/PENTAKILL 등)',
    `occurred_at` DATETIME(6) NOT NULL COMMENT '발생시각',
    `event_data` JSON NULL COMMENT '이벤트상세데이터',
    `processed_at` DATETIME(6) NULL COMMENT '쿠폰 발동 처리 시각(미처리면 NULL)',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    PRIMARY KEY (`match_event_id`),
    -- 같은 사건을 두 번 감지해도 한 번만 기록되게 한다 (폴링 재시도·재기동 대비)
    UNIQUE KEY `uk_match_event_external` (`external_event_id`),
    KEY `idx_match_event_match_type` (`esports_match_id`, `event_type`),
    KEY `idx_match_event_unprocessed` (`processed_at`, `occurred_at`),
    CONSTRAINT `fk_match_event_esports_match`
        FOREIGN KEY (`esports_match_id`) REFERENCES `esports_match` (`esports_match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_match_event_data_json`
        CHECK (`event_data` IS NULL OR JSON_TYPE(`event_data`) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 2. coupon_event — 이벤트 "정의"
--
--    매치당 1건 제약을 푼다. 한 경기에서 퍼블·22분 바론·펜타킬이 모두 터질 수 있으므로
--    (매치, 트리거유형) 단위로 유니크를 다시 잡는다.
--    started_at/closed_at 은 정의 시점에 알 수 없다(사건이 언제 터질지 모른다) —
--    실제 오픈·마감 시각은 coupon_event_occurrence 가 갖는다.
-- ---------------------------------------------------------------------------

ALTER TABLE `coupon_event`
    DROP CHECK `chk_coupon_event_period`;

ALTER TABLE `coupon_event`
    DROP INDEX `uk_coupon_event_match`;

ALTER TABLE `coupon_event`
    ADD COLUMN `event_name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '이벤트이름' AFTER `esports_match_id`,
    ADD COLUMN `trigger_type` VARCHAR(50) NOT NULL DEFAULT 'MANUAL' COMMENT '이벤트발생조건' AFTER `event_name`,
    ADD COLUMN `event_status` VARCHAR(20) NOT NULL DEFAULT 'READY' COMMENT '전체이벤트상태' AFTER `trigger_type`,
    ADD COLUMN `claim_window_seconds` INT NOT NULL DEFAULT 300 COMMENT '오픈 후 신청 가능 시간(초)' AFTER `event_status`;

-- 발동 시각은 occurrence 로 옮겨간다
ALTER TABLE `coupon_event`
    DROP COLUMN `started_at`,
    DROP COLUMN `closed_at`,
    DROP COLUMN `event_type`,
    DROP COLUMN `description`;

ALTER TABLE `coupon_event`
    ADD UNIQUE KEY `uk_coupon_event_match_trigger` (`esports_match_id`, `trigger_type`),
    ADD CONSTRAINT `chk_coupon_event_status`
        CHECK (`event_status` IN ('READY', 'OPEN', 'CLOSED', 'CANCELLED')),
    ADD CONSTRAINT `chk_coupon_event_claim_window`
        CHECK (`claim_window_seconds` > 0);

-- ---------------------------------------------------------------------------
-- 3. coupon_event_occurrence — 정의가 실제로 발동한 회차
--
--    "언제 사건이 감지됐고(detected_at), 언제 신청을 열었고(opened_at),
--     언제 닫히는지(expires_at)" 를 담는다. 발급 요청은 이 회차에 매달린다.
-- ---------------------------------------------------------------------------

CREATE TABLE `coupon_event_occurrence` (
    `coupon_event_occurrence_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
    `coupon_event_id` BIGINT NOT NULL COMMENT '쿠폰이벤트식별자',
    `match_event_id` BIGINT NULL COMMENT '발동 근거가 된 경기 이벤트',
    `source_event_key` VARCHAR(100) NULL COMMENT '외부 이벤트 중복 발동 방지 키',
    `game_time_seconds` INT NULL COMMENT '게임 시작 후 발생 초',
    `source_occurred_at` DATETIME(6) NULL COMMENT '원본 데이터상 실제 발생 시각',
    `detected_at` DATETIME(6) NOT NULL COMMENT '서버가 감지한 시각',
    `opened_at` DATETIME(6) NOT NULL COMMENT '신청 오픈 시각',
    `expires_at` DATETIME(6) NOT NULL COMMENT '신청 마감 시각',
    `closed_at` DATETIME(6) NULL COMMENT '실제 종료 시각',
    `occurrence_status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '회차상태',
    `close_reason` VARCHAR(50) NULL COMMENT '종료사유(SOLD_OUT/EXPIRED/CANCELLED)',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성시각',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정시각',
    PRIMARY KEY (`coupon_event_occurrence_id`),
    -- 같은 사건으로 두 번 발동하지 않게 한다 (재기동·중복 감지 대비)
    UNIQUE KEY `uk_occurrence_source_key` (`source_event_key`),
    KEY `idx_occurrence_event_status` (`coupon_event_id`, `occurrence_status`),
    KEY `idx_occurrence_open_window` (`occurrence_status`, `expires_at`),
    KEY `idx_occurrence_match_event` (`match_event_id`),
    CONSTRAINT `fk_occurrence_coupon_event`
        FOREIGN KEY (`coupon_event_id`) REFERENCES `coupon_event` (`coupon_event_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_occurrence_match_event`
        FOREIGN KEY (`match_event_id`) REFERENCES `esports_match_event` (`match_event_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_occurrence_status`
        CHECK (`occurrence_status` IN ('OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT `chk_occurrence_period`
        CHECK (`expires_at` > `opened_at`),
    CONSTRAINT `chk_occurrence_game_time`
        CHECK (`game_time_seconds` IS NULL OR `game_time_seconds` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 4. coupon_type — 정률/정액 할인 구분
--
--    기존 discount_value 는 INT + "0~100" CHECK 라 정률만 가능했다.
--    정액(예: 3000원)을 담으려면 타입과 범위를 함께 바꿔야 한다.
-- ---------------------------------------------------------------------------

ALTER TABLE `coupon_type`
    DROP CHECK `chk_coupon_type_discount`;

ALTER TABLE `coupon_type`
    ADD COLUMN `discount_type` VARCHAR(20) NOT NULL DEFAULT 'RATE' COMMENT '할인타입(RATE 또는 AMOUNT)' AFTER `coupon_name`,
    MODIFY COLUMN `discount_value` DECIMAL(10,2) NOT NULL COMMENT '할인값';

ALTER TABLE `coupon_type`
    ADD CONSTRAINT `chk_coupon_type_discount_type`
        CHECK (`discount_type` IN ('RATE', 'AMOUNT')),
    -- 정률은 0~100(%), 정액은 양수면 된다
    ADD CONSTRAINT `chk_coupon_type_discount_value`
        CHECK ((`discount_type` = 'RATE'   AND `discount_value` BETWEEN 0 AND 100)
            OR (`discount_type` = 'AMOUNT' AND `discount_value` > 0));

-- ---------------------------------------------------------------------------
-- 5. coupon_claim_request — 회차 연결 + 실패 사유 기록
--
--    유니크를 (user, item) 에서 (user, occurrence) 로 옮긴다.
--    같은 쿠폰이라도 회차가 다르면 다시 신청할 수 있어야 한다
--    (1세트 퍼블로 받고 2세트 퍼블로 또 받는 경우).
-- ---------------------------------------------------------------------------

ALTER TABLE `coupon_claim_request`
    ADD COLUMN `coupon_event_occurrence_id` BIGINT NULL COMMENT '쿠폰이벤트회차식별자' AFTER `coupon_event_id`,
    ADD COLUMN `completed_at` DATETIME(6) NULL COMMENT '처리 완료 시각' AFTER `updated_at`,
    ADD COLUMN `failure_code` VARCHAR(30) NULL COMMENT '실패코드' AFTER `completed_at`,
    ADD COLUMN `failure_reason` VARCHAR(500) NULL COMMENT '실패사유' AFTER `failure_code`;

ALTER TABLE `coupon_claim_request`
    DROP INDEX `uk_claim_request_user_item`;

ALTER TABLE `coupon_claim_request`
    ADD UNIQUE KEY `uk_claim_request_user_occurrence` (`user_id`, `coupon_event_occurrence_id`),
    ADD KEY `idx_claim_request_occurrence` (`coupon_event_occurrence_id`),
    ADD CONSTRAINT `fk_claim_request_occurrence`
        FOREIGN KEY (`coupon_event_occurrence_id`)
        REFERENCES `coupon_event_occurrence` (`coupon_event_occurrence_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    -- 값은 ClaimRequestStatus enum 과 일치해야 한다 (PENDING/SUCCEEDED/FAILED)
    ADD CONSTRAINT `chk_claim_request_status`
        CHECK (`request_status` IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED'));

-- ---------------------------------------------------------------------------
-- 6. user_coupon — 회차 연결
--    같은 쿠폰을 회차마다 받을 수 있으므로 (user, item) 유니크를 (user, occurrence) 로 옮긴다.
-- ---------------------------------------------------------------------------

ALTER TABLE `user_coupon`
    ADD COLUMN `coupon_event_occurrence_id` BIGINT NULL COMMENT '쿠폰이벤트회차식별자' AFTER `coupon_event_id`;

ALTER TABLE `user_coupon`
    DROP INDEX `uk_user_coupon_user_item`;

ALTER TABLE `user_coupon`
    ADD UNIQUE KEY `uk_user_coupon_user_occurrence` (`user_id`, `coupon_event_occurrence_id`),
    ADD KEY `idx_user_coupon_occurrence` (`coupon_event_occurrence_id`),
    ADD CONSTRAINT `fk_user_coupon_occurrence`
        FOREIGN KEY (`coupon_event_occurrence_id`)
        REFERENCES `coupon_event_occurrence` (`coupon_event_occurrence_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT `chk_user_coupon_status`
        CHECK (`coupon_status` IN ('ISSUED', 'USED', 'EXPIRED', 'CANCELLED')),
    ADD CONSTRAINT `chk_user_coupon_discount_type`
        CHECK (`discount_type` IN ('RATE', 'AMOUNT'));
