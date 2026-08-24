package com.clutch.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    /**
     * 양수 증감값이 사용자의 기존 포인트에 누적되는지 검증한다.
     */
    @Test
    void increasesPoint() {
        User user = User.create(UserRole.USER, "viewer@example.com");

        user.changePoint(50L);

        assertThat(user.getPoint()).isEqualTo(50L);
    }

    /**
     * 음수 증감값으로 사용자의 기존 포인트를 차감할 수 있는지 검증한다.
     */
    @Test
    void decreasesPoint() {
        User user = User.create(UserRole.USER, "viewer@example.com");
        user.changePoint(50L);

        user.changePoint(-20L);

        assertThat(user.getPoint()).isEqualTo(30L);
    }

    /**
     * 증감값이 0이면 사용자 포인트가 바뀌지 않는지 검증한다.
     */
    @Test
    void keepsPointWhenDeltaIsZero() {
        User user = User.create(UserRole.USER, "viewer@example.com");
        user.changePoint(50L);

        user.changePoint(0L);

        assertThat(user.getPoint()).isEqualTo(50L);
    }

    /**
     * 사용자 로그 표현에 이름, 이메일과 전화번호가 포함되지 않는지 검증한다.
     */
    @Test
    void excludesPersonalDataFromToString() {
        User user = User.create(
                UserRole.USER,
                "viewer@example.com",
                "홍길동",
                "01012345678"
        );

        assertThat(user.toString())
                .isEqualTo("User(id=null, role=USER)")
                .doesNotContain("viewer@example.com", "홍길동", "01012345678");
    }
}
