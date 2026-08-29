package com.clutch.replay.service;

import java.util.List;

/** JSONL fixture 타임라인에서의 현재 replay 위치와 동시 재생 경기 목록. */
public record ReplayStatusResult(
        String runId,
        List<ReplayMatchResult> matches,
        long elapsedSeconds,
        long totalSeconds,
        double progressPercent,
        String fixtureTime,
        double speed
) {

    public ReplayStatusResult {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
