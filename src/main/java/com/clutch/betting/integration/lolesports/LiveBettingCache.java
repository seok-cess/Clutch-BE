package com.clutch.betting.integration.lolesports;

import java.time.LocalDateTime;
import java.util.List;

public interface LiveBettingCache {

    List<LiveMatchSnapshot> findLiveMatches();

    record LiveMatchSnapshot(
            String externalMatchId,
            List<String> externalTeamIds,
            List<SetSnapshot> sets
    ) {
    }

    record SetSnapshot(
            String externalGameId,
            int setNumber,
            LocalDateTime startedAt,
            boolean active,
            boolean finished,
            String winnerExternalTeamId
    ) {
    }
}
