package com.clutch.betting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "user_bet",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_bet_event_user",
                columnNames = {"betting_event_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBet {

    public static final long MIN_AMOUNT = 1_000L;
    public static final long MAX_AMOUNT = 100_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_bet_id", nullable = false)
    private Long id;

    @Column(name = "betting_event_id", nullable = false)
    private Long bettingEventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "selected_external_team_id", nullable = false, length = 32)
    private String selectedExternalTeamId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserBetStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private UserBet(Long bettingEventId, Long userId, String selectedExternalTeamId, long amount) {
        if (bettingEventId == null) {
            throw new IllegalArgumentException("배팅 이벤트 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (selectedExternalTeamId == null || selectedExternalTeamId.isBlank()) {
            throw new IllegalArgumentException("선택 팀 ID는 필수입니다.");
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("배팅 금액은 1,000포인트 이상 100,000포인트 이하여야 합니다.");
        }
        this.bettingEventId = bettingEventId;
        this.userId = userId;
        this.selectedExternalTeamId = selectedExternalTeamId;
        this.amount = amount;
        this.status = UserBetStatus.PLACED;
    }

    public static UserBet place(
            Long bettingEventId,
            Long userId,
            String selectedExternalTeamId,
            long amount
    ) {
        return new UserBet(bettingEventId, userId, selectedExternalTeamId, amount);
    }

    public void win() {
        transitionFromPlaced(UserBetStatus.WON);
    }

    public void lose() {
        transitionFromPlaced(UserBetStatus.LOST);
    }

    public void refund() {
        transitionFromPlaced(UserBetStatus.REFUNDED);
    }

    private void transitionFromPlaced(UserBetStatus targetStatus) {
        if (status == targetStatus) {
            return;
        }
        if (status != UserBetStatus.PLACED) {
            throw new IllegalStateException("등록 상태의 배팅만 정산할 수 있습니다.");
        }
        status = targetStatus;
    }
}
