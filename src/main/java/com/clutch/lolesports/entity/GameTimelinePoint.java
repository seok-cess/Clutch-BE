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

/**
 * 골드 추이 그래프용 시계열 지점.
 *
 * 세트당 수백 행이 쌓이는 유일한 테이블이라 컬럼을 최소로 유지한다 (V4).
 * 골드차는 저장하지 않고 blue_gold - red_gold 로 계산한다.
 */
@Entity
@Table(name = "game_timeline_point")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameTimelinePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_timeline_point_id")
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    /** 게임 시작 기준 경과 초. (game_id, game_time_seconds) 가 유니크 */
    @Column(name = "game_time_seconds", nullable = false)
    private Integer gameTimeSeconds;

    @Column(name = "blue_gold", nullable = false)
    private Long blueGold;

    @Column(name = "red_gold", nullable = false)
    private Long redGold;

    @Column(name = "blue_kills", nullable = false)
    private Integer blueKills;

    @Column(name = "red_kills", nullable = false)
    private Integer redKills;

    public GameTimelinePoint(Long gameId, Integer gameTimeSeconds, Long blueGold, Long redGold,
                             Integer blueKills, Integer redKills) {
        this.gameId = gameId;
        this.gameTimeSeconds = gameTimeSeconds;
        this.blueGold = blueGold;
        this.redGold = redGold;
        this.blueKills = blueKills;
        this.redKills = redKills;
    }

    public long goldDiff() {
        return blueGold - redGold;
    }
}
