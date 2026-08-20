package com.clutch.lolesports.service;

import com.clutch.lolesports.api.ApiDtos;
import com.clutch.lolesports.repository.SeasonStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 메인 화면의 시즌 요약 지표를 계산한다.
 *
 * 적재가 끝난 세트 기록만 사용하며 외부 API 를 호출하지 않는다.
 * 시즌 규모가 수백 세트라 요청 시점 집계로 충분해 별도 캐시를 두지 않는다
 * (TeamRecordService 와 같은 판단).
 */
@Service
public class SeasonStatsService {

    /** KDA 순위에 넣을 최소 출전 세트. 한두 판만 뛴 선수가 상위를 차지하는 것을 막는다 */
    private static final int MIN_GAMES_FOR_KDA = 3;

    /** 픽률 순위에 넣을 최소 픽 수. 단발성 픽을 제외한다 */
    private static final int MIN_PICKS = 2;

    private final SeasonStatsRepository repository;

    public SeasonStatsService(SeasonStatsRepository repository) {
        this.repository = repository;
    }

    /** 시즌 누적 KDA 상위 선수 */
    @Transactional(readOnly = true)
    public ApiDtos.PlayerKdaBoard playerKda(String season, int limit) {
        String seasonKey = resolveSeason(season);
        if (seasonKey == null) {
            return new ApiDtos.PlayerKdaBoard(null, 0, List.of());
        }

        int totalGames = (int) repository.finalizedGameCount(seasonKey);

        List<ApiDtos.PlayerKdaRow> rows = repository.playerTotals(seasonKey).stream()
                .filter(t -> count(t.getGames()) >= MIN_GAMES_FOR_KDA)
                .map(t -> {
                    int kills = count(t.getKills());
                    int deaths = count(t.getDeaths());
                    int assists = count(t.getAssists());
                    return new ApiDtos.PlayerKdaRow(
                            0, // 정렬 후 채운다
                            t.getSummonerName(),
                            t.getTeamCode(),
                            count(t.getGames()),
                            kills, deaths, assists,
                            kda(kills, deaths, assists));
                })
                .sorted(Comparator.comparingDouble(ApiDtos.PlayerKdaRow::kda).reversed())
                .limit(Math.max(1, limit))
                .toList();

        return new ApiDtos.PlayerKdaBoard(seasonKey, totalGames, withPlayerRank(rows));
    }

    /** 시즌 챔피언 픽률과 승률 */
    @Transactional(readOnly = true)
    public ApiDtos.ChampionBoard champions(String season, int limit) {
        String seasonKey = resolveSeason(season);
        if (seasonKey == null) {
            return new ApiDtos.ChampionBoard(null, 0, List.of());
        }

        int totalGames = (int) repository.finalizedGameCount(seasonKey);

        List<ApiDtos.ChampionRow> rows = repository.championTotals(seasonKey).stream()
                .filter(t -> count(t.getPicks()) >= MIN_PICKS)
                .map(t -> {
                    int picks = count(t.getPicks());
                    int decided = count(t.getDecidedPicks());
                    int wins = count(t.getWins());
                    return new ApiDtos.ChampionRow(
                            0,
                            t.getChampionId(),
                            picks,
                            totalGames > 0 ? ratio(picks, totalGames) : null,
                            decided > 0 ? wins : null,
                            decided,
                            decided > 0 ? ratio(wins, decided) : null);
                })
                .sorted(Comparator.comparingInt(ApiDtos.ChampionRow::picks).reversed())
                .limit(Math.max(1, limit))
                .toList();

        return new ApiDtos.ChampionBoard(seasonKey, totalGames, withChampionRank(rows));
    }

    /**
     * KDA = (킬 + 어시스트) / 데스.
     * 데스가 0이면 나눌 수 없으므로 1로 본다(이른바 Perfect KDA 관례).
     */
    private static double kda(int kills, int deaths, int assists) {
        return round2((double) (kills + assists) / Math.max(deaths, 1));
    }

    private static Double ratio(int numerator, int denominator) {
        return round2((double) numerator / denominator);
    }

