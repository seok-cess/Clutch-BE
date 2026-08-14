package com.clutch.lolesports.service;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 세트 종료 시 저장되는 매치 상태 검증.
 *
 * 예전에는 MatchContext.of 가 상태를 "completed" 로 고정해, Bo3 의 1세트만 끝나도
 * 매치 전체가 종료로 저장됐다. 그 값을 시청 세션 입장 검사가 읽고 있어 2·3세트
 * 입장이 막혔다. 매치 종료는 최종 승리 팀이 정해진 시점이어야 한다.
 */
class MatchStateTest {

    private static ScheduleResponse.Team team(String id, String code, Integer gameWins) {
        return new ScheduleResponse.Team(id, code, code, null,
                new ScheduleResponse.Result(null, gameWins), null);
    }

    /** 세트 하나가 끝난 상황을 만든다 (적재는 항상 세트 단위로 일어난다) */
    private static String stateAfterSet(Integer bestOf, Integer winsA, Integer winsB) {
        DataCacheService.LiveMatch match = new DataCacheService.LiveMatch(
                "m1", "1주 차", "LCK", "2026-08-13T08:00:00Z",
                List.of(team("tA", "AAA", winsA), team("tB", "BBB", winsB)),
                List.of(new EventDetailsResponse.Game("g1", 1, "completed", List.of())),
                bestOf,
                null);
        return GamePersistService.MatchContext.of(match, "g1", bestOf).state();
    }

    @Test
    void Bo3_1세트만_끝나면_매치는_진행중이다() {
        assertEquals("inProgress", stateAfterSet(3, 1, 0));
    }

    @Test
    void Bo3_과반을_넘기면_매치가_종료된다() {
        assertEquals("completed", stateAfterSet(3, 2, 0));
        assertEquals("completed", stateAfterSet(3, 2, 1));
    }

    @Test
    void Bo5_는_3세트를_가져가야_종료된다() {
        assertEquals("inProgress", stateAfterSet(5, 2, 1));
        assertEquals("completed", stateAfterSet(5, 3, 2));
    }

    @Test
    void Bo1_은_한_세트로_종료된다() {
        assertEquals("completed", stateAfterSet(1, 1, 0));
    }

    /**
     * gameWins 는 세트 종료보다 약 5분 늦게 오른다 (2026-08-13 실측).
     * 아직 오르지 않은 시점에 적재되면 매치를 종료로 판정하면 안 된다.
     */
    @Test
    void gameWins_가_아직_반영되지_않으면_진행중으로_둔다() {
        assertEquals("inProgress", stateAfterSet(3, 0, 0));
        assertEquals("inProgress", stateAfterSet(3, null, null));
    }

    /** bestOf 를 일정에서 찾지 못하는 경우가 있다 — 단판으로 보수적으로 다룬다 */
    @Test
    void bestOf_를_모르면_단판으로_취급한다() {
        assertEquals("completed", stateAfterSet(null, 1, 0));
        assertEquals("inProgress", stateAfterSet(null, 0, 0));
    }

    /**
     * 진영은 세트마다 바뀐다. getEventDetails 의 세트별 side 를 그대로 실어야
     * 피드 메타가 없을 때도 승자를 진영에 귀속할 수 있다.
     */
    @Test
    void 세트별_진영을_그_세트의_값으로_담는다() {
        DataCacheService.LiveMatch match = new DataCacheService.LiveMatch(
                "m1", "1주 차", "LCK", "2026-08-13T08:00:00Z",
                List.of(team("tA", "AAA", 1), team("tB", "BBB", 1)),
                List.of(
                        new EventDetailsResponse.Game("g1", 1, "completed", List.of(
                                new EventDetailsResponse.GameTeam("tA", "blue"),
                                new EventDetailsResponse.GameTeam("tB", "red"))),
                        new EventDetailsResponse.Game("g2", 2, "completed", List.of(
                                new EventDetailsResponse.GameTeam("tB", "blue"),
                                new EventDetailsResponse.GameTeam("tA", "red")))),
                3,
                null);

        GamePersistService.MatchContext g1 =
                GamePersistService.MatchContext.of(match, "g1", 3);
        assertEquals("tA", g1.blueTeamId());
        assertEquals("tB", g1.redTeamId());

        GamePersistService.MatchContext g2 =
                GamePersistService.MatchContext.of(match, "g2", 3);
        assertEquals("tB", g2.blueTeamId());
        assertEquals("tA", g2.redTeamId());
    }
}
