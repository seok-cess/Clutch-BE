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

    public boolean hasParticipant(String externalTeamId) {
        return firstExternalTeamId.equals(externalTeamId)
                || secondExternalTeamId.equals(externalTeamId);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
