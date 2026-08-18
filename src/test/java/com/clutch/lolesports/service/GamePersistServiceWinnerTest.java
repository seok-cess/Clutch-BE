package com.clutch.lolesports.service;

import com.clutch.lolesports.config.LolesportsProperties;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.entity.EsportsGame;
import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.entity.MatchTeam;
import com.clutch.lolesports.repository.EsportsGameRepository;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.lolesports.repository.GamePlayerStatRepository;
import com.clutch.lolesports.repository.GameTimelinePointRepository;
import com.clutch.lolesports.repository.MatchTeamRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GamePersistServiceWinnerTest {

    private final DataCacheService cache = mock(DataCacheService.class);
    private final EsportsMatchRepository matchRepository = mock(EsportsMatchRepository.class);
    private final MatchTeamRepository matchTeamRepository = mock(MatchTeamRepository.class);
    private final EsportsGameRepository gameRepository = mock(EsportsGameRepository.class);
    private final GamePlayerStatRepository playerStatRepository = mock(GamePlayerStatRepository.class);
    private final GameTimelinePointRepository timelineRepository = mock(GameTimelinePointRepository.class);
    private final LolesportsProperties properties = mock(LolesportsProperties.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final SetWinnerTracker winnerTracker = mock(SetWinnerTracker.class);
    private final GamePersistService service = new GamePersistService(
            cache,
            matchRepository,
            matchTeamRepository,
            gameRepository,
            playerStatRepository,
            timelineRepository,
            properties,
            objectMapper,
            winnerTracker
    );

    @Test
    void persistsWinnerDecidedAfterFinishedGameWasAlreadyStored() {
        EsportsMatch match = mock(EsportsMatch.class);
        MatchTeam firstTeam = team("team-a", 101L);
        MatchTeam secondTeam = team("team-b", 102L);
        EsportsGame game = mock(EsportsGame.class);
        given(match.getId()).willReturn(10L);
        given(matchRepository.findByExternalMatchId("match-1")).willReturn(Optional.of(match));
        given(matchTeamRepository.findByMatchIdOrderByDisplayOrderAsc(10L))
                .willReturn(List.of(firstTeam, secondTeam));
        given(gameRepository.findByExternalGameId("game-1")).willReturn(Optional.of(game));
        given(game.getExternalGameId()).willReturn("game-1");
        given(winnerTracker.winnerOf("match-1", "game-1")).willReturn("team-a");

        service.persistLiveMatch(liveMatch());

        verify(game).decideWinner(eq(101L), any(LocalDateTime.class));
    }

    private MatchTeam team(String externalTeamId, Long id) {
        MatchTeam team = mock(MatchTeam.class);
        given(team.getExternalTeamId()).willReturn(externalTeamId);
        given(team.getId()).willReturn(id);
        return team;
    }

    private DataCacheService.LiveMatch liveMatch() {
        return new DataCacheService.LiveMatch(
                "match-1",
                "결승",
                "LCK",
                "2026-08-18T08:00:00Z",
                3,
                List.of(
                        scheduleTeam("team-a", 1),
                        scheduleTeam("team-b", 0)
                ),
                List.of(new EventDetailsResponse.Game(
                        "game-1",
                        1,
                        "completed",
                        List.of()
                )),
                null
        );
    }

    private ScheduleResponse.Team scheduleTeam(String id, int gameWins) {
        return new ScheduleResponse.Team(
                id,
                id,
                id,
                null,
                new ScheduleResponse.Result(null, gameWins),
                null
        );
    }
}
