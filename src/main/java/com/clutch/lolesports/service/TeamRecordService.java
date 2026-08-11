package com.clutch.lolesports.service;

import com.clutch.lolesports.api.ApiDtos;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 완료된 경기 기록으로 팀별 최근 전적과 상대 전적(H2H)을 계산한다.
 *
 * 추가 API 호출 없이 이미 수집한 일정 데이터만 사용한다.
 * 계산은 요청 시점에 수행 — 경기 수가 수백 건 규모라 캐싱할 만큼 무겁지 않다.
 */
@Service
public class TeamRecordService {

    /** 최근 폼에 포함할 경기 수 */
    private static final int RECENT_LIMIT = 5;

    private final DataCacheService cache;

    public TeamRecordService(DataCacheService cache) {
        this.cache = cache;
    }

    /** 팀 코드 → 최근 경기 결과 (최신순). 완료 경기만 */
    public Map<String, List<ApiDtos.RecentMatch>> recentFormByTeam() {
        Map<String, List<ApiDtos.RecentMatch>> byTeam = new HashMap<>();

        for (ScheduleResponse.Event ev : completedEvents()) {
            List<ScheduleResponse.Team> teams = ev.match().teams();
            if (teams == null || teams.size() < 2) {
                continue;
            }
            for (int i = 0; i < teams.size(); i++) {
                ScheduleResponse.Team self = teams.get(i);
                ScheduleResponse.Team opponent = teams.get(i == 0 ? 1 : 0);
                if (!hasCode(self) || !hasCode(opponent)) {
                    continue;
                }
                String outcome = self.result() != null ? self.result().outcome() : null;
                if (outcome == null) {
                    continue;
                }
                byTeam.computeIfAbsent(self.code(), k -> new ArrayList<>())
                        .add(new ApiDtos.RecentMatch(
                                ev.startTime(),
                                opponent.code(),
                                opponent.name(),
                                outcome,
                                self.result().gameWins(),
                                opponent.result() != null ? opponent.result().gameWins() : null
                        ));
            }
        }

        // 최신순 정렬 후 상위 N개만
        byTeam.replaceAll((code, list) -> {
            list.sort(Comparator.comparing(ApiDtos.RecentMatch::startTime).reversed());
            return list.size() > RECENT_LIMIT ? new ArrayList<>(list.subList(0, RECENT_LIMIT)) : list;
        });
        return byTeam;
    }

    /**
     * 두 팀의 상대 전적. 팀 순서와 무관하게 동작하며,
     * 반환값은 요청한 순서(codeA 기준) 그대로 매핑된다.
     */
    public ApiDtos.HeadToHead headToHead(String codeA, String codeB) {
        int winsA = 0;
        int winsB = 0;
        List<ApiDtos.RecentMatch> meetings = new ArrayList<>();

        for (ScheduleResponse.Event ev : completedEvents()) {
            List<ScheduleResponse.Team> teams = ev.match().teams();
            if (teams == null || teams.size() < 2) {
                continue;
            }
            ScheduleResponse.Team t0 = teams.get(0);
            ScheduleResponse.Team t1 = teams.get(1);
            if (!hasCode(t0) || !hasCode(t1)) {
                continue;
            }

            boolean match = (t0.code().equals(codeA) && t1.code().equals(codeB))
                    || (t0.code().equals(codeB) && t1.code().equals(codeA));
            if (!match) {
                continue;
            }

            ScheduleResponse.Team self = t0.code().equals(codeA) ? t0 : t1;
            ScheduleResponse.Team other = self == t0 ? t1 : t0;
            String outcome = self.result() != null ? self.result().outcome() : null;
            if (outcome == null) {
                continue;
            }
            if ("win".equalsIgnoreCase(outcome)) {
                winsA++;
            } else {
                winsB++;
            }
            meetings.add(new ApiDtos.RecentMatch(
                    ev.startTime(),
                    other.code(),
                    other.name(),
                    outcome,
                    self.result().gameWins(),
                    other.result() != null ? other.result().gameWins() : null
            ));
        }

        meetings.sort(Comparator.comparing(ApiDtos.RecentMatch::startTime).reversed());
        return new ApiDtos.HeadToHead(codeA, codeB, winsA, winsB, meetings);
    }

    // ---- 내부 ----

    private List<ScheduleResponse.Event> completedEvents() {
        ScheduleResponse cached = cache.getSchedule();
        if (cached == null || cached.data() == null || cached.data().schedule() == null
                || cached.data().schedule().events() == null) {
            return List.of();
        }
        return cached.data().schedule().events().stream()
                .filter(e -> "completed".equalsIgnoreCase(e.state()))
                .filter(e -> e.match() != null)
                .toList();
    }

    /** TBD(미확정 대진)는 전적 계산에서 제외 */
    private static boolean hasCode(ScheduleResponse.Team t) {
        return t != null && t.code() != null && !t.code().isBlank() && !"TBD".equalsIgnoreCase(t.code());
    }
}
