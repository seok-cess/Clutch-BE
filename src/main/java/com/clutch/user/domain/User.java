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

    private User(
            UserRole role,
            String email,
            String name,
            String phoneNumber
    ) {
        this.role = Objects.requireNonNull(role, "사용자 권한은 필수입니다.");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        this.email = email;
        this.name = normalizeName(name);
        this.phoneNumber = normalizePhoneNumber(phoneNumber);
        this.point = 0L;
    }

    public static User create(UserRole role, String email) {
        return new User(role, email, null, null);
    }

    /**
     * 이름과 전화번호를 포함한 가상 사용자를 생성한다.
     *
     * @param role 사용자 권한
     * @param email 이메일
     * @param name 이름
     * @param phoneNumber 전화번호
     * @return 생성된 사용자
     */
    public static User create(
            UserRole role,
            String email,
            String name,
            String phoneNumber
    ) {
        return new User(role, email, name, phoneNumber);
    }

    /**
     * 증감값을 사용자 포인트에 반영한다. 양수는 지급, 음수는 차감, 0은 변경 없음이다.
     */
    public void changePoint(long pointDelta) {
        this.point = Math.addExact(this.point, pointDelta);
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("이름은 50자 이하여야 합니다.");
        }
        return normalized;
    }

    private static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String normalized = phoneNumber.replaceAll("[^0-9]", "");
        if (normalized.length() < 10 || normalized.length() > 15) {
            throw new IllegalArgumentException(
                    "전화번호는 숫자 10자 이상 15자 이하여야 합니다."
            );
        }
        return normalized;
    }
}
