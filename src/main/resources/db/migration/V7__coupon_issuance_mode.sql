-- 쿠폰 이벤트 발급 방식
ALTER TABLE `coupon_event`
    ADD COLUMN `issuance_mode` VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
    COMMENT '발급방식(STANDARD 또는 TIME_TIERED)'
        AFTER `claim_window_seconds`,
    ADD CONSTRAINT `chk_coupon_event_issuance_mode`
        CHECK (`issuance_mode` IN ('STANDARD', 'TIME_TIERED'));

-- 쿠폰 이벤트 항목 활성 시간
ALTER TABLE `coupon_event_item`
    ADD COLUMN `available_from_seconds` INT NOT NULL DEFAULT 0
    COMMENT '회차 시작 기준 활성 시작 초'
        AFTER `success_count`,
    ADD COLUMN `available_until_seconds` INT NULL
        COMMENT '회차 시작 기준 활성 종료 초'
        AFTER `available_from_seconds`;

-- 기존 쿠폰 항목은 회차 전체 시간 동안 활성화
UPDATE `coupon_event_item` AS `item`
    JOIN `coupon_event` AS `event`
ON `event`.`coupon_event_id` =
    `item`.`coupon_event_id`
    SET `item`.`available_until_seconds` =
        `event`.`claim_window_seconds`
WHERE `item`.`available_until_seconds` IS NULL;

-- 시간 조건과 조회 인덱스
ALTER TABLE `coupon_event_item`
    MODIFY COLUMN `available_until_seconds` INT NOT NULL DEFAULT 300
    COMMENT '회차 시작 기준 활성 종료 초',
    ADD CONSTRAINT `chk_coupon_event_item_available_from`
    CHECK (`available_from_seconds` >= 0),
    ADD CONSTRAINT `chk_coupon_event_item_available_period`
    CHECK (
    `available_until_seconds` >
    `available_from_seconds`
    ),
    ADD KEY `idx_coupon_event_item_available_window` (
    `coupon_event_id`,
    `available_from_seconds`,
    `available_until_seconds`
    );