package com.clutch.replay.service;

import java.util.List;

/** 새 replay 실행에 할당된 외부 경기·세트 ID. */
public record ReplayStartResult(
        String runId,
        String matchId,
        List<String> gameIds
) {

    public ReplayStartResult {
        gameIds = gameIds == null ? List.of() : List.copyOf(gameIds);
    }
}
