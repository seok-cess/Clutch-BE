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
 * 매치(다전제). 세트는 {@link EsportsGame} 이 갖는다.
 */
@Entity
@Table(name = "esports_match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EsportsMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "esports_match_id")
    private Long id;

    /** LoL Esports matchId — 외부 식별자이자 중복 적재 방지 키 */
    @Column(name = "external_match_id", nullable = false, length = 32)
    private String externalMatchId;

    @Column(name = "league_external_id", nullable = false, length = 32)
    private String leagueExternalId;

    /** H2H 집계 시즌 키 (예: "2026") */
    @Column(name = "season_key", nullable = false, length = 50)
    private String seasonKey;

    @Column(name = "tournament_external_id", length = 32)
    private String tournamentExternalId;

    @Column(name = "block_name", length = 100)
    private String blockName;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** unstarted / inProgress / completed */
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private String lifecycleStatus;

    @Column(name = "best_of")
    private Integer bestOf;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public EsportsMatch(String externalMatchId, String leagueExternalId,
                        String seasonKey, String tournamentExternalId,
                        String blockName, LocalDateTime scheduledAt, LocalDateTime startedAt,
                        String lifecycleStatus, Integer bestOf) {
        this.externalMatchId = externalMatchId;
        this.leagueExternalId = leagueExternalId;
        this.seasonKey = seasonKey;
        this.tournamentExternalId = tournamentExternalId;
        this.blockName = blockName;
        this.scheduledAt = scheduledAt;
        this.startedAt = startedAt;
        this.lifecycleStatus = lifecycleStatus;
        this.bestOf = bestOf;
    }

    /** 재적재 시 변할 수 있는 값만 갱신한다 (외부 식별자·리그는 불변) */
    public void updateProgress(String lifecycleStatus, LocalDateTime startedAt, Integer bestOf) {
        this.lifecycleStatus = lifecycleStatus;
        if (startedAt != null) {
            this.startedAt = startedAt;
        }
        if (bestOf != null) {
            this.bestOf = bestOf;
        }
    }
}
