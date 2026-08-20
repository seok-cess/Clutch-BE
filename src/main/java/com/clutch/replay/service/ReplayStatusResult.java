package com.clutch.replay.service;

import java.util.List;

/** JSONL fixture 타임라인에서의 현재 replay 위치. */
public record ReplayStatusResult(
        String runId,
        String matchId,
        Long esportsMatchId,
        List<String> gameIds,
        long elapsedSeconds,
        long totalSeconds,
        double progressPercent,
        String fixtureTime,
        double speed
) {

    public ReplayStatusResult {
        gameIds = gameIds == null ? List.of() : List.copyOf(gameIds);
    }
}
