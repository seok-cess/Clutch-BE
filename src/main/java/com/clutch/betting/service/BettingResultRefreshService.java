package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.lolesports.client.LolesportsApiClient;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.repository.EsportsGameRepository;
import com.clutch.lolesports.service.GamePersistService;
import com.clutch.lolesports.service.SetWinnerTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 라이브 목록에서 사라진 뒤에도 종료됐지만 미정산인 세트의 공식 결과를 다시 확인한다.
 *
 * <p>livestats의 {@code finished}는 세트 종료 신호일 뿐 승자를 주지 않는다. DB에 남은
 * {@code ended_at != null && winner_decided_at == null} 세트를 출발점으로 매치 상세를
 * 직접 재조회하고, 공식 {@code gameWins} 증가분이 확인됐을 때만 기존 정산 경로에 넘긴다.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BettingResultRefreshService {

    private final EsportsGameRepository gameRepository;
    private final BettingEventRepository bettingEventRepository;
    private final LolesportsApiClient apiClient;
    private final SetWinnerTracker setWinnerTracker;
    private final GamePersistService gamePersistService;
    private final BettingEventSynchronizationService synchronizationService;

    /** 종료됐지만 승자가 없는 세트 또는 선개설 후속 세트가 있는 매치별로 공식 결과를 재조회한다. */
    public void refreshPendingResults() {
        pendingExternalMatchIds().forEach(this::refreshSafely);
    }

    /** 결과 복구가 필요한 세트·이벤트에서 중복 없는 매치 ID 목록을 만든다. */
    private Set<String> pendingExternalMatchIds() {
        Set<String> externalMatchIds = new LinkedHashSet<>(
                gameRepository.findExternalMatchIdsPendingWinnerReconciliation()
        );
        externalMatchIds.addAll(bettingEventRepository.findExternalMatchIdsWithOpenSpeculativeFutureEvent());
        externalMatchIds.addAll(bettingEventRepository.findExternalMatchIdsWithClosedEventWithoutWinner());
        return externalMatchIds;
    }

    /** 한 매치의 재조회 실패가 다른 매치의 결과 복구를 막지 않게 격리한다. */
    private void refreshSafely(String externalMatchId) {
        try {
            refreshMatch(externalMatchId);
        } catch (RuntimeException exception) {
            log.warn("배팅 결과 재조회 실패 (matchId={}): {}", externalMatchId, exception.toString());
        }
    }

    /**
     * 한 매치의 공식 상세를 재조회해 승자 복구와 불필요한 후속 이벤트 취소를 처리한다.
     *
     * @param externalMatchId 재조회할 외부 매치 ID
     */
    private void refreshMatch(String externalMatchId) {
        if (externalMatchId == null || externalMatchId.isBlank()) {
            return;
        }

        List<BettingEvent> events = prepareEventsForRefresh(externalMatchId);
        EventDetailsResponse.Match match = fetchCompleteMatch(externalMatchId);
        if (match == null) {
            return;
        }

        observeAndSynchronizeWinners(externalMatchId, events, match);
        cancelUnusedFutureEventsIfMatchFinished(externalMatchId, match);
    }

    /** 종료 프레임이 확인된 이벤트를 닫고, 재시작에 대비해 기존 승자를 추적기에 복원한다. */
    private List<BettingEvent> prepareEventsForRefresh(String externalMatchId) {
        synchronizationService.closeFinishedEventsForReconciliation(externalMatchId);
        List<BettingEvent> events = bettingEventRepository.findAllByExternalMatchId(externalMatchId);
        restoreConfirmedWinners(externalMatchId, events);
        return events;
    }

    /** 공식 상세 응답에서 팀과 세트 정보가 모두 있는 매치만 반환한다. */
    private EventDetailsResponse.Match fetchCompleteMatch(String externalMatchId) {
        EventDetailsResponse.Match match = matchOf(apiClient.getEventDetails(externalMatchId));
        if (match == null || match.teams() == null || match.games() == null) {
            log.debug("배팅 결과 재조회 응답이 불완전합니다 (matchId={})", externalMatchId);
            return null;
        }
        return match;
    }

    /** 공식 스코어에서 추적한 신규 세트 승자만 이벤트에 반영한다. */
    private void observeAndSynchronizeWinners(
            String externalMatchId,
            List<BettingEvent> events,
            EventDetailsResponse.Match match
    ) {
        setWinnerTracker.observe(externalMatchId, match.teams(), match.games());
        Map<String, String> winners = pendingEventWinners(externalMatchId, events);
        if (!winners.isEmpty()) {
            gamePersistService.persistTrackedWinners(externalMatchId);
            synchronizationService.synchronizeConfirmedWinners(externalMatchId, winners);
            log.info("배팅 결과 재조회로 세트 승자 {}건 확정 (matchId={})", winners.size(), externalMatchId);
        }
    }

    /** 공식 최종 스코어가 확정됐으면 실제로 진행되지 않은 후속 세트 이벤트를 취소한다. */
    private void cancelUnusedFutureEventsIfMatchFinished(
            String externalMatchId,
            EventDetailsResponse.Match match
    ) {
        if (isMatchFinished(match)) {
            lastFinishedSetNumber(match).ifPresent(lastSetNumber ->
                    synchronizationService.cancelFutureEventsAfterConfirmedMatch(
                            externalMatchId,
                            lastSetNumber
                    ));
        }
    }

    /** 이전 세트의 확정 결과를 복원해 재시작 뒤에도 gameWins 증가분을 정확히 계산한다. */
    private void restoreConfirmedWinners(String externalMatchId, List<BettingEvent> events) {
        for (BettingEvent event : events) {
            if (event.getExternalGameId() != null && event.getWinnerExternalTeamId() != null) {
                setWinnerTracker.restoreWinner(
                        externalMatchId,
                        event.getExternalGameId(),
                        event.getWinnerExternalTeamId()
                );
            }
        }
    }

    /** 이번 재조회에서 아직 결과가 없던 종료 이벤트에만 대응하는 승자를 뽑는다. */
    private Map<String, String> pendingEventWinners(String externalMatchId, List<BettingEvent> events) {
        Map<String, String> winners = new LinkedHashMap<>();
        for (BettingEvent event : events) {
            if (event.getWinnerExternalTeamId() != null || event.getExternalGameId() == null) {
                continue;
            }
            String winner = setWinnerTracker.winnerOf(externalMatchId, event.getExternalGameId());
            if (winner != null) {
                winners.put(event.getExternalGameId(), winner);
            }
        }
        return winners;
    }

    /** 공식 상세 응답의 중첩 구조가 완전할 때만 매치 데이터를 꺼낸다. */
    private EventDetailsResponse.Match matchOf(EventDetailsResponse response) {
        if (response == null || response.data() == null || response.data().event() == null) {
            return null;
        }
        return response.data().event().match();
    }

    /** 공식 누적 승수가 다전제 과반에 도달했을 때만 매치 종료로 판단한다. */
    private boolean isMatchFinished(EventDetailsResponse.Match match) {
        if (match.strategy() == null || match.strategy().count() == null
                || match.strategy().count() < 1) {
            return false;
        }
        int requiredWins = match.strategy().count() / 2 + 1;
        return match.teams().stream()
                .map(team -> team.result())
                .filter(result -> result != null && result.gameWins() != null)
                .anyMatch(result -> result.gameWins() >= requiredWins);
    }

    /** 완료 상태 세트 중 가장 큰 번호를 찾아 이후 선개설 이벤트의 취소 기준으로 사용한다. */
    private Optional<Integer> lastFinishedSetNumber(EventDetailsResponse.Match match) {
        return match.games().stream()
                .filter(game -> "completed".equalsIgnoreCase(game.state()))
                .map(EventDetailsResponse.Game::number)
                .filter(number -> number != null && number > 0)
                .max(Integer::compareTo);
    }
}
