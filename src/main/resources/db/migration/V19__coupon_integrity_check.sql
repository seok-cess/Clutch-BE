CREATE TABLE `coupon_integrity_check` (
    `coupon_integrity_check_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '검증 실행 식별자',
    `execution_status` VARCHAR(20) NOT NULL COMMENT '실행 상태',
    `overall_verdict` VARCHAR(20) NULL COMMENT '완료된 실행의 전체 판정',
    `requested_by` BIGINT NOT NULL COMMENT '실행 요청 관리자',
    `as_of_utc` DATETIME(6) NULL COMMENT '일관 스냅샷 기준 시각',
    `started_at` DATETIME(6) NOT NULL COMMENT '실행 시작 시각',
    `completed_at` DATETIME(6) NULL COMMENT '실행 완료 시각',
    `user_count` BIGINT NULL,
    `claim_request_count` BIGINT NULL,
    `user_coupon_count` BIGINT NULL,
    `coupon_event_count` BIGINT NULL,
    `occurrence_count` BIGINT NULL,
    `event_item_count` BIGINT NULL,
    `claim_request_min_id` BIGINT NULL,
    `claim_request_max_id` BIGINT NULL,
    `claim_request_fingerprint` BIGINT UNSIGNED NULL,
    `user_coupon_min_id` BIGINT NULL,
    `user_coupon_max_id` BIGINT NULL,
    `user_coupon_fingerprint` BIGINT UNSIGNED NULL,
    `check_count` BIGINT NULL,
    `pass_count` BIGINT NULL,
    `info_count` BIGINT NULL,
    `warn_count` BIGINT NULL,
    `fail_count` BIGINT NULL,
    `error_code` VARCHAR(100) NULL,
    `error_message` VARCHAR(500) NULL,
    PRIMARY KEY (`coupon_integrity_check_id`),
    KEY `idx_coupon_integrity_check_started_at` (`started_at`),
    KEY `idx_coupon_integrity_check_status_started_at` (`execution_status`, `started_at`),
    CONSTRAINT `fk_coupon_integrity_check_requested_by`
        FOREIGN KEY (`requested_by`) REFERENCES `user` (`user_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_coupon_integrity_check_execution_status`
        CHECK (`execution_status` IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT `chk_coupon_integrity_check_overall_verdict`
        CHECK (`overall_verdict` IS NULL OR `overall_verdict` IN ('PASS', 'WARN', 'FAIL')),
    CONSTRAINT `chk_coupon_integrity_check_lifecycle`
        CHECK (
            (`execution_status` = 'RUNNING' AND `overall_verdict` IS NULL AND `completed_at` IS NULL)
            OR (`execution_status` = 'COMPLETED' AND `overall_verdict` IS NOT NULL AND `completed_at` IS NOT NULL)
            OR (`execution_status` = 'FAILED' AND `overall_verdict` IS NULL AND `completed_at` IS NOT NULL)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_integrity_check_result` (
    `coupon_integrity_check_result_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '검사항목 결과 식별자',
    `coupon_integrity_check_id` BIGINT NOT NULL COMMENT '검증 실행 식별자',
    `check_code` VARCHAR(100) NOT NULL COMMENT '공개 후 변경하지 않는 검사 코드',
    `severity` VARCHAR(20) NOT NULL COMMENT '검사 심각도',
    `verdict` VARCHAR(20) NOT NULL COMMENT '검사항목 판정',
    `violation_count` BIGINT NOT NULL COMMENT '위반 집계 건수',
    `description` VARCHAR(500) NOT NULL COMMENT '검사항목 설명',
    `display_order` INT NOT NULL COMMENT '표시 순서',
    PRIMARY KEY (`coupon_integrity_check_result_id`),
    UNIQUE KEY `uk_coupon_integrity_check_result_code` (`coupon_integrity_check_id`, `check_code`),
    CONSTRAINT `fk_coupon_integrity_check_result_check`
        FOREIGN KEY (`coupon_integrity_check_id`)
        REFERENCES `coupon_integrity_check` (`coupon_integrity_check_id`)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT `chk_coupon_integrity_check_result_severity`
        CHECK (`severity` IN ('INFO', 'WARN', 'FAIL')),
    CONSTRAINT `chk_coupon_integrity_check_result_verdict`
        CHECK (`verdict` IN ('PASS', 'INFO', 'WARN', 'FAIL')),
    CONSTRAINT `chk_coupon_integrity_check_result_count`
        CHECK (`violation_count` >= 0),
    CONSTRAINT `chk_coupon_integrity_check_result_order`
        CHECK (`display_order` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
