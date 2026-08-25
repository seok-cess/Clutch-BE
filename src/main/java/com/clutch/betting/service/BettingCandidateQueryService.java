package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.dto.BettingCandidateView;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.lolesports.service.SetWinnerTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/** 실제로 열려 있는 배팅 이벤트와 연결된 예정·라이브 매치를 조회한다. */
@Service
@RequiredArgsConstructor
public class BettingCandidateQueryService {

    private final BettingEventRepository bettingEventRepository;
    private final DataCacheService dataCacheService;
    private final SetWinnerTracker setWinnerTracker;
    private final PollingScheduler pollingScheduler;
    private final Clock clock;

    /**
     * 배팅 이벤트가 OPEN이고 현재 시각에도 유효한 매치만 반환한다.
     *
     * <p>라이브 화면용 캐시와 배팅 후보 캐시는 의도적으로 분리돼 있다. 이 조회는
     * 시작 전 20분처럼 아직 {@code /api/live}에 없는 경기라도, 실제 배팅 이벤트가
     * 생성된 뒤에만 사용자 화면에 노출하게 한다.</p>
     *
     * @return 현재 배팅 가능한 매치의 화면 표시용 조회 모델
     */
    @Transactional(readOnly = true)
    public List<BettingCandidateView> findOpenMatchCandidates() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Set<String> openMatchIds = bettingEventRepository.findAllByStatus(BettingEventStatus.OPEN).stream()
                .filter(event -> event.isOpenAt(now))
                .map(event -> event.getExternalMatchId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        if (openMatchIds.isEmpty()) {
            return List.of();
        }
        return dataCacheService.getBettingMatches().stream()
                .filter(match -> openMatchIds.contains(match.matchId()))
                .filter(match -> !match.isFinished())
                .map(this::toView)
                .toList();
    }

    /** lolesports 캐시를 배팅 후보 API가 소유한 조회 모델로 변환한다. */
    private BettingCandidateView toView(DataCacheService.LiveMatch match) {
        return new BettingCandidateView(
                match.matchId(),
                match.leagueName(),
                match.blockName(),
                match.startTime(),
                match.bestOf(),
                match.isFinished(),
                match.winnerTeamId(),
                teamsOf(match.teams()),
                gamesOf(match),
                match.activeGameId()
        );
    }

    private List<BettingCandidateView.Team> teamsOf(List<ScheduleResponse.Team> teams) {
        if (teams == null) {
            return List.of();
        }
        return teams.stream()
                .map(team -> new BettingCandidateView.Team(
                        team.id(),
                        team.name(),
                        team.code(),
                        team.image(),
                        team.result() != null ? team.result().outcome() : null,
                        team.result() != null ? team.result().gameWins() : null,
                        team.record() != null ? team.record().wins() : null,
                        team.record() != null ? team.record().losses() : null
                ))
                .toList();
    }

    private List<BettingCandidateView.Game> gamesOf(DataCacheService.LiveMatch match) {
        List<EventDetailsResponse.Game> games = match.games();
        if (games == null) {
            return List.of();
        }
        return games.stream()
                .map(game -> new BettingCandidateView.Game(
                        game.id(),
                        game.number(),
                        game.state(),
                        dataCacheService.isFeedFinished(game.id()),
                        setWinnerTracker.winnerOf(match.matchId(), game.id()),
                        pollingScheduler.isStatsUnavailable(game.id())
                ))
                .toList();
    }
}
