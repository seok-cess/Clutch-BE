package com.clutch.watch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 시청 세션의 최종 포인트 지급 결과를 기록하는 엔티티.
 */
@Getter
@Entity
@Table(name = "watch_point_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchPointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watch_point_transaction_id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "watch_session_id", nullable = false, unique = true)
    private Long watchSessionId;

    @Column(name = "esports_match_id", nullable = false)
    private Long esportsMatchId;

    @Column(name = "awarded_point", nullable = false)
    private long awardedPoint;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private WatchPointTransaction(
            Long userId,
            Long watchSessionId,
            Long esportsMatchId,
            long awardedPoint
    ) {
        this.userId = Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        this.watchSessionId = Objects.requireNonNull(watchSessionId, "시청 세션 ID는 필수입니다.");
        this.esportsMatchId = Objects.requireNonNull(esportsMatchId, "경기 ID는 필수입니다.");
        if (awardedPoint < 0) {
            throw new IllegalArgumentException("지급 포인트는 음수일 수 없습니다.");
        }
        this.awardedPoint = awardedPoint;
    }

    public static WatchPointTransaction create(
            Long userId,
            Long watchSessionId,
            Long esportsMatchId,
            long awardedPoint
    ) {
        return new WatchPointTransaction(userId, watchSessionId, esportsMatchId, awardedPoint);
    }
}
