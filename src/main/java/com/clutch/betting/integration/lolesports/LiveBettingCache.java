package com.clutch.betting.integration.lolesports;

import java.time.LocalDateTime;
import java.util.List;

public interface LiveBettingCache {

    List<LiveMatchSnapshot> findLiveMatches();

    boolean isAcceptingBets(String externalMatchId, String externalGameId);

    record LiveMatchSnapshot(
            String externalMatchId,
            List<String> externalTeamIds,
            List<SetSnapshot> sets,
            boolean matchFinished
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
