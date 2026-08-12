package com.clutch.lolesports.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 매치에 참가한 팀 (매치당 2행).
 *
 * 팀 마스터 테이블이 아니라 "그 경기 당시의 팀 정보 스냅샷"이다.
 * 로고·이름·전적이 시즌 중 바뀌어도 과거 경기 화면이 그대로 남아야 하기 때문이다.
 */
@Entity
@Table(name = "match_team")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_team_id")
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    /**
     * LoL Esports 팀 식별자.
     * 스코어보드의 esportsTeamId 와 매칭해 진영(블루/레드)을 판별한다. TBD 매치면 null.
     */
    @Column(name = "external_team_id", length = 32)
    private String externalTeamId;

    /** 매치 헤더 표시 순서 (1 또는 2) */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "team_code", length = 16)
    private String teamCode;

    @Column(name = "team_name", length = 100)
    private String teamName;

    @Column(name = "team_image_url", length = 2048)
    private String teamImageUrl;

    /** win / loss / null(미종료) */
    @Column(name = "outcome", length = 10)
    private String outcome;

    @Column(name = "game_wins")
    private Integer gameWins;

    @Column(name = "record_wins_snapshot")
    private Integer recordWinsSnapshot;

    @Column(name = "record_losses_snapshot")
    private Integer recordLossesSnapshot;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public MatchTeam(Long matchId, String externalTeamId, Integer displayOrder, String teamCode,
                     String teamName, String teamImageUrl, String outcome, Integer gameWins,
                     Integer recordWinsSnapshot, Integer recordLossesSnapshot) {
        this.matchId = matchId;
        this.externalTeamId = externalTeamId;
        this.displayOrder = displayOrder;
        this.teamCode = teamCode;
        this.teamName = teamName;
        this.teamImageUrl = teamImageUrl;
        this.outcome = outcome;
        this.gameWins = gameWins;
        this.recordWinsSnapshot = recordWinsSnapshot;
        this.recordLossesSnapshot = recordLossesSnapshot;
    }

    /** 경기 결과가 확정되면 승패·세트 득점을 채운다 */
    public void updateResult(String outcome, Integer gameWins) {
        this.outcome = outcome;
        this.gameWins = gameWins;
    }
}
