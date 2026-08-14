package com.clutch.betting.integration.lolesports;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.SetWinnerTracker;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class LolesportsBettingCacheAdapter implements LiveBettingCache {

    private final DataCacheService dataCacheService;
    private final SetWinnerTracker setWinnerTracker;

    public LolesportsBettingCacheAdapter(
            DataCacheService dataCacheService,
            SetWinnerTracker setWinnerTracker
    ) {
        this.dataCacheService = dataCacheService;
        this.setWinnerTracker = setWinnerTracker;
    }

    @Override
    public List<LiveMatchSnapshot> findLiveMatches() {
        return dataCacheService.getLiveMatches().stream()
                .map(this::toSnapshot)
                .toList();
    }

    private LiveMatchSnapshot toSnapshot(DataCacheService.LiveMatch liveMatch) {
        List<String> teamIds = liveMatch.teams() == null
                ? List.of()
                : liveMatch.teams().stream()
                        .map(team -> team.id())
                        .filter(id -> id != null && !id.isBlank())
                        .distinct()
                        .toList();

        List<SetSnapshot> sets = new ArrayList<>();
        if (liveMatch.games() != null) {
            for (EventDetailsResponse.Game game : liveMatch.games()) {
                if (game.id() == null || game.id().isBlank() || game.number() == null) {
                    continue;
                }
                LocalDateTime startedAt = dataCacheService.getGameStart(game.id()) == null
                        ? null
                        : LocalDateTime.ofInstant(
                                dataCacheService.getGameStart(game.id()),
                                ZoneOffset.UTC
                        );
                sets.add(new SetSnapshot(
                        game.id(),
                        game.number(),
                        startedAt,
                        game.id().equals(liveMatch.activeGameId()),
                        dataCacheService.isFeedFinished(game.id())
                                || "completed".equalsIgnoreCase(game.state()),
                        setWinnerTracker.winnerOf(liveMatch.matchId(), game.id())
                ));
            }
        }
        sets.sort(Comparator.comparingInt(SetSnapshot::setNumber));
        return new LiveMatchSnapshot(liveMatch.matchId(), teamIds, List.copyOf(sets));
    }
}
