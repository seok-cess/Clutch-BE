package com.clutch.betting.integration.lolesports;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.SetWinnerTracker;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
/** lolesports 캐시를 배팅 도메인이 사용하는 라이브 스냅샷으로 변환한다. */
public class LolesportsBettingCacheAdapter implements LiveBettingCache {

    private final DataCacheService dataCacheService;
    private final SetWinnerTracker setWinnerTracker;

    /** 라이브 매치 캐시와 세트 승자 추적기를 주입받는다. */
    public LolesportsBettingCacheAdapter(
            DataCacheService dataCacheService,
            SetWinnerTracker setWinnerTracker
    ) {
        this.dataCacheService = dataCacheService;
        this.setWinnerTracker = setWinnerTracker;
    }

    @Override
    /** 캐시된 모든 라이브 매치를 배팅용 스냅샷으로 변환한다. */
    public List<LiveMatchSnapshot> findLiveMatches() {
        return dataCacheService.getLiveMatches().stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    /** 경기 종료·참가 팀·이전 세트 종료 여부를 모두 만족할 때만 배팅을 허용한다. */
    public boolean isAcceptingBets(
            String externalMatchId,
            String externalGameId,
            int setNumber
    ) {
        return findLiveMatches().stream()
                .filter(match -> match.externalMatchId().equals(externalMatchId))
                .filter(match -> !match.matchFinished())
                .filter(match -> match.externalTeamIds().size() == 2)
                .anyMatch(match -> isSetAcceptingBets(match, externalGameId, setNumber));
    }

    /** 외부 라이브 매치에서 유효한 팀과 정렬된 세트 상태를 추출한다. */
    private LiveMatchSnapshot toSnapshot(DataCacheService.LiveMatch liveMatch) {
        List<String> teamIds = liveMatch.teams() == null
                ? List.of()
                : liveMatch.teams().stream()
                        .map(team -> team.id())
                        .filter(id -> id != null && !id.isBlank())
                        .distinct()
                        .toList();

        List<SetSnapshot> sets = new ArrayList<>();
        if (liveMatch.games() != null) {
            for (EventDetailsResponse.Game game : liveMatch.games()) {
                if (game.id() == null || game.id().isBlank() || game.number() == null) {
                    continue;
                }
                LocalDateTime startedAt = dataCacheService.getGameStart(game.id()) == null
                        ? null
                        : LocalDateTime.ofInstant(
                                dataCacheService.getGameStart(game.id()),
                                ZoneOffset.UTC
                        );
                sets.add(new SetSnapshot(
                        game.id(),
                        game.number(),
                        startedAt,
                        game.id().equals(liveMatch.activeGameId()),
                        dataCacheService.isFeedFinished(game.id())
                                || "completed".equalsIgnoreCase(game.state()),
                        setWinnerTracker.winnerOf(liveMatch.matchId(), game.id())
                ));
            }
        }
        sets.sort(Comparator.comparingInt(SetSnapshot::setNumber));
        return new LiveMatchSnapshot(
                liveMatch.matchId(),
                teamIds,
                List.copyOf(sets),
                isMatchFinished(liveMatch)
        );
    }

    /** best-of 승리 조건을 충족한 팀이 있는지 보수적으로 판단한다. */
    private boolean isMatchFinished(DataCacheService.LiveMatch liveMatch) {
        if (liveMatch.bestOf() == null || liveMatch.bestOf() < 1) {
            return false;
        }
        int bestOf = liveMatch.bestOf();
        int requiredWins = bestOf / 2 + 1;
        return liveMatch.teams() != null && liveMatch.teams().stream()
                .map(team -> team.result())
                .filter(result -> result != null && result.gameWins() != null)
                .anyMatch(result -> result.gameWins() >= requiredWins);
    }

    /** 실제 또는 선개설 세트가 현재 배팅 가능한 순서와 상태인지 검증한다. */
    private boolean isSetAcceptingBets(
            LiveMatchSnapshot match,
            String externalGameId,
            int setNumber
    ) {
        if (match.sets().isEmpty()) {
            return false;
        }
        boolean previousSetFinished = setNumber == 1 || match.sets().stream()
                .filter(set -> set.setNumber() == setNumber - 1)
                .anyMatch(SetSnapshot::finished);
        if (!previousSetFinished) {
            return false;
        }
        if (externalGameId != null) {
            return match.sets().stream()
                    .filter(set -> set.setNumber() == setNumber)
                    .filter(set -> set.externalGameId().equals(externalGameId))
                    .anyMatch(set -> !set.finished());
        }
        List<SetSnapshot> targetSets = match.sets().stream()
                .filter(set -> set.setNumber() == setNumber)
                .toList();
        if (!targetSets.isEmpty()) {
            return targetSets.stream().anyMatch(set -> !set.finished());
        }
        return setNumber > 1;
    }
}
