package com.clutch.lolesports.service;

import com.clutch.lolesports.dto.external.WindowResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 프레임 버퍼 동작 검증.
 * 실제 피드 특성(10초 응답에 초 단위 프레임 수십 개, 응답 간 중복)을 재현한다.
 */
class DataCacheServiceTest {

    private static final String GAME = "g1";
    private static final Instant T0 = Instant.parse("2026-08-07T08:20:00Z");

    /** t0+offsetMillis 시점, 골드 = gold 인 프레임 */
    private static WindowResponse.Frame frame(long offsetMillis, long gold) {
        WindowResponse.TeamFrame blue = new WindowResponse.TeamFrame(
                gold, 0, 0, 0, 0, List.of(), List.of());
        return new WindowResponse.Frame(
                T0.plusMillis(offsetMillis).toString(), "in_game", blue, blue);
    }

    @Test
    void 응답의_모든_프레임을_버퍼에_적재한다() {
        DataCacheService cache = new DataCacheService();

        List<WindowResponse.Frame> added = cache.addWindowFrames(GAME, null,
                List.of(frame(0, 100), frame(1000, 110), frame(2000, 120)));

        assertEquals(3, added.size(), "3개 프레임이 모두 새로 적재되어야 함");
    }

    @Test
    void 중복_프레임은_무시된다() {
        DataCacheService cache = new DataCacheService();
        cache.addWindowFrames(GAME, null, List.of(frame(0, 100), frame(1000, 110)));

        // 다음 폴링에서 겹치는 프레임 + 새 프레임이 함께 온 상황
        List<WindowResponse.Frame> added = cache.addWindowFrames(GAME, null,
                List.of(frame(1000, 110), frame(2000, 120)));

        assertEquals(1, added.size(), "겹치는 프레임은 제외되고 새 프레임만 반환되어야 함");
        assertEquals(T0.plusMillis(2000).toString(), added.get(0).rfc460Timestamp());
    }

    @Test
    void 재생시점마다_다른_프레임이_반환된다() {
        DataCacheService cache = new DataCacheService();
        // 1초 간격 10개 프레임 (실제 피드처럼 값이 계속 증가)
        for (int i = 0; i < 10; i++) {
            cache.addWindowFrames(GAME, null, List.of(frame(i * 1000L, 100 + i * 10L)));
        }

        // 재생 시점을 1초씩 전진시키면 골드도 단계적으로 전진해야 함
        assertEquals(100L, cache.getWindowFrameAt(GAME, T0).blueTeam().totalGold());
        assertEquals(110L, cache.getWindowFrameAt(GAME, T0.plusSeconds(1)).blueTeam().totalGold());
        assertEquals(120L, cache.getWindowFrameAt(GAME, T0.plusSeconds(2)).blueTeam().totalGold());
        assertEquals(190L, cache.getWindowFrameAt(GAME, T0.plusSeconds(9)).blueTeam().totalGold());
    }

    @Test
    void 프레임_사이_시점은_직전_프레임을_쓴다() {
        DataCacheService cache = new DataCacheService();
        cache.addWindowFrames(GAME, null, List.of(frame(0, 100), frame(1000, 110)));

        // 0.5초 시점 → 0초 프레임 (미래 값을 앞당겨 보여주지 않음)
        assertEquals(100L, cache.getWindowFrameAt(GAME, T0.plusMillis(500)).blueTeam().totalGold());
    }

    @Test
    void 최신프레임_조회는_가장_새_프레임을_반환한다() {
        DataCacheService cache = new DataCacheService();
        cache.addWindowFrames(GAME, null, List.of(frame(0, 100), frame(5000, 150)));

        assertEquals(150L, cache.getNewestWindowFrame(GAME).blueTeam().totalGold());
    }

    @Test
    void 실제_인게임과_일시정지_프레임에서만_세트_진행으로_판정한다() {
        DataCacheService cache = new DataCacheService();

        assertEquals(false, cache.isGameInProgress(GAME));
        cache.addWindowFrames(GAME, null, List.of(frame(0, 100)));
        assertEquals(true, cache.isGameInProgress(GAME));
        cache.addWindowFrames(GAME, null, List.of(new WindowResponse.Frame(
                T0.plusSeconds(1).toString(), "paused", null, null)));
        assertEquals(true, cache.isGameInProgress(GAME));
        cache.addWindowFrames(GAME, null, List.of(new WindowResponse.Frame(
                T0.plusSeconds(2).toString(), "finished", null, null)));
        assertEquals(false, cache.isGameInProgress(GAME));
    }

    @Test
    void 버퍼가_비면_null() {
        DataCacheService cache = new DataCacheService();

        assertNull(cache.getWindowFrameAt(GAME, T0));
        assertNull(cache.getNewestWindowFrame(GAME));
    }

    @Test
    void 재생시점이_버퍼보다_과거면_가장_오래된_프레임() {
        DataCacheService cache = new DataCacheService();
        cache.addWindowFrames(GAME, null, List.of(frame(5000, 150)));

        // 워밍업 직후 재생 시점이 버퍼 시작보다 앞선 경우에도 화면이 비지 않아야 함
        assertNotNull(cache.getWindowFrameAt(GAME, T0));
        assertEquals(150L, cache.getWindowFrameAt(GAME, T0).blueTeam().totalGold());
    }

    @Test
    void 최초_finished_프레임_시각을_버퍼_해제_후에도_보존한다() {
        DataCacheService cache = new DataCacheService();
        WindowResponse.Frame finished = new WindowResponse.Frame(
                T0.plusSeconds(10).toString(),
                "finished",
                null,
                null
        );

        cache.addWindowFrames(GAME, null, List.of(finished));
        cache.evictGame(GAME);

        assertEquals(T0.plusSeconds(10), cache.getFeedFinishedAt(GAME));
    }

    @Test
    void 라이브_화면과_배팅_후보_캐시는_분리한다() {
        DataCacheService cache = new DataCacheService();
        DataCacheService.LiveMatch upcoming = new DataCacheService.LiveMatch(
                "match-1",
                "1주 차",
                "LCK",
                T0.plusSeconds(60).toString(),
                3,
                List.of(),
                List.of(),
                null
        );

        cache.putBettingMatches(List.of(upcoming));

        assertEquals(List.of(), cache.getLiveMatches());
        assertEquals(List.of(upcoming), cache.getBettingMatches());
    }
}
