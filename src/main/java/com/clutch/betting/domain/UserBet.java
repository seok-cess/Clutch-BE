package com.clutch.betting.domain;

import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
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

/** 사용자가 특정 세트에서 선택한 팀과 배팅 금액 및 정산 상태를 관리한다. */
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

    /** 한 번에 배팅할 수 있는 최소 포인트다. */
    public static final long MIN_AMOUNT = 1_000L;
    /** 한 번에 배팅할 수 있는 최대 포인트다. */
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

    /**
     * 배팅 식별자·팀·금액을 검증하고 PLACED 상태로 초기화한다.
     *
     * @param bettingEventId 배팅 이벤트 ID
     * @param userId 사용자 ID
     * @param selectedExternalTeamId 선택한 외부 팀 ID
     * @param amount 배팅 포인트
     * @throws BettingException 필수 값이 없거나 금액이 허용 범위를 벗어날 때
     */
    private UserBet(Long bettingEventId, Long userId, String selectedExternalTeamId, long amount) {
        if (bettingEventId == null) {
            throw new BettingException(BettingErrorCode.BETTING_EVENT_ID_REQUIRED);
        }
        if (userId == null) {
            throw new BettingException(BettingErrorCode.USER_ID_REQUIRED);
        }
        if (selectedExternalTeamId == null || selectedExternalTeamId.isBlank()) {
            throw new BettingException(BettingErrorCode.SELECTED_TEAM_ID_REQUIRED);
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BettingException(BettingErrorCode.BET_AMOUNT_OUT_OF_RANGE);
        }
        this.bettingEventId = bettingEventId;
        this.userId = userId;
        this.selectedExternalTeamId = selectedExternalTeamId;
        this.amount = amount;
        this.status = UserBetStatus.PLACED;
    }

    /**
     * 검증된 사용자 배팅을 신규 등록 상태로 생성한다.
     *
     * @param bettingEventId 배팅 이벤트 ID
     * @param userId 사용자 ID
     * @param selectedExternalTeamId 선택한 외부 팀 ID
     * @param amount 배팅 포인트
     * @return PLACED 상태의 사용자 배팅
     * @throws BettingException 필수 값이 없거나 금액이 허용 범위를 벗어날 때
     */
    public static UserBet place(
            Long bettingEventId,
            Long userId,
            String selectedExternalTeamId,
            long amount
    ) {
        return new UserBet(bettingEventId, userId, selectedExternalTeamId, amount);
    }

    /** 등록 상태의 배팅을 적중 상태로 전환한다. */
    public void win() {
        transitionFromPlaced(UserBetStatus.WON);
    }

    /** 등록 상태의 배팅을 실패 상태로 전환한다. */
    public void lose() {
        transitionFromPlaced(UserBetStatus.LOST);
    }

    /** 등록 상태의 배팅을 환불 상태로 전환한다. */
    public void refund() {
        transitionFromPlaced(UserBetStatus.REFUNDED);
    }

    /**
     * 이미 처리된 결과는 멱등하게 유지하고 다른 완료 상태 간 변경은 차단한다.
     *
     * @param targetStatus 전환할 최종 배팅 상태
     * @throws BettingException 등록 상태가 아닌 배팅을 다른 결과로 바꾸려 할 때
     */
    private void transitionFromPlaced(UserBetStatus targetStatus) {
        if (status == targetStatus) {
            return;
        }
        if (status != UserBetStatus.PLACED) {
            throw new BettingException(BettingErrorCode.USER_BET_NOT_PLACED);
        }
        status = targetStatus;
    }
}
