-- 관리자 화면에서 가상 회원의 개인정보 마스킹을 확인할 수 있도록
-- 사용자 이름과 전화번호를 추가한다.
-- 기존 사용자와의 호환을 위해 우선 NULL을 허용한다.
ALTER TABLE `user`
    ADD COLUMN `name` VARCHAR(50) NULL
        COMMENT '가상 회원 이름'
        AFTER `email`,
    ADD COLUMN `phone_number` VARCHAR(20) NULL
        COMMENT '가상 회원 전화번호(숫자 정규화 값)'
        AFTER `name`,
    ADD UNIQUE KEY `uk_user_phone_number` (`phone_number`);
