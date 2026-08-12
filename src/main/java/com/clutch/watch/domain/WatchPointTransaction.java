package com.clutch.watch.domain;

import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
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
        if (userId == null) {
            throw new WatchException(WatchError.USER_ID_REQUIRED);
        }
        if (watchSessionId == null) {
            throw new WatchException(WatchError.WATCH_SESSION_ID_REQUIRED);
        }
        if (esportsMatchId == null) {
            throw new WatchException(WatchError.MATCH_ID_REQUIRED);
        }
        if (awardedPoint < 0) {
            throw new WatchException(WatchError.AWARDED_POINT_NEGATIVE);
        }
        this.userId = userId;
        this.watchSessionId = watchSessionId;
        this.esportsMatchId = esportsMatchId;
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
