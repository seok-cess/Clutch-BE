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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 세트별 선수 최종 기록 (세트당 10행).
 * 스코어보드 선수 표와 선수 상세 표가 이 값을 쓴다.
 */
@Entity
@Table(name = "game_player_stat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GamePlayerStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_player_stat_id")
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "match_team_id")
    private Long matchTeamId;

    /** 세트 내부 참가 번호 1~10 (1~5 블루, 6~10 레드) */
    @Column(name = "participant_no", nullable = false)
    private Integer participantNo;

    /** blue / red */
    @Column(name = "side", nullable = false, length = 4)
    private String side;

    @Column(name = "external_player_id", length = 32)
    private String externalPlayerId;

    @Column(name = "summoner_name", length = 100)
    private String summonerName;

    @Column(name = "champion_id", length = 50)
    private String championId;

    @Column(name = "role", length = 20)
    private String role;

    @Column(name = "level")
    private Integer level;

    @Column(name = "kills")
    private Integer kills;

    @Column(name = "deaths")
    private Integer deaths;

    @Column(name = "assists")
    private Integer assists;

    @Column(name = "creep_score")
    private Integer creepScore;

    /** Window 스코어보드 기준 총 골드 */
    @Column(name = "total_gold")
    private Long totalGold;

    /** Details 기준 획득 골드 */
    @Column(name = "total_gold_earned")
    private Long totalGoldEarned;

    @Column(name = "kill_participation_ratio", precision = 7, scale = 6)
    private BigDecimal killParticipationRatio;

    @Column(name = "champion_damage_share_ratio", precision = 7, scale = 6)
    private BigDecimal championDamageShareRatio;

    @Column(name = "wards_placed")
    private Integer wardsPlaced;

    @Column(name = "wards_destroyed")
    private Integer wardsDestroyed;

    /** 아이템 ID 배열 — 이름·아이콘 매핑은 프론트의 Data Dragon 이 담당한다 */
    @Column(name = "items_json", columnDefinition = "json")
    private String itemsJson;

    /** {"perks":[...]} 형태 객체 (CHECK 제약이 OBJECT 를 요구한다) */
    @Column(name = "perks_json", columnDefinition = "json")
    private String perksJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public GamePlayerStat(Long gameId, Long matchTeamId, Integer participantNo, String side,
                          String externalPlayerId, String summonerName, String championId, String role,
                          Integer level, Integer kills, Integer deaths, Integer assists,
                          Integer creepScore, Long totalGold, Long totalGoldEarned,
                          BigDecimal killParticipationRatio, BigDecimal championDamageShareRatio,
                          Integer wardsPlaced, Integer wardsDestroyed,
                          String itemsJson, String perksJson) {
        this.gameId = gameId;
        this.matchTeamId = matchTeamId;
        this.participantNo = participantNo;
        this.side = side;
        this.externalPlayerId = externalPlayerId;
        this.summonerName = summonerName;
        this.championId = championId;
        this.role = role;
        this.level = level;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.creepScore = creepScore;
        this.totalGold = totalGold;
        this.totalGoldEarned = totalGoldEarned;
        this.killParticipationRatio = killParticipationRatio;
        this.championDamageShareRatio = championDamageShareRatio;
        this.wardsPlaced = wardsPlaced;
        this.wardsDestroyed = wardsDestroyed;
        this.itemsJson = itemsJson;
        this.perksJson = perksJson;
    }
}
