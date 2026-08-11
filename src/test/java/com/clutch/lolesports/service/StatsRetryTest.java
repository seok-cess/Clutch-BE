package com.clutch.lolesports.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 통계 404 게임의 재시도 정책 검증.
 *
 * 배경: 경기 시작 전에는 livestats 가 "Stats are disabled" 404 를 주다가
 * 실제 인게임이 시작되면 정상 응답으로 바뀐다 (2026-08-08 DCG vs MVK 실측).
 * 404 를 영구 제외로 처리하면 그 경기는 끝까지 갱신되지 않으므로, 반드시 재시도되어야 한다.
 */
class StatsRetryTest {

    /** PollingScheduler.isStatsOnHold 와 동일한 판정 로직 */
    private static boolean isOnHold(Map<String, Long> retryAt, String gameId, long now) {
        Long at = retryAt.get(gameId);
        return at != null && at > now;
    }

    @Test
    void 대기시간_안에는_폴링에서_제외된다() {
        Map<String, Long> retryAt = new ConcurrentHashMap<>();
        long now = 1_000_000L;
        retryAt.put("g1", now + 60_000);

        assertTrue(isOnHold(retryAt, "g1", now), "대기 중인 게임은 폴링 대상에서 빠져야 함");
    }

    @Test
    void 대기시간이_지나면_다시_폴링된다() {
        Map<String, Long> retryAt = new ConcurrentHashMap<>();
        long now = 1_000_000L;
        retryAt.put("g1", now + 60_000);

        // 60초 경과 — 경기가 시작돼 통계가 열렸을 수 있는 시점
        assertFalse(isOnHold(retryAt, "g1", now + 61_000),
                "대기 시간이 지나면 반드시 다시 시도해야 함 (영구 제외 금지)");
    }

    @Test
    void 한번도_실패하지_않은_게임은_항상_폴링된다() {
        Map<String, Long> retryAt = new ConcurrentHashMap<>();

        assertFalse(isOnHold(retryAt, "정상게임", 1_000_000L));
    }

    @Test
    void 한_게임의_대기는_다른_게임에_영향을_주지_않는다() {
        Map<String, Long> retryAt = new ConcurrentHashMap<>();
        long now = 1_000_000L;
        retryAt.put("통계없는게임", now + 60_000);

        // 동시에 진행 중인 다른 경기는 계속 폴링되어야 한다
        assertFalse(isOnHold(retryAt, "정상경기", now),
                "한 경기의 404 가 다른 경기 폴링을 막으면 안 됨");
    }
}
