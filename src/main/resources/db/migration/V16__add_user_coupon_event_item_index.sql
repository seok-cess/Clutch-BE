-- 쿠폰 성공 수량 GROUP BY 집계가 user_coupon 본문 대신 커버링 인덱스를 사용하도록 한다.
ALTER TABLE `user_coupon`
    ADD KEY `idx_user_coupon_event_item` (`coupon_event_item_id`);
