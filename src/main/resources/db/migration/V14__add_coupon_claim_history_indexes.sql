-- 관리자 쿠폰 발급 내역의 이벤트·사용자·기간 필터와
-- ID 커서 기반 최신순 조회를 지원한다.
ALTER TABLE `coupon_claim_request`
    ADD KEY `idx_claim_request_event_cursor`
        (`coupon_event_id`, `coupon_claim_request_id`),
    ADD KEY `idx_claim_request_user_cursor`
        (`user_id`, `coupon_claim_request_id`),
    ADD KEY `idx_claim_request_created_cursor`
        (`created_at`, `coupon_claim_request_id`);
