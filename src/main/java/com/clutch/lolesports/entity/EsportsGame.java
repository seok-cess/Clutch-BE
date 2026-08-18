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
import java.time.ZoneOffset;

/**
 * 세트(게임) 하나 + 팀 최종 총계.
 *
 * 스코어보드 상단(타워·억제기·바론·용 아이콘, 골드바)이 이 최종 총계를 쓴다.
 * 진행 중 프레임은 캐시에만 있고, 여기에는 종료 시점 값만 적재한다.
 */
@Entity
@Table(name = "esports_game")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EsportsGame {

    /** 수집 상태 값 — CHECK 제약과 일치해야 한다 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_COMPLETE = "COMPLETE";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "esports_game_id")
    private Long id;

    /** LoL Esports gameId — 중복 적재 방지 키 */
    @Column(name = "external_game_id", nullable = false, length = 32)
    private String externalGameId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "game_number", nullable = false)
    private Integer gameNumber;

    @Column(name = "blue_match_team_id")
    private Long blueMatchTeamId;

    @Column(name = "red_match_team_id")
    private Long redMatchTeamId;

    /**
     * 세트 승리 팀.
     *
     * 소스가 세트 승자를 주지 않아 매치의 gameWins 증가분으로 판정한다.
     * 세트 종료보다 약 5분 늦게 확정되므로, 그전까지는 null 이다.
     */
    @Column(name = "winner_match_team_id")
    private Long winnerMatchTeamId;

    /** 승자 확정 시각. null 이면 아직 판정되지 않았다 (정산은 이 값이 있을 때만 신뢰) */
    @Column(name = "winner_decided_at")
    private LocalDateTime winnerDecidedAt;

    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private String lifecycleStatus;

    /** 마지막 Window frame 의 gameState 원본 */
    @Column(name = "telemetry_state", length = 30)
    private String telemetryState;

    @Column(name = "patch_version", length = 50)
    private String patchVersion;

    /** 게임 시작 시각 — 경과 시간 계산 기준 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "final_window_frame_at")
    private LocalDateTime finalWindowFrameAt;

    @Column(name = "final_details_frame_at")
    private LocalDateTime finalDetailsFrameAt;

    // ---- 팀 최종 총계 (스코어보드 상단) ----

    @Column(name = "blue_total_gold")
    private Long blueTotalGold;

    @Column(name = "red_total_gold")
    private Long redTotalGold;

    @Column(name = "blue_total_kills")
    private Integer blueTotalKills;

    @Column(name = "red_total_kills")
    private Integer redTotalKills;

    @Column(name = "blue_towers")
    private Integer blueTowers;

    @Column(name = "red_towers")
    private Integer redTowers;

    @Column(name = "blue_inhibitors")
    private Integer blueInhibitors;

    @Column(name = "red_inhibitors")
    private Integer redInhibitors;

    @Column(name = "blue_barons")
    private Integer blueBarons;

    @Column(name = "red_barons")
    private Integer redBarons;

    /**
     * 그래프 마커·용 툴팁용 오브젝트 이벤트 배열.
     * [{"gameTimeSeconds":612,"side":"blue","type":"dragon","subtype":"ocean"}, ...]
     */
    @Column(name = "objectives_json", columnDefinition = "json")
    private String objectivesJson;

    // ---- 수집 상태 (재시도·검증용) ----

    @Column(name = "window_collection_status", nullable = false, length = 20)
    private String windowCollectionStatus = STATUS_PENDING;

    @Column(name = "details_collection_status", nullable = false, length = 20)
    private String detailsCollectionStatus = STATUS_PENDING;

    @Column(name = "timeline_collection_status", nullable = false, length = 20)
    private String timelineCollectionStatus = STATUS_PENDING;

    @Column(name = "timeline_covered_from_seconds")
    private Integer timelineCoveredFromSeconds;

    @Column(name = "timeline_covered_to_seconds")
    private Integer timelineCoveredToSeconds;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "last_collection_error", length = 1000)
    private String lastCollectionError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public EsportsGame(String externalGameId, Long matchId, Integer gameNumber,
                       String lifecycleStatus) {
        this.externalGameId = externalGameId;
        this.matchId = matchId;
        this.gameNumber = gameNumber;
        this.lifecycleStatus = lifecycleStatus;
    }

    public void assignSides(Long blueMatchTeamId, Long redMatchTeamId) {
        this.blueMatchTeamId = blueMatchTeamId;
        this.redMatchTeamId = redMatchTeamId;
    }

    /**
     * 세트 승자를 확정한다. 이 세트에 참여하지 않은 팀이면 무시한다
     * (DB CHECK 제약과 같은 조건 — 진영 매핑이 없으면 저장하지 않는다).
     *
     * @return 실제로 확정했으면 true
     */
    public boolean decideWinner(Long winnerMatchTeamId, LocalDateTime decidedAt) {
        if (winnerMatchTeamId == null || decidedAt == null) {
            return false;
        }
        boolean participated = winnerMatchTeamId.equals(blueMatchTeamId)
                || winnerMatchTeamId.equals(redMatchTeamId);
        if (!participated) {
            return false;
        }
        this.winnerMatchTeamId = winnerMatchTeamId;
        this.winnerDecidedAt = decidedAt;
        return true;
    }

    public boolean isWinnerDecided() {
        return winnerMatchTeamId != null;
    }

    public void updateMeta(String telemetryState, String patchVersion,
                           LocalDateTime startedAt, LocalDateTime endedAt,
                           Integer durationSeconds,
                           LocalDateTime finalWindowFrameAt, LocalDateTime finalDetailsFrameAt) {
        this.telemetryState = telemetryState;
        this.patchVersion = patchVersion;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.finalWindowFrameAt = finalWindowFrameAt;
        this.finalDetailsFrameAt = finalDetailsFrameAt;
    }

    public void updateBlueTotals(Long gold, Integer kills, Integer towers,
                                 Integer inhibitors, Integer barons) {
        this.blueTotalGold = gold;
        this.blueTotalKills = kills;
        this.blueTowers = towers;
        this.blueInhibitors = inhibitors;
        this.blueBarons = barons;
    }

    public void updateRedTotals(Long gold, Integer kills, Integer towers,
                                Integer inhibitors, Integer barons) {
        this.redTotalGold = gold;
        this.redTotalKills = kills;
        this.redTowers = towers;
        this.redInhibitors = inhibitors;
        this.redBarons = barons;
    }

    public void updateObjectives(String objectivesJson) {
        this.objectivesJson = objectivesJson;
    }

    public void markLifecycle(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    /** 적재 성공 — 이 시점 이후에만 캐시를 지워도 안전하다 */
    public void markCollected(String windowStatus, String detailsStatus, String timelineStatus,
                              Integer coveredFrom, Integer coveredTo) {
        this.windowCollectionStatus = windowStatus;
        this.detailsCollectionStatus = detailsStatus;
        this.timelineCollectionStatus = timelineStatus;
        this.timelineCoveredFromSeconds = coveredFrom;
        this.timelineCoveredToSeconds = coveredTo;
        this.lastCollectionError = null;
        this.finalizedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** 적재 실패 — 나중에 배치로 재시도할 수 있게 사유를 남긴다 */
    public void markFailed(String error) {
        this.lastCollectionError = error != null && error.length() > 1000
                ? error.substring(0, 1000) : error;
    }

    public boolean isFinalized() {
        return finalizedAt != null;
    }
}
