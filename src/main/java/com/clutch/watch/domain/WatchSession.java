package com.clutch.watch.domain;

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
 * 경기 시청시간과 정산 상태를 보존하는 엔티티.
 */
@Getter
@Entity
@Table(name = "watch_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watch_session_id", nullable = false)
    private Long id;

    @Column(name = "session_key", nullable = false, unique = true, length = 36)
    private String sessionKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "esports_match_id", nullable = false)
    private Long esportsMatchId;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "eligible_milliseconds", nullable = false)
    private long eligibleMilliseconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WatchSessionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private WatchSession(
            String sessionKey,
            Long userId,
            Long esportsMatchId,
            LocalDateTime enteredAt
    ) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("세션 키는 필수입니다.");
        }
        this.sessionKey = sessionKey;
        this.userId = Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        this.esportsMatchId = Objects.requireNonNull(esportsMatchId, "경기 ID는 필수입니다.");
        this.enteredAt = Objects.requireNonNull(enteredAt, "입장 시각은 필수입니다.");
        this.lastSeenAt = enteredAt;
        this.eligibleMilliseconds = 0L;
        this.status = WatchSessionStatus.WATCHING;
    }

    public static WatchSession start(
            String sessionKey,
            Long userId,
            Long esportsMatchId,
            LocalDateTime enteredAt
    ) {
        return new WatchSession(sessionKey, userId, esportsMatchId, enteredAt);
    }

    /**
     * Redis에서 확정한 마지막 시청 상태를 반영하고 세션을 완료한다.
     */
    public void complete(LocalDateTime lastSeenAt, long eligibleMilliseconds) {
        if (status != WatchSessionStatus.WATCHING) {
            throw new IllegalStateException("이미 완료된 시청 세션입니다.");
        }
        Objects.requireNonNull(lastSeenAt, "마지막 확인 시각은 필수입니다.");
        if (lastSeenAt.isBefore(enteredAt)) {
            throw new IllegalArgumentException("마지막 확인 시각은 입장 시각보다 이전일 수 없습니다.");
        }
        if (eligibleMilliseconds < 0) {
            throw new IllegalArgumentException("유효 시청시간은 음수일 수 없습니다.");
        }
        this.lastSeenAt = lastSeenAt;
        this.eligibleMilliseconds = eligibleMilliseconds;
        this.status = WatchSessionStatus.COMPLETED;
    }
}