    /** 소수 둘째 자리까지. 화면이 그대로 쓰도록 서버에서 자른다 */
    /**
     * 리그 팀 순위 (매치 기준).
     *
     * 정렬은 승 → 세트 득실 → 승률 순이다. 세트 득실을 2순위로 두는 것은
     * 같은 승수면 세트를 더 많이 딴 팀이 위로 가는 일반적인 리그 규칙을 따른 것이다.
     *
     * KDA·킬 같은 세트 기록 지표는 여기 넣지 않는다. 그쪽은 game_player_stat
     * 적재가 끝나야 하는데 매치 전적과 갱신 시점이 달라 한 응답에 섞으면
     * 일부 팀만 값이 비는 상태가 생긴다.
     */
    @Transactional(readOnly = true)
    public ApiDtos.TeamStandingsBoard teamStandings(String season, String leagueId,
                                                    List<String> tournamentIds,
                                                    Map<String, String> groupByTeamCode) {
        String seasonKey = resolveSeason(season);
        if (seasonKey == null || tournamentIds == null || tournamentIds.isEmpty()) {
            return new ApiDtos.TeamStandingsBoard(seasonKey, List.of());
        }

        List<ApiDtos.TeamStandingRow> rows = repository
                .teamStandings(seasonKey, leagueId, tournamentIds).stream()
                .map(t -> {
                    int wins = count(t.getWins());
                    int losses = count(t.getLosses());
                    int games = wins + losses;
                    int setsWon = count(t.getSetsWon());
                    int setsLost = count(t.getSetsLost());
                    return new ApiDtos.TeamStandingRow(
                            null,
                            t.getTeamCode(),
                            t.getTeamName(),
                            t.getTeamImageUrl(),
                            games, wins, losses,
                            setsWon, setsLost, setsWon - setsLost,
                            games == 0 ? null : round2((double) wins / games));
                })
                .sorted(Comparator
                        .comparingInt(ApiDtos.TeamStandingRow::wins).reversed()
                        .thenComparing(Comparator.comparingInt(ApiDtos.TeamStandingRow::setDiff).reversed())
                        .thenComparing(r -> r.winRate() == null ? 0.0 : r.winRate(),
                                Comparator.reverseOrder()))
                .toList();

        Map<String, String> groups = groupByTeamCode == null ? Map.of() : groupByTeamCode;
        if (groups.isEmpty()) {
            // 그룹 편성을 모르면 단일 순위표로 내려준다
            return new ApiDtos.TeamStandingsBoard(seasonKey,
                    List.of(new ApiDtos.TeamStandingsGroup(null, withTeamRank(rows))));
        }

        // 그룹별로 나눠 각 그룹 안에서 순위를 매긴다. 소스가 준 그룹 순서를 유지한다
        Map<String, List<ApiDtos.TeamStandingRow>> byGroup = new java.util.LinkedHashMap<>();
        for (String name : new java.util.LinkedHashSet<>(groups.values())) {
            byGroup.put(name, new java.util.ArrayList<>());
        }
        List<ApiDtos.TeamStandingRow> ungrouped = new java.util.ArrayList<>();
        for (ApiDtos.TeamStandingRow r : rows) {
            String g = groups.get(r.teamCode());
            if (g == null) {
                ungrouped.add(r);
            } else {
                byGroup.get(g).add(r);
            }
        }

        List<ApiDtos.TeamStandingsGroup> out = new java.util.ArrayList<>();
        byGroup.forEach((name, list) -> {
            if (!list.isEmpty()) {
                out.add(new ApiDtos.TeamStandingsGroup(name, withTeamRank(list)));
            }
        });
        if (!ungrouped.isEmpty()) {
            out.add(new ApiDtos.TeamStandingsGroup(null, withTeamRank(ungrouped)));
        }
        return new ApiDtos.TeamStandingsBoard(seasonKey, out);
    }

    /** 정렬된 순위표에 순위를 매긴다. 승·세트득실이 모두 같으면 공동 순위 */
    private static List<ApiDtos.TeamStandingRow> withTeamRank(List<ApiDtos.TeamStandingRow> rows) {
        List<ApiDtos.TeamStandingRow> out = new java.util.ArrayList<>(rows.size());
        int rank = 0;
        Integer prevWins = null;
        Integer prevDiff = null;
        for (int i = 0; i < rows.size(); i++) {
            ApiDtos.TeamStandingRow r = rows.get(i);
            boolean tied = prevWins != null && prevWins == r.wins() && prevDiff == r.setDiff();
            if (!tied) {
                rank = i + 1;
            }
            prevWins = r.wins();
            prevDiff = r.setDiff();
            out.add(new ApiDtos.TeamStandingRow(rank, r.teamCode(), r.teamName(), r.teamImageUrl(),
                    r.games(), r.wins(), r.losses(), r.setsWon(), r.setsLost(), r.setDiff(),
                    r.winRate()));
        }
        return out;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static int count(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private String resolveSeason(String season) {
        if (season != null && !season.isBlank()) {
            return season.trim();
        }
        return repository.latestSeasonKey();
    }

    private static List<ApiDtos.PlayerKdaRow> withPlayerRank(List<ApiDtos.PlayerKdaRow> rows) {
        List<ApiDtos.PlayerKdaRow> out = new java.util.ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ApiDtos.PlayerKdaRow r = rows.get(i);
            out.add(new ApiDtos.PlayerKdaRow(i + 1, r.summonerName(), r.teamCode(),
                    r.games(), r.kills(), r.deaths(), r.assists(), r.kda()));
        }
        return out;
    }

    private static List<ApiDtos.ChampionRow> withChampionRank(List<ApiDtos.ChampionRow> rows) {
        List<ApiDtos.ChampionRow> out = new java.util.ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ApiDtos.ChampionRow r = rows.get(i);
            out.add(new ApiDtos.ChampionRow(i + 1, r.championId(), r.picks(), r.pickRate(),
                    r.wins(), r.decidedPicks(), r.winRate()));
        }
        return out;
    }
}
