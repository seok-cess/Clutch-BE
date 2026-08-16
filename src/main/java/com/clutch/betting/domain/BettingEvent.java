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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 한 매치의 특정 세트에 대한 배팅 가능 기간과 결과 상태를 관리한다. */
@Getter
@Entity
@Table(name = "betting_event")
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
     * @param closesAt 이벤트 마감 시각
     * @throws BettingException 필수 값이 없거나 세트·참가 팀 조건이 올바르지 않을 때
     */
    private BettingEvent(
            String externalMatchId,
            int setNumber,
            String firstExternalTeamId,
            String secondExternalTeamId,
            LocalDateTime openedAt,
            LocalDateTime closesAt
    ) {
        this.externalMatchId = requireText(
                externalMatchId,
                BettingErrorCode.EXTERNAL_MATCH_ID_REQUIRED
        );
        if (setNumber < 1) {
            throw new BettingException(BettingErrorCode.INVALID_SET_NUMBER);
        }
        this.setNumber = setNumber;
        this.firstExternalTeamId = requireText(
                firstExternalTeamId,
                BettingErrorCode.FIRST_TEAM_ID_REQUIRED
        );
        this.secondExternalTeamId = requireText(
                secondExternalTeamId,
                BettingErrorCode.SECOND_TEAM_ID_REQUIRED
        );
        if (this.firstExternalTeamId.equals(this.secondExternalTeamId)) {
            throw new BettingException(BettingErrorCode.DUPLICATE_TEAM_OPTIONS);
        }
        definePeriod(openedAt, closesAt);
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
     * @param closesAt 이벤트 마감 시각
     * @return OPEN 상태의 신규 배팅 이벤트
     * @throws BettingException 필수 값이 없거나 세트·참가 팀 조건이 올바르지 않을 때
     */
    public static BettingEvent open(
            String externalMatchId,
            int setNumber,
            String firstExternalTeamId,
            String secondExternalTeamId,
            LocalDateTime openedAt,
            LocalDateTime closesAt
    ) {
        return new BettingEvent(
                externalMatchId,
                setNumber,
                firstExternalTeamId,
                secondExternalTeamId,
                openedAt,
                closesAt
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
        return openedAt != null
                && closesAt != null
                && !now.isBefore(openedAt)
                && now.isBefore(closesAt);
    }

    /**
     * 실제 세트 ID를 이벤트에 연결한다.
     *
     * @param externalGameId 외부 세트 ID
     * @throws BettingException 외부 세트 ID가 없거나 다른 세트가 이미 연결됐을 때
     */
    public void attachGame(String externalGameId) {
        if (isTerminal()) {
            return;
        }
        String normalizedGameId = requireText(externalGameId, BettingErrorCode.EXTERNAL_GAME_ID_REQUIRED);
        if (this.externalGameId != null && !this.externalGameId.equals(normalizedGameId)) {
            throw new BettingException(BettingErrorCode.EVENT_GAME_ALREADY_ATTACHED);
        }
        this.externalGameId = normalizedGameId;
    }

    /**
     * 확정된 기준 시각으로 이벤트 오픈·마감 기간을 설정하거나 복구한다.
     *
     * @param openedAt 이벤트 오픈 시각
     * @param closesAt 이벤트 마감 시각
     * @throws BettingException 필수 시각이 없거나 마감이 오픈보다 늦지 않을 때
     */
    public void definePeriod(LocalDateTime openedAt, LocalDateTime closesAt) {
        if (openedAt == null) {
            throw new BettingException(BettingErrorCode.EVENT_OPENED_AT_REQUIRED);
        }
        if (closesAt == null) {
            throw new BettingException(BettingErrorCode.EVENT_CLOSES_AT_REQUIRED);
        }
        if (!closesAt.isAfter(openedAt)) {
            throw new BettingException(BettingErrorCode.INVALID_BETTING_PERIOD);
        }
        this.openedAt = openedAt;
        this.closesAt = closesAt;
    }

    /**
     * 마감 시각이 지난 OPEN 이벤트를 CLOSED 상태로 전환한다.
     *
     * @param now 판단 기준 시각
     * @return 이번 호출에서 종료 상태로 전환했으면 true
     * @throws BettingException 기준 시각이 없을 때
     */
    public boolean closeIfExpired(LocalDateTime now) {
        if (now == null) {
            throw new BettingException(BettingErrorCode.CURRENT_TIME_REQUIRED);
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
     * @throws BettingException 승리 팀 ID가 없거나 참가 팀이 아닐 때
     */
    public void recordWinner(String externalTeamId) {
        String winnerTeamId = requireText(externalTeamId, BettingErrorCode.WINNER_TEAM_ID_REQUIRED);
        if (!hasParticipant(winnerTeamId)) {
            throw new BettingException(BettingErrorCode.WINNER_NOT_PARTICIPANT);
        }
        if (isTerminal()) {
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
     * @throws BettingException 이벤트가 종료 상태가 아니거나 승자가 없을 때
     */
    public void settle() {
        if (status == BettingEventStatus.SETTLED) {
            return;
        }
        if (status != BettingEventStatus.CLOSED || winnerExternalTeamId == null) {
            throw new BettingException(BettingErrorCode.EVENT_NOT_SETTLEABLE);
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
        status = BettingEventStatus.CANCELLED;
    }

    private boolean isTerminal() {
        return status == BettingEventStatus.SETTLED
                || status == BettingEventStatus.CANCELLED;
    }

    /**
     * 필수 문자열 값을 공백까지 포함해 검증한다.
     *
     * @param value 검증할 문자열
     * @param errorCode 검증 실패 시 반환할 배팅 오류 코드
     * @return 검증을 통과한 원본 문자열
     * @throws BettingException 값이 null 또는 공백일 때
     */
    private static String requireText(String value, BettingErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            throw new BettingException(errorCode);
        }
        return value;
    }
}
