package com.clutch.betting.live;

import com.clutch.betting.config.BettingProperties;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.WindowResponse;
import com.clutch.lolesports.repository.EsportsGameRepository;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.SetWinnerTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/**
 * lolesports 캐시를 읽어 배팅 동기화용 라이브 상태로 변환하고 신규 배팅 가능 여부를 판단한다.
 *
 * <p>lolesports의 원본 DTO·캐시 구조가 배팅 서비스와 스케줄러로 퍼지지 않도록,
 * 필요한 매치·세트 상태만 불변 스냅샷으로 제공한다.</p>
 */
@Component
@RequiredArgsConstructor
public class BettingLiveStateReader {

    private final DataCacheService dataCacheService;
    private final SetWinnerTracker setWinnerTracker;
    private final EsportsGameRepository esportsGameRepository;
    private final EsportsMatchRepository esportsMatchRepository;
    private final BettingProperties bettingProperties;
    private final Clock clock;

    /**
     * 캐시된 모든 배팅 후보 매치를 배팅용 스냅샷으로 변환한다.
     *
     * @return 배팅 동기화용 라이브 매치 스냅샷 목록
     */
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
                isMatchFinished(liveMatch),
                resolveBestOf(liveMatch)
        );
    }

    /** 라이브 캐시의 다전제 수가 비어 있으면 이미 적재된 매치 정보로 즉시 보완한다. */
    private Integer resolveBestOf(DataCacheService.LiveMatch liveMatch) {
        Integer bestOf = liveMatch.bestOf();
        if (bestOf == null || bestOf < 1) {
            bestOf = esportsMatchRepository.findByExternalMatchId(liveMatch.matchId())
                    .map(match -> match.getBestOf())
                    .filter(value -> value != null && value > 0)
                    .orElse(null);
        }
        int maximumKnownSetNumber = liveMatch.games() == null
                ? 0
                : liveMatch.games().stream()
                        .map(EventDetailsResponse.Game::number)
                        .filter(number -> number != null && number > 0)
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0);
        if (maximumKnownSetNumber > 0
                && (bestOf == null || maximumKnownSetNumber > bestOf)) {
            return maximumKnownSetNumber;
        }
        return bestOf;
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

    /** 세트 스냅샷의 식별과 순서에 필요한 외부 게임 ID와 세트 번호가 모두 있는지 확인한다. */
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
        WindowResponse.Frame frame = dataCacheService.getNewestWindowFrame(game.id());
        LocalDateTime finishedAt = toUtc(dataCacheService.getFeedFinishedAt(game.id()));
        boolean finished = finishedAt != null || "completed".equalsIgnoreCase(game.state());
        return new SetSnapshot(
                game.id(),
                game.number(),
                startedAt,
                frame != null ? frame.gameTimeSeconds() : null,
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
        if (match.bestOf() != null && match.bestOf() > 0 && setNumber > match.bestOf()) {
            return false;
        }
        if (match.sets().isEmpty()) {
            return setNumber == 1 && externalGameId == null;
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
                    .anyMatch(this::isWithinStartGracePeriod);
        }
        return match.sets().stream()
                .filter(set -> set.setNumber() == setNumber)
                .findFirst()
                .map(this::isWithinStartGracePeriod)
                .orElse(setNumber > 1);
    }

    /** 실제 세트가 시작되면 공통 1분 유예 시간까지만 배팅을 허용한다. */
    private boolean isWithinStartGracePeriod(SetSnapshot set) {
        if (set.finished()) {
            return false;
        }
        if (set.gameTimeSeconds() != null) {
            return set.gameTimeSeconds() < bettingProperties.firstSetCloseAfterStart().toSeconds();
        }
        if (set.startedAt() == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return now.isBefore(set.startedAt().plus(bettingProperties.firstSetCloseAfterStart()));
    }

    /**
     * 팀·세트·종료 여부를 묶은 매치 단위 불변 스냅샷이다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param scheduledStartAt 공식 일정의 매치 시작 시각(UTC) 또는 미확정이면 null
     * @param externalTeamIds 배팅 선택지로 사용할 참가 팀 외부 ID 목록
     * @param sets 세트 번호순 라이브 세트 상태
     * @param matchFinished 다전제 승자가 확정돼 매치가 끝났는지 여부
     * @param bestOf 최대 세트 수 또는 확인되지 않았으면 null
     */
    public record LiveMatchSnapshot(
            String externalMatchId,
            LocalDateTime scheduledStartAt,
            List<String> externalTeamIds,
            List<SetSnapshot> sets,
            boolean matchFinished,
            Integer bestOf
    ) {

        /** 다전제 수가 아직 확인되지 않은 매치의 스냅샷을 만든다. */
        public LiveMatchSnapshot(
                String externalMatchId,
                LocalDateTime scheduledStartAt,
                List<String> externalTeamIds,
                List<SetSnapshot> sets,
                boolean matchFinished
        ) {
            this(externalMatchId, scheduledStartAt, externalTeamIds, sets, matchFinished, null);
        }
    }

    /**
     * 세트 시작·종료·승자 상태를 묶은 불변 스냅샷이다.
     *
     * @param externalGameId 외부 게임 ID
     * @param setNumber 매치 내 세트 번호
     * @param startedAt 첫 프레임 기준 세트 시작 시각(UTC) 또는 미확정이면 null
     * @param gameTimeSeconds 피드가 제공한 게임 경과 초 또는 미제공이면 null
     * @param active 현재 라이브로 진행 중인 세트인지 여부
     * @param finished 세트 종료가 확인됐는지 여부
     * @param finishedAt 종료 피드 시각(UTC) 또는 미확정이면 null
     * @param winnerExternalTeamId 확정된 승리 팀 외부 ID 또는 미확정이면 null
     */
    public record SetSnapshot(
            String externalGameId,
            int setNumber,
            LocalDateTime startedAt,
            Long gameTimeSeconds,
            boolean active,
            boolean finished,
            LocalDateTime finishedAt,
            String winnerExternalTeamId
    ) {

        /** 게임 시계가 없는 세트 스냅샷을 만든다. */
        public SetSnapshot(
                String externalGameId,
                int setNumber,
                LocalDateTime startedAt,
                boolean active,
                boolean finished,
                LocalDateTime finishedAt,
                String winnerExternalTeamId
        ) {
            this(externalGameId, setNumber, startedAt, null, active, finished, finishedAt, winnerExternalTeamId);
        }
    }
}
