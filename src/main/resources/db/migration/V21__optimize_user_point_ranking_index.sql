-- 상위 포인트 순위의 점수 내림차순·동점 사용자 ID 오름차순 정렬을 인덱스 순서로 처리한다.
ALTER TABLE `user`
    DROP INDEX `idx_user_role_point`,
    ADD INDEX `idx_user_role_point_user_id` (`role`, `point` DESC, `user_id` ASC);
