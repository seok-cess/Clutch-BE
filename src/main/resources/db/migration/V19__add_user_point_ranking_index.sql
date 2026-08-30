-- 보유 포인트가 더 높은 일반 사용자 수를 빠르게 세어 개인 순위를 계산한다.
ALTER TABLE `user`
    ADD KEY `idx_user_role_point` (`role`, `point`);
