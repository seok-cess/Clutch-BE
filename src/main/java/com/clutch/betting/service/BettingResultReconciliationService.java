package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.lolesports.client.LolesportsApiClient;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.repository.EsportsGameRepository;
import com.clutch.lolesports.service.GamePersistService;
import com.clutch.lolesports.service.SetWinnerTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 라이브 목록에서 사라진 뒤에도 종료됐지만 미정산인 세트의 공식 결과를 다시 확인한다.
 *
 * <p>livestats의 {@code finished}는 세트 종료 신호일 뿐 승자를 주지 않는다. DB에 남은
 * {@code ended_at != null && winner_decided_at == null} 세트를 출발점으로 매치 상세를
 * 직접 재조회하고, 공식 {@code gameWins} 증가분이 확인됐을 때만 기존 정산 경로에 넘긴다.</p>
 */
@Service
public class BettingResultReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(BettingResultReconciliationService.class);

    private final EsportsGameRepository gameRepository;
    private final BettingEventRepository bettingEventRepository;
    private final LolesportsApiClient apiClient;
    private final SetWinnerTracker setWinnerTracker;
    private final GamePersistService gamePersistService;
    private final BettingEventSynchronizationService synchronizationService;

    public BettingResultReconciliationService(
            EsportsGameRepository gameRepository,
            BettingEventRepository bettingEventRepository,
            LolesportsApiClient apiClient,
            SetWinnerTracker setWinnerTracker,
            GamePersistService gamePersistService,
            BettingEventSynchronizationService synchronizationService
    ) {
        this.gameRepository = gameRepository;
        this.bettingEventRepository = bettingEventRepository;
        this.apiClient = apiClient;
        this.setWinnerTracker = setWinnerTracker;
        this.gamePersistService = gamePersistService;
        this.synchronizationService = synchronizationService;
    }

    /** 종료됐지만 승자가 없는 세트 또는 선개설 후속 세트가 있는 매치별로 공식 결과를 재조회한다. */
    public void reconcilePendingResults() {
        Set<String> externalMatchIds = new LinkedHashSet<>(
                gameRepository.findExternalMatchIdsPendingWinnerReconciliation()
        );
        externalMatchIds.addAll(bettingEventRepository.findExternalMatchIdsWithOpenSpeculativeFutureEvent());
        externalMatchIds.addAll(bettingEventRepository.findExternalMatchIdsWithClosedEventWithoutWinner());
        for (String externalMatchId : externalMatchIds) {
            try {
                reconcileMatch(externalMatchId);
            } catch (RuntimeException exception) {
                log.warn("배팅 결과 재조회 실패 (matchId={}): {}", externalMatchId, exception.toString());
            }
        }
    }

    private void reconcileMatch(String externalMatchId) {
        if (externalMatchId == null || externalMatchId.isBlank()) {
            return;
        }

        synchronizationService.closeFinishedEventsForReconciliation(externalMatchId);
        List<BettingEvent> events = bettingEventRepository.findAllByExternalMatchId(externalMatchId);
        restoreConfirmedWinners(externalMatchId, events);

        EventDetailsResponse response = apiClient.getEventDetails(externalMatchId);
        EventDetailsResponse.Match match = matchOf(response);
        if (match == null || match.teams() == null || match.games() == null) {
            log.debug("배팅 결과 재조회 응답이 불완전합니다 (matchId={})", externalMatchId);
            return;
        }

        setWinnerTracker.observe(externalMatchId, match.teams(), match.games());
        Map<String, String> winners = pendingEventWinners(externalMatchId, events);
        if (!winners.isEmpty()) {
            gamePersistService.persistTrackedWinners(externalMatchId);
            synchronizationService.synchronizeConfirmedWinners(externalMatchId, winners);
            log.info("배팅 결과 재조회로 세트 승자 {}건 확정 (matchId={})", winners.size(), externalMatchId);
        }
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

    private java.util.Optional<Integer> lastFinishedSetNumber(EventDetailsResponse.Match match) {
        return match.games().stream()
                .filter(game -> "completed".equalsIgnoreCase(game.state()))
                .map(EventDetailsResponse.Game::number)
                .filter(number -> number != null && number > 0)
                .max(Integer::compareTo);
    }
}
