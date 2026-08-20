package com.clutch.lolesports.service;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.client.LolesportsApiClient;
import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.entity.MatchTeam;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.lolesports.repository.MatchTeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 이미 적재된 매치의 리그·대회·승패를 소스 실제값으로 바로잡는다 (일회성 보정).
 *
 * 두 가지를 고친다. 둘 다 적재 로직은 이미 수정됐고, 그전에 쌓인 행만 남은 문제다.
 *
 *  1. 리그·대회 — getLive 가 전 리그를 주는데 예전 적재는 설정의 단일 리그 id 를
 *     그대로 박아, 타 리그 경기까지 LCK 로 저장됐다. 순위표에 2군·타 리그가 섞인다.
 *  2. 승패 — 소스가 outcome 을 채우지 않고 gameWins 만 주는 경우가 있어
 *     (2026-08-19 실측: LCK 25경기) 순위표 승수가 실제보다 적게 나온다.
 *
 * 스키마 변경이 아니라 데이터 보정이므로 Flyway 가 아닌 관리자 API 로 둔다.
 * 마이그레이션에 넣으면 신규 환경에서도 무의미하게 실행되고, 대회 id 가 이력에 박힌다.
 */
@Service
public class MatchOriginRepairService {

    private static final Logger log = LoggerFactory.getLogger(MatchOriginRepairService.class);

    private final EsportsMatchRepository matchRepo;
    private final MatchTeamRepository matchTeamRepo;
    private final LolesportsApiClient api;

    public MatchOriginRepairService(EsportsMatchRepository matchRepo,
                                    MatchTeamRepository matchTeamRepo,
                                    LolesportsApiClient api) {
        this.matchRepo = matchRepo;
        this.matchTeamRepo = matchTeamRepo;
        this.api = api;
    }

    /**
     * 전체 매치를 소스와 대조해 보정한다.
     *
     * @param dryRun true 면 무엇이 바뀌는지만 세고 저장하지 않는다
     */
    @Transactional
    public Map<String, Object> repair(boolean dryRun) {
        List<EsportsMatch> matches = matchRepo.findAll();
        int originFixed = 0;
        int outcomeFixed = 0;
        int failed = 0;

        for (EsportsMatch match : matches) {
            EventDetailsResponse res;
            try {
                res = api.getEventDetails(match.getExternalMatchId());
            } catch (Exception e) {
                failed++;
                continue;
            }
            if (res == null || res.data() == null || res.data().event() == null) {
                failed++;
                continue;
            }
            EventDetailsResponse.Event ev = res.data().event();

            // 1) 리그·대회
            String leagueId = ev.league() != null ? ev.league().id() : null;
            String tournamentId = ev.tournament() != null ? ev.tournament().id() : null;
            boolean originDiffers =
                    (leagueId != null && !leagueId.equals(match.getLeagueExternalId()))
                    || (tournamentId != null && !tournamentId.equals(match.getTournamentExternalId()));
            if (originDiffers) {
                if (!dryRun) {
                    match.reassignOrigin(leagueId, tournamentId);
                }
                originFixed++;
            }

            // 2) 승패 — 과반 세트를 가져간 팀이 있을 때만 확정한다
            outcomeFixed += repairOutcomes(match, ev, dryRun);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dryRun", dryRun);
        out.put("matchesScanned", matches.size());
        out.put("originFixed", originFixed);
        out.put("outcomeFixed", outcomeFixed);
        out.put("failed", failed);
        log.info("매치 보정 완료 — {}", out);
        return out;
    }

    private int repairOutcomes(EsportsMatch match, EventDetailsResponse.Event ev, boolean dryRun) {
        if (ev.match() == null || ev.match().teams() == null) {
            return 0;
        }
        Integer bestOf = ev.match().strategy() != null ? ev.match().strategy().count() : match.getBestOf();
        int needed = (bestOf == null ? 1 : bestOf) / 2 + 1;

        boolean decided = ev.match().teams().stream()
                .anyMatch(t -> t.result() != null && t.result().gameWins() != null
                        && t.result().gameWins() >= needed);
        if (!decided) {
            return 0;   // 진행 중 — Bo3 의 1:0 은 아직 승부가 나지 않았다
        }

        Map<String, MatchTeam> byTeamId = new LinkedHashMap<>();
        for (MatchTeam mt : matchTeamRepo.findByMatchIdOrderByDisplayOrderAsc(match.getId())) {
            if (mt.getExternalTeamId() != null) {
                byTeamId.put(mt.getExternalTeamId(), mt);
            }
        }

        int fixed = 0;
        for (ScheduleResponse.Team team : ev.match().teams()) {
            MatchTeam mt = team.id() != null ? byTeamId.get(team.id()) : null;
            if (mt == null || team.result() == null || team.result().gameWins() == null) {
                continue;
            }
            int wins = team.result().gameWins();
            String outcome = team.result().outcome();
            if (outcome == null || outcome.isBlank()) {
                outcome = wins >= needed ? "win" : "loss";
            }
            boolean differs = !outcome.equals(mt.getOutcome())
                    || !Integer.valueOf(wins).equals(mt.getGameWins());
            if (differs) {
                if (!dryRun) {
                    mt.updateResult(outcome, wins);
                }
                fixed++;
            }
        }
        return fixed;
    }
}
