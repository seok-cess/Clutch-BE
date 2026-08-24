package com.clutch.lolesports.api;

import com.clutch.betting.service.BettingCandidateQueryService;
import com.clutch.lolesports.client.LiveStatsClient;
import com.clutch.lolesports.config.LolesportsProperties;
import com.clutch.lolesports.dto.external.WindowResponse;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.GameQueryService;
import com.clutch.lolesports.service.HistoricalGameService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.lolesports.service.SeasonStatsService;
import com.clutch.lolesports.service.SetWinnerTracker;
import com.clutch.lolesports.service.TeamRecordService;
import com.clutch.lolesports.source.ExternalSourceMode;
import com.clutch.lolesports.source.ExternalSourceState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ApiControllerReplayTimerTest {

    @Test
    void replayFrameGameTimeIsUsedInsteadOfCompressedFrameTimestamp() {
        DataCacheService cache = mock(DataCacheService.class);
        ExternalSourceState sourceState = mock(ExternalSourceState.class);
        WindowResponse.Frame frame = new WindowResponse.Frame(
                "2026-08-24T01:00:01Z",
                "in_game",
                null,
                null,
                20L
        );
        given(sourceState.mode()).willReturn(ExternalSourceMode.STUB);
        given(cache.getReplayWindowFrame(eq("game-1"), any(Instant.class))).willReturn(frame);

        ApiController controller = new ApiController(
                cache,
                mock(HistoricalGameService.class),
                mock(LolesportsProperties.class),
                mock(LiveStatsClient.class),
                mock(TeamRecordService.class),
                mock(GameQueryService.class),
                mock(SetWinnerTracker.class),
                mock(SeasonStatsService.class),
                mock(PollingScheduler.class),
                sourceState,
                mock(BettingCandidateQueryService.class)
        );

        ApiDtos.Scoreboard result = controller.scoreboard("game-1", null).getBody();

        assertThat(result).isNotNull();
        assertThat(result.gameTimeSeconds()).isEqualTo(20L);
    }

    @Test
    void historyOrdersPointsByGameTimeAndDoesNotRepeatObjectivesAfterAStaleFrame() {
        DataCacheService cache = mock(DataCacheService.class);
        ExternalSourceState sourceState = mock(ExternalSourceState.class);
        given(sourceState.mode()).willReturn(ExternalSourceMode.STUB);
        given(cache.hasWindow("game-1")).willReturn(true);

        WindowResponse.TeamFrame noObjective = team(4_000L, List.of());
        WindowResponse.TeamFrame firstDragon = team(5_100L, List.of("cloud"));
        WindowResponse.TeamFrame stale = team(5_200L, List.of());
        WindowResponse.TeamFrame restored = team(5_300L, List.of("cloud"));
        List<WindowResponse.Frame> frames = List.of(
                frame(360L, firstDragon),
                frame(300L, noObjective),
                frame(365L, stale),
                frame(370L, restored)
        );
        given(cache.getWindowSeries(eq("game-1"), any(Instant.class), anyInt()))
                .willReturn(frames);

        ApiController controller = controller(cache, sourceState);

        ApiDtos.GameHistory result = controller.history("game-1", 0L, 1).getBody();

        assertThat(result).isNotNull();
        assertThat(result.points()).extracting(ApiDtos.HistoryPoint::gameTimeSeconds)
                .containsExactly(300L, 360L, 365L, 370L);
        assertThat(result.objectives())
                .containsExactly(new ApiDtos.ObjectiveEvent(360L, "blue", "dragon", "cloud"));
    }

    private static ApiController controller(DataCacheService cache, ExternalSourceState sourceState) {
        return new ApiController(
                cache,
                mock(HistoricalGameService.class),
                mock(LolesportsProperties.class),
                mock(LiveStatsClient.class),
                mock(TeamRecordService.class),
                mock(GameQueryService.class),
                mock(SetWinnerTracker.class),
                mock(SeasonStatsService.class),
                mock(PollingScheduler.class),
                sourceState,
                mock(BettingCandidateQueryService.class)
        );
    }

    private static WindowResponse.Frame frame(long gameTimeSeconds, WindowResponse.TeamFrame blue) {
        return new WindowResponse.Frame(
                "2026-08-24T01:00:00Z",
                "in_game",
                blue,
                team(5_000L, List.of()),
                gameTimeSeconds
        );
    }

    private static WindowResponse.TeamFrame team(long gold, List<String> dragons) {
        return new WindowResponse.TeamFrame(gold, 0, 0, 0, 0, dragons, List.of());
    }
}
