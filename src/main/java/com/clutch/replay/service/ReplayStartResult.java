package com.clutch.replay.service;

import java.util.List;

/** 새 replay 실행에 할당된 외부 경기·세트 ID 목록. */
public record ReplayStartResult(
        String runId,
        List<ReplayMatchResult> matches
) {

    public ReplayStartResult {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
