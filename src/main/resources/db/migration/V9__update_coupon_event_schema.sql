-- 쿠폰 이벤트 발급 방식 추가
-- 일반 선착순과 차등 혜택 모두 경기 트리거 감지 즉시 시작한다.
-- 두 방식은 issue_mode로 구분한다.

ALTER TABLE `coupon_event`
    ADD COLUMN `issue_mode` VARCHAR(30) NOT NULL
        DEFAULT 'SINGLE_FIRST_COME'
    COMMENT '쿠폰 발급 방식(SINGLE_FIRST_COME, PHASED_FIRST_COME)'
        AFTER `event_name`;

ALTER TABLE `coupon_event`
    ADD CONSTRAINT `chk_coupon_event_issue_mode`
        CHECK (
            `issue_mode` IN (
                             'SINGLE_FIRST_COME',
                             'PHASED_FIRST_COME'
                )
            );


-- 차등 혜택 이벤트의 시간대별 쿠폰 단계를 저장한다.
--
-- 예:
-- phase_sequence 1 / open_offset_seconds 0  / 10% 쿠폰
-- phase_sequence 2 / open_offset_seconds 30 / 20% 쿠폰
-- phase_sequence 3 / open_offset_seconds 60 / 30% 쿠폰
--
-- 일반 선착순도 한 개의 phase를 사용한다.
-- phase_sequence 1 / open_offset_seconds 0

CREATE TABLE `coupon_event_phase` (
                                      `coupon_event_phase_id` BIGINT NOT NULL AUTO_INCREMENT
        COMMENT '쿠폰 이벤트 단계 식별자',

                                      `coupon_event_id` BIGINT NOT NULL
                                          COMMENT '쿠폰 이벤트 식별자',

                                      `coupon_event_item_id` BIGINT NOT NULL
                                          COMMENT '해당 단계에서 발급할 쿠폰 이벤트 항목 식별자',

                                      `phase_sequence` INT NOT NULL
                                          COMMENT '이벤트 내 단계 순서(1부터 시작)',

                                      `open_offset_seconds` INT NOT NULL
                                          COMMENT '이벤트 오픈 후 해당 단계가 시작되는 경과 초',

                                      `created_at` DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '생성 시각',

                                      `updated_at` DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
        COMMENT '수정 시각',

                                      PRIMARY KEY (`coupon_event_phase_id`),

                                      UNIQUE KEY `uk_coupon_event_phase_sequence`
                                          (`coupon_event_id`, `phase_sequence`),

                                      UNIQUE KEY `uk_coupon_event_phase_offset`                   (`coupon_event_id`, `open_offset_seconds`),

                                      UNIQUE KEY `uk_coupon_event_phase_item`
                                          (`coupon_event_item_id`),

                                      KEY `idx_coupon_event_phase_event`
                                          (`coupon_event_id`),

                                      CONSTRAINT `fk_coupon_event_phase_event`
                                          FOREIGN KEY (`coupon_event_id`)
                                              REFERENCES `coupon_event` (`coupon_event_id`)
                                              ON UPDATE RESTRICT
                                              ON DELETE RESTRICT,

                                      CONSTRAINT `fk_coupon_event_phase_item`
                                          FOREIGN KEY (`coupon_event_item_id`)
                                              REFERENCES `coupon_event_item` (`coupon_event_item_id`)
                                              ON UPDATE RESTRICT
                                              ON DELETE RESTRICT,

                                      CONSTRAINT `chk_coupon_event_phase_sequence`
                                          CHECK (`phase_sequence` >= 1),

                                      CONSTRAINT `chk_coupon_event_phase_offset`
                                          CHECK (`open_offset_seconds` >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;