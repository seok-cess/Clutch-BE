package com.clutch.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 사용자 엔티티.
 */
@Getter
@Entity
@Table(name = "user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /** 회원 이름. 기존 회원과의 호환을 위해 NULL 을 허용한다 */
    @Column(name = "name", length = 50)
    private String name;

    /** 숫자만 남긴 전화번호(예: 01012345678). 전 회원에 걸쳐 고유하다 */
    @Column(name = "phone_number", length = 20, unique = true)
    private String phoneNumber;

    @Column(name = "point", nullable = false)
    private long point;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private User(UserRole role, String email) {
        this.role = Objects.requireNonNull(role, "사용자 권한은 필수입니다.");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        this.email = email;
        this.point = 0L;
    }

    public static User create(UserRole role, String email) {
        return new User(role, email);
    }

    /**
     * 개인정보를 로그에 흘리지 않기 위해 식별자만 남긴다.
     *
     * 엔티티를 그대로 로그에 넘기는 코드는 언제든 생길 수 있고, 그때 기본
     * toString 이면 이름·전화번호·이메일이 파일로 남는다. 여기서 원천 차단한다.
     */
    @Override
    public String toString() {
        return "User(id=" + id + ", role=" + role + ")";
    }

    /**
     * 증감값을 사용자 포인트에 반영한다. 양수는 지급, 음수는 차감, 0은 변경 없음이다.
     */
    public void changePoint(long pointDelta) {
        this.point = Math.addExact(this.point, pointDelta);
    }
}
