package com.clutch.lolesports.service;

import com.clutch.lolesports.api.ApiDtos;
import com.clutch.lolesports.repository.SeasonStatsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시즌 누적 집계의 계산 규칙 검증.
 *
 * 나눗셈 분모가 두 군데서 문제가 된다.
 *  - KDA: 데스가 0인 세트가 실제로 나온다 (분모 0)
 *  - 챔피언 승률: 세트 승자는 종료 약 5분 뒤에 확정된다. 확정 전 픽을 분모에 넣으면
 *    방금 끝난 세트가 전부 패배로 집계된다 (V8 참고)
 */
class SeasonStatsServiceTest {

    private static SeasonStatsRepository.PlayerTotals player(
            String name, String team, long k, long d, long a, long games) {
        SeasonStatsRepository.PlayerTotals t = Mockito.mock(SeasonStatsRepository.PlayerTotals.class);
        Mockito.when(t.getSummonerName()).thenReturn(name);
        Mockito.when(t.getTeamCode()).thenReturn(team);
        Mockito.when(t.getKills()).thenReturn(k);
        Mockito.when(t.getDeaths()).thenReturn(d);
        Mockito.when(t.getAssists()).thenReturn(a);
        Mockito.when(t.getGames()).thenReturn(games);
        return t;
    }

    private static SeasonStatsRepository.ChampionTotals champion(
            String id, long picks, long decided, long wins) {
        SeasonStatsRepository.ChampionTotals t = Mockito.mock(SeasonStatsRepository.ChampionTotals.class);
        Mockito.when(t.getChampionId()).thenReturn(id);
        Mockito.when(t.getPicks()).thenReturn(picks);
        Mockito.when(t.getDecidedPicks()).thenReturn(decided);
        Mockito.when(t.getWins()).thenReturn(wins);
        return t;
    }

    /*
     * 주의: player(...) / champion(...) 은 내부에서 목을 만들고 스터빙한다.
     * 이 호출을 thenReturn(...) 인자 안에 두면 바깥 스터빙이 끝나기 전에 새 스터빙이 시작돼
     * Mockito 가 UnfinishedStubbingException 을 던진다. 목록을 먼저 만든 뒤 스터빙한다.
     */

    private static SeasonStatsRepository repo() {
        SeasonStatsRepository r = Mockito.mock(SeasonStatsRepository.class);
        Mockito.when(r.latestSeasonKey()).thenReturn("2026");
        Mockito.when(r.finalizedGameCount("2026")).thenReturn(10L);
        return r;
    }

    @Test
    void 데스가_0이면_1로_나눠_KDA를_계산한다() {
        SeasonStatsRepository r = repo();
        List<SeasonStatsRepository.PlayerTotals> totals = List.of(
                player("Perfect", "RVN", 6, 0, 4, 5));
        Mockito.when(r.playerTotals("2026")).thenReturn(totals);

        ApiDtos.PlayerKdaBoard board = new SeasonStatsService(r).playerKda(null, 5);

        // (6 + 4) / max(0, 1) = 10.0 — 0 으로 나눠 무한대가 되지 않아야 한다
        assertEquals(10.0, board.players().get(0).kda());
    }

    @Test
    void KDA가_높은_순으로_정렬하고_순위를_1부터_매긴다() {
        SeasonStatsRepository r = repo();
        List<SeasonStatsRepository.PlayerTotals> totals = List.of(
                player("Low", "AZR", 4, 4, 4, 5),        // (4+4)/4 = 2.0
                player("High", "RVN", 10, 2, 10, 5));    // (10+10)/2 = 10.0
        Mockito.when(r.playerTotals("2026")).thenReturn(totals);

        List<ApiDtos.PlayerKdaRow> rows = new SeasonStatsService(r).playerKda(null, 5).players();

        assertEquals("High", rows.get(0).summonerName());
        assertEquals(1, rows.get(0).rank());
        assertEquals("Low", rows.get(1).summonerName());
        assertEquals(2, rows.get(1).rank());
    }

    @Test
    void 출전_세트가_적은_선수는_KDA_순위에서_제외한다() {
        SeasonStatsRepository r = repo();
        List<SeasonStatsRepository.PlayerTotals> totals = List.of(
                player("OneGame", "GLC", 20, 0, 20, 1), // 한 판만 뛰고 KDA 40 — 제외돼야 한다
                player("Regular", "RVN", 10, 5, 10, 8));
        Mockito.when(r.playerTotals("2026")).thenReturn(totals);

        List<ApiDtos.PlayerKdaRow> rows = new SeasonStatsService(r).playerKda(null, 5).players();

        assertEquals(1, rows.size());
        assertEquals("Regular", rows.get(0).summonerName());
    }

    @Test
    void 챔피언_승률은_승자가_확정된_픽만_분모로_쓴다() {
        SeasonStatsRepository r = repo();
        // 6번 픽됐지만 그중 4세트만 승자가 확정됐고, 그 4세트 중 3승
        List<SeasonStatsRepository.ChampionTotals> totals = List.of(
                champion("Azir", 6, 4, 3));
        Mockito.when(r.championTotals("2026")).thenReturn(totals);

        ApiDtos.ChampionRow row = new SeasonStatsService(r).champions(null, 5).champions().get(0);

        assertEquals(6, row.picks());
        assertEquals(0.6, row.pickRate().doubleValue());   // 6 / 10세트
        assertEquals(0.75, row.winRate().doubleValue());   // 3 / 4 — 6 으로 나누지 않는다
        assertEquals(4, row.decidedPicks());
    }

    @Test
    void 승자가_아직_확정되지_않았으면_승률은_비어_있다() {
        SeasonStatsRepository r = repo();
        List<SeasonStatsRepository.ChampionTotals> totals = List.of(
                champion("Poppy", 3, 0, 0));
        Mockito.when(r.championTotals("2026")).thenReturn(totals);

        ApiDtos.ChampionRow row = new SeasonStatsService(r).champions(null, 5).champions().get(0);

        assertNull(row.winRate());
        assertNull(row.wins());
        assertEquals(0.3, row.pickRate().doubleValue());
    }

    @Test
    void 적재된_시즌이_없으면_빈_목록을_돌려준다() {
        SeasonStatsRepository r = Mockito.mock(SeasonStatsRepository.class);
        Mockito.when(r.latestSeasonKey()).thenReturn(null);

        SeasonStatsService service = new SeasonStatsService(r);

        assertTrue(service.playerKda(null, 5).players().isEmpty());
        assertTrue(service.champions(null, 5).champions().isEmpty());
        assertNull(service.playerKda(null, 5).seasonKey());
        // 시즌이 없으면 집계 쿼리를 아예 실행하지 않아야 한다
        Mockito.verify(r, Mockito.never()).playerTotals(Mockito.anyString());
    }
}
