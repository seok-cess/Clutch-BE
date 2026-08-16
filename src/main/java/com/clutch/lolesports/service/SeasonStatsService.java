package com.clutch.lolesports.service;

import com.clutch.lolesports.api.ApiDtos;
import com.clutch.lolesports.repository.SeasonStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

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
