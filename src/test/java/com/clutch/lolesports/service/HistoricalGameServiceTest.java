package com.clutch.lolesports.service;

import com.clutch.lolesports.client.LiveStatsClient;
import com.clutch.lolesports.client.LolesportsApiClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HistoricalGameServiceTest {

    @Test
    void 활성_게임은_과거_경기_로더가_별도로_조회하지_않는다() {
        LolesportsApiClient api = mock(LolesportsApiClient.class);
        LiveStatsClient liveStats = mock(LiveStatsClient.class);
        DataCacheService cache = new DataCacheService();
        cache.putLiveMatches(List.of(new DataCacheService.LiveMatch(
                "match-1",
                "1주 차",
                "LCK",
                "2026-08-19T08:00:00Z",
                3,
                List.of(),
                List.of(),
                "game-1"
        )));
        HistoricalGameService service = new HistoricalGameService(api, liveStats, cache);

        service.ensureGameLoaded("game-1");

        verifyNoInteractions(api, liveStats);
    }
}
