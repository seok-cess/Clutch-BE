ALTER TABLE `coupon_type`
    MODIFY COLUMN `status`
        VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '쿠폰 종류 상태(ACTIVE, INACTIVE)',
    ADD CONSTRAINT `chk_coupon_type_status`
        CHECK (`status` IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE `coupon_event_item`
    ADD CONSTRAINT `fk_coupon_event_item_coupon_type`
        FOREIGN KEY (`coupon_type_id`)
        REFERENCES `coupon_type` (`coupon_type_id`)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT;
