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
     * 증감값을 사용자 포인트에 반영한다. 양수는 지급, 음수는 차감, 0은 변경 없음이다.
     */
    public void changePoint(long pointDelta) {
        this.point = Math.addExact(this.point, pointDelta);
    }
}
