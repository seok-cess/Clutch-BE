package com.clutch.betting.live;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.repository.EsportsGameRepository;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.SetWinnerTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/** lolesports 캐시를 배팅 도메인의 라이브 데이터 계약으로 제공한다. */
@Component
@RequiredArgsConstructor
public class LolesportsLiveBettingDataProvider implements LiveBettingDataProvider {

    private final DataCacheService dataCacheService;
    private final SetWinnerTracker setWinnerTracker;
    private final EsportsGameRepository esportsGameRepository;

    /**
     * 캐시된 모든 배팅 후보 매치를 배팅용 스냅샷으로 변환한다.
     *
     * @return 배팅 동기화용 라이브 매치 스냅샷 목록
     */
    @Override
    public List<LiveMatchSnapshot> findLiveMatches() {
        return dataCacheService.getBettingMatches().stream()
                .map(this::toSnapshot)
                .toList();
    }

    /**
     * 경기 종료·참가 팀·이전 세트 종료 여부를 모두 만족할 때만 배팅을 허용한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param externalGameId 연결된 외부 게임 ID 또는 선개설 이벤트이면 null
     * @param setNumber 세트 번호
     * @return 최신 캐시 상태에서 배팅 가능하면 true
     */
    @Override
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

    /**
     * 외부 배팅 후보 매치에서 유효한 팀과 정렬된 세트 상태를 추출한다.
     *
     * @param liveMatch lolesports 라이브 매치 캐시 값
     * @return 배팅 도메인용 라이브 매치 스냅샷
     */
    private LiveMatchSnapshot toSnapshot(DataCacheService.LiveMatch liveMatch) {
        List<String> teamIds = liveMatch.teams() == null
                ? List.of()
                : liveMatch.teams().stream()
                        .map(team -> team.id())
                        .filter(id -> id != null && !id.isBlank())
                        .distinct()
                        .toList();

        return new LiveMatchSnapshot(
                liveMatch.matchId(),
                parseUtc(liveMatch.startTime()),
                teamIds,
                toSetSnapshots(liveMatch),
                isMatchFinished(liveMatch)
        );
    }

    /**
     * 외부 게임 목록을 세트 번호순 배팅 스냅샷으로 변환한다.
     *
     * @param liveMatch 세트 목록을 가진 외부 매치
     * @return 유효한 세트만 포함한 정렬된 불변 목록
     */
    private List<SetSnapshot> toSetSnapshots(DataCacheService.LiveMatch liveMatch) {
        if (liveMatch.games() == null) {
            return List.of();
        }

        return liveMatch.games().stream()
                .filter(this::hasRequiredGameData)
                .map(game -> toSetSnapshot(liveMatch, game))
                .sorted(Comparator.comparingInt(SetSnapshot::setNumber))
                .toList();
    }

    private boolean hasRequiredGameData(EventDetailsResponse.Game game) {
        return game.id() != null
                && !game.id().isBlank()
                && game.number() != null;
    }

    /**
     * 외부 게임과 캐시된 시작·종료·승자 정보를 하나의 세트 스냅샷으로 결합한다.
     *
     * @param liveMatch 세트가 속한 외부 매치
     * @param game 변환할 외부 게임
     * @return 배팅 도메인용 세트 스냅샷
     */
    private SetSnapshot toSetSnapshot(
            DataCacheService.LiveMatch liveMatch,
            EventDetailsResponse.Game game
    ) {
        LocalDateTime startedAt = toUtc(dataCacheService.getGameStart(game.id()));
        LocalDateTime finishedAt = toUtc(dataCacheService.getFeedFinishedAt(game.id()));
        boolean finished = finishedAt != null || "completed".equalsIgnoreCase(game.state());
        return new SetSnapshot(
                game.id(),
                game.number(),
                startedAt,
                game.id().equals(liveMatch.activeGameId()),
                finished,
                finishedAt,
                findWinnerExternalTeamId(liveMatch.matchId(), game.id(), finished)
        );
    }

    /** 메모리 승자가 없으면 완료 세트에 한해 DB에 확정된 승자를 복원한다. */
    private String findWinnerExternalTeamId(String matchId, String gameId, boolean finished) {
        String trackedWinner = setWinnerTracker.winnerOf(matchId, gameId);
        if (trackedWinner != null || !finished) {
            return trackedWinner;
        }

        return esportsGameRepository.findWinnerExternalTeamId(gameId)
                .map(winnerTeamId -> {
                    setWinnerTracker.restoreWinner(matchId, gameId, winnerTeamId);
                    return winnerTeamId;
                })
                .orElse(null);
    }

    /**
     * nullable Instant를 UTC LocalDateTime으로 변환한다.
     *
     * @param instant 변환할 절대 시각 또는 null
     * @return UTC 로컬 시각 또는 입력이 null이면 null
     */
    private LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * RFC3339 공식 일정 시각을 UTC 로컬 시각으로 변환한다.
     *
     * @param value 외부 일정 시각 문자열
     * @return UTC 공식 시작 시각 또는 파싱할 수 없으면 null
     */
    private LocalDateTime parseUtc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * best-of 승리 조건을 충족한 팀이 있는지 보수적으로 판단한다.
     *
     * @param liveMatch lolesports 라이브 매치 캐시 값
     * @return 매치 승리 조건을 충족한 팀이 있으면 true
     */
    private boolean isMatchFinished(DataCacheService.LiveMatch liveMatch) {
        if (liveMatch.activeGameId() != null && !liveMatch.activeGameId().isBlank()) {
            return false;
        }
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

    /**
     * 실제 또는 선개설 세트가 현재 배팅 가능한 순서와 상태인지 검증한다.
     *
     * @param match 배팅용 라이브 매치 스냅샷
     * @param externalGameId 연결된 외부 게임 ID 또는 선개설 이벤트이면 null
     * @param setNumber 세트 번호
     * @return 이전 세트가 끝나고 대상 세트가 종료되지 않았으면 true
     */
    private boolean isSetAcceptingBets(
            LiveMatchSnapshot match,
            String externalGameId,
            int setNumber
    ) {
        if (match.sets().isEmpty()) {
            return setNumber == 1 && externalGameId == null;
        }
        boolean previousSetFinished = setNumber == 1 || match.sets().stream()
                .filter(set -> set.setNumber() == setNumber - 1)
                .anyMatch(set -> set.finished() && set.finishedAt() != null);
        if (!previousSetFinished) {
            return false;
        }
        if (externalGameId != null) {
            return match.sets().stream()
                    .filter(set -> set.setNumber() == setNumber)
                    .filter(set -> set.externalGameId().equals(externalGameId))
                    .anyMatch(set -> !set.finished());
        }
        return match.sets().stream()
                .filter(set -> set.setNumber() == setNumber)
                .findFirst()
                .map(set -> !set.finished())
                .orElse(setNumber > 1);
    }
}
