package com.clutch.replay.service;

import java.util.List;

/** replay 실행 중인 한 외부 경기와 해당 세트 ID 목록. */
public record ReplayMatchResult(
        String matchId,
        Long esportsMatchId,
        List<String> gameIds
) {

    public ReplayMatchResult {
        gameIds = gameIds == null ? List.of() : List.copyOf(gameIds);
    }
}
