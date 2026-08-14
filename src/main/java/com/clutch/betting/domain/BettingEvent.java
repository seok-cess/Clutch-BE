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

import java.time.Duration;
import java.time.LocalDateTime;

/** 한 매치의 특정 세트에 대한 배팅 가능 기간과 결과 상태를 관리한다. */
@Getter
@Entity
@Table(
        name = "betting_event",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_betting_event_match_set",
                        columnNames = {"external_match_id", "set_number"}
                ),
                @UniqueConstraint(name = "uk_betting_event_game", columnNames = "external_game_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BettingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "betting_event_id", nullable = false)
    private Long id;

    @Column(name = "external_match_id", nullable = false, length = 32)
    private String externalMatchId;

    @Column(name = "external_game_id", length = 32)
    private String externalGameId;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(name = "first_external_team_id", nullable = false, length = 32)
    private String firstExternalTeamId;

    @Column(name = "second_external_team_id", nullable = false, length = 32)
    private String secondExternalTeamId;

    @Column(name = "winner_external_team_id", length = 32)
    private String winnerExternalTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BettingEventStatus status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closes_at")
    private LocalDateTime closesAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 참가 팀과 오픈 시각을 검증하고 신규 이벤트를 OPEN 상태로 초기화한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param setNumber 세트 번호
     * @param firstExternalTeamId 첫 번째 참가 팀 ID
     * @param secondExternalTeamId 두 번째 참가 팀 ID
     * @param openedAt 이벤트 오픈 시각
     * @throws IllegalArgumentException 필수 값이 없거나 세트·참가 팀 조건이 올바르지 않을 때
     */
    private BettingEvent(
            String externalMatchId,
            int setNumber,
            String firstExternalTeamId,
            String secondExternalTeamId,
            LocalDateTime openedAt
    ) {
        this.externalMatchId = requireText(externalMatchId, "외부 매치 ID는 필수입니다.");
        if (setNumber < 1) {
            throw new IllegalArgumentException("세트 번호는 1 이상이어야 합니다.");
        }
        this.setNumber = setNumber;
        this.firstExternalTeamId = requireText(firstExternalTeamId, "첫 번째 팀 ID는 필수입니다.");
        this.secondExternalTeamId = requireText(secondExternalTeamId, "두 번째 팀 ID는 필수입니다.");
        if (this.firstExternalTeamId.equals(this.secondExternalTeamId)) {
            throw new IllegalArgumentException("배팅 선택지의 두 팀은 서로 달라야 합니다.");
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("배팅 오픈 시각은 필수입니다.");
        }
        this.openedAt = openedAt;
        this.status = BettingEventStatus.OPEN;
    }

    /**
     * 외부 매치의 특정 세트에 대한 신규 배팅 이벤트를 연다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param setNumber 세트 번호
     * @param firstExternalTeamId 첫 번째 참가 팀 ID
     * @param secondExternalTeamId 두 번째 참가 팀 ID
     * @param openedAt 이벤트 오픈 시각
     * @return OPEN 상태의 신규 배팅 이벤트
     * @throws IllegalArgumentException 필수 값이 없거나 세트·참가 팀 조건이 올바르지 않을 때
     */
    public static BettingEvent open(
            String externalMatchId,
            int setNumber,
            String firstExternalTeamId,
            String secondExternalTeamId,
            LocalDateTime openedAt
    ) {
        return new BettingEvent(
                externalMatchId,
                setNumber,
                firstExternalTeamId,
                secondExternalTeamId,
                openedAt
        );
    }

    /**
     * 주어진 외부 팀이 이 이벤트의 선택지에 포함되는지 확인한다.
     *
     * @param externalTeamId 확인할 외부 팀 ID
     * @return 두 참가 팀 중 하나이면 true
     */
    public boolean hasParticipant(String externalTeamId) {
        return firstExternalTeamId.equals(externalTeamId)
                || secondExternalTeamId.equals(externalTeamId);
    }

    /**
     * 상태와 마감 시각을 함께 확인해 현재 배팅 가능 여부를 판단한다.
     *
     * @param now 판단 기준 시각
     * @return OPEN 상태이고 마감 전이면 true
     */
    public boolean isOpenAt(LocalDateTime now) {
        if (now == null || status != BettingEventStatus.OPEN) {
            return false;
        }
        return closesAt == null || now.isBefore(closesAt);
    }

    /**
     * 실제 세트 ID를 연결하고 세트 시작 시각 기준 마감 시각을 계산한다.
     *
     * @param externalGameId 외부 세트 ID
     * @param setStartedAt 세트 시작 시각
     * @param bettingDurationAfterStart 세트 시작 후 배팅 허용 기간
     * @throws IllegalArgumentException 외부 세트 ID가 없거나 배팅 허용 기간이 양수가 아닐 때
     * @throws IllegalStateException 이미 다른 외부 세트가 연결됐을 때
     */
    public void attachGame(
            String externalGameId,
            LocalDateTime setStartedAt,
            Duration bettingDurationAfterStart
    ) {
        if (status == BettingEventStatus.SETTLED || status == BettingEventStatus.CANCELLED) {
            return;
        }
        String normalizedGameId = requireText(externalGameId, "외부 세트 ID는 필수입니다.");
        if (this.externalGameId != null && !this.externalGameId.equals(normalizedGameId)) {
            throw new IllegalStateException("이미 다른 세트 ID가 연결된 배팅 이벤트입니다.");
        }
        this.externalGameId = normalizedGameId;
        if (setStartedAt != null) {
            if (bettingDurationAfterStart == null
                    || bettingDurationAfterStart.isZero()
                    || bettingDurationAfterStart.isNegative()) {
                throw new IllegalArgumentException("세트 시작 후 배팅 가능 시간은 양수여야 합니다.");
            }
            LocalDateTime calculatedClosesAt = setStartedAt.plus(bettingDurationAfterStart);
            this.closesAt = calculatedClosesAt;
        }
    }

    /**
     * 마감 시각이 지난 OPEN 이벤트를 CLOSED 상태로 전환한다.
     *
     * @param now 판단 기준 시각
     * @return 이번 호출에서 종료 상태로 전환했으면 true
     * @throws IllegalArgumentException 기준 시각이 없을 때
     */
    public boolean closeIfExpired(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다.");
        }
        if (status == BettingEventStatus.OPEN
                && closesAt != null
                && !now.isBefore(closesAt)) {
            status = BettingEventStatus.CLOSED;
            return true;
        }
        return false;
    }

    /** 열려 있는 이벤트를 명시적으로 종료한다. */
    public void close() {
        if (status == BettingEventStatus.OPEN) {
            status = BettingEventStatus.CLOSED;
        }
    }

    /**
     * 최초로 확인한 참가 팀 승자를 보존하고 이벤트를 종료한다.
     *
     * @param externalTeamId 승리한 외부 팀 ID
     * @throws IllegalArgumentException 승리 팀 ID가 없거나 참가 팀이 아닐 때
     */
    public void recordWinner(String externalTeamId) {
        String winnerTeamId = requireText(externalTeamId, "승리 팀 ID는 필수입니다.");
        if (!hasParticipant(winnerTeamId)) {
            throw new IllegalArgumentException("승리 팀은 배팅 이벤트 참가 팀이어야 합니다.");
        }
        if (status == BettingEventStatus.SETTLED || status == BettingEventStatus.CANCELLED) {
            return;
        }
        if (winnerExternalTeamId != null) {
            return;
        }
        this.winnerExternalTeamId = winnerTeamId;
        close();
    }

    /**
     * 승자가 확정된 종료 이벤트를 최종 정산 상태로 전환한다.
     *
     * @throws IllegalStateException 이벤트가 종료 상태가 아니거나 승자가 없을 때
     */
    public void settle() {
        if (status == BettingEventStatus.SETTLED) {
            return;
        }
        if (status != BettingEventStatus.CLOSED || winnerExternalTeamId == null) {
            throw new IllegalStateException("승리 팀이 확정된 종료 이벤트만 정산할 수 있습니다.");
        }
        status = BettingEventStatus.SETTLED;
    }

    /** 승자가 없는 미정산 이벤트를 취소 상태로 전환한다. */
    public void cancel() {
        if (status == BettingEventStatus.SETTLED
                || status == BettingEventStatus.CANCELLED
                || winnerExternalTeamId != null) {
            return;
        }
        winnerExternalTeamId = null;
        status = BettingEventStatus.CANCELLED;
    }

    /**
     * 필수 문자열 값을 공백까지 포함해 검증한다.
     *
     * @param value 검증할 문자열
     * @param message 검증 실패 메시지
     * @return 검증을 통과한 원본 문자열
     * @throws IllegalArgumentException 값이 null 또는 공백일 때
     */
    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
