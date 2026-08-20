package com.clutch.lolesports.api;

import com.clutch.lolesports.service.BackfillService;
import com.clutch.lolesports.service.MatchOriginRepairService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 운영용 엔드포인트 — 과거 경기 백필.
 *
 * 세트 하나에 소스 요청이 수백 건 필요해 한 번 호출에 수 분이 걸린다.
 * (배포 시 인증을 걸거나 프로파일로 잠글 것 — 이번 범위 아님)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final BackfillService backfill;
    private final MatchOriginRepairService originRepair;

    public AdminController(BackfillService backfill, MatchOriginRepairService originRepair) {
        this.backfill = backfill;
        this.originRepair = originRepair;
    }

    /**
     * 완료된 과거 매치를 DB 에 적재한다 — 백그라운드로 돌고 즉시 반환한다.
     *
     * 세트 하나에 소스 요청이 수백 건 필요해(약 11초) 전체 적재는 두 시간이 넘는다.
     * 진행 상황은 {@code GET /api/admin/backfill/status} 로 확인한다.
     *
     * @param limit 처리할 매치 수 (최신순). 기본 1000 — 사실상 전체
     * @param all   이미 적재된 세트도 다시 수집할지
     */
    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill(
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(defaultValue = "false") boolean all) {

        if (!backfill.startAsync(limit, all)) {
            return ResponseEntity.status(409).body(Map.of("error", "백필이 이미 실행 중이다"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("started", true);
        out.put("limit", limit);
        out.put("statusUrl", "/api/admin/backfill/status");
        return ResponseEntity.accepted().body(out);
    }

    /**
     * 이미 적재된 매치의 리그·대회·승패를 소스와 대조해 보정한다 (일회성).
     *
     * 적재 로직은 이미 고쳤고 그전에 쌓인 행만 남은 문제라, 스키마 변경이 아니므로
     * 마이그레이션이 아닌 이 API 로 처리한다. 환경마다 적재 시점이 달라 각 DB 에서
     * 한 번씩 돌려야 한다.
     *
     * @param dryRun 기본 true — 무엇이 바뀌는지만 세고 저장하지 않는다
     */
    @PostMapping("/repair/match-origin")
    public ResponseEntity<Map<String, Object>> repairMatchOrigin(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(originRepair.repair(dryRun));
    }

    /** 백필 진행 상황 */
    @GetMapping("/backfill/status")
    public ResponseEntity<Map<String, Object>> backfillStatus() {
        return ResponseEntity.ok(backfill.progress());
    }
}
