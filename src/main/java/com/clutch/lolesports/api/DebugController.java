package com.clutch.lolesports.api;

import com.clutch.lolesports.client.LiveStatsClient;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.PollingScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 개발/테스트용 내부 상태 확인 엔드포인트.
 * 라이브 테스트 때 폴링·캐시·백오프가 어떻게 돌고 있는지 프론트 디버그 패널에서 본다.
 * (배포 시 제거 또는 프로파일로 잠글 것 — 이번 범위 아님)
 */
@RestController
@RequestMapping("/api")
public class DebugController {

    private final DataCacheService cache;
    private final PollingScheduler scheduler;
    private final LiveStatsClient liveStats;

    public DebugController(DataCacheService cache, PollingScheduler scheduler, LiveStatsClient liveStats) {
        this.cache = cache;
        this.scheduler = scheduler;
        this.liveStats = liveStats;
    }

    @GetMapping("/debug")
    public Map<String, Object> debug() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serverTime", Instant.now().toString());
        // 서버 요구에 맞춰 자동 조정된 현재 지연 (경기마다 다름)
        out.put("liveStatsLagSeconds", liveStats.currentLagSeconds());
        out.put("backoff", scheduler.backoffStatus());
        out.putAll(cache.debugSnapshot());
        return out;
    }
}
