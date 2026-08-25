package com.clutch.betting.scheduler;

import com.clutch.betting.live.BettingLiveStateReader;
import com.clutch.betting.live.BettingLiveStateReader.LiveMatchSnapshot;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.service.BettingEventSynchronizationService;
import com.clutch.betting.service.BettingResultRefreshService;
import com.clutch.betting.service.BettingService;
import com.clutch.lolesports.source.ExternalSourceState;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BettingSchedulerTest {

    private final BettingLiveStateReader liveStateReader =
            mock(BettingLiveStateReader.class);
    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final BettingEventSynchronizationService synchronizationService =
            mock(BettingEventSynchronizationService.class);
    private final BettingService bettingService = mock(BettingService.class);
    private final BettingResultRefreshService resultRefreshService = mock(BettingResultRefreshService.class);
    private final ExternalSourceState sourceState = mock(ExternalSourceState.class);
    private final BettingScheduler scheduler = new BettingScheduler(
            liveStateReader,
            eventRepository,
            synchronizationService,
            bettingService,
            resultRefreshService,
            sourceState
    );

    @Test
    void processesSynchronizationSettlementAndRefundInOrderDespiteIndividualFailures() {
        LiveMatchSnapshot firstMatch = snapshot("match-1");
        LiveMatchSnapshot secondMatch = snapshot("match-2");
        given(liveStateReader.findLiveMatches())
                .willReturn(List.of(firstMatch, secondMatch));
        given(eventRepository.findIdsReadyToSettle()).willReturn(List.of(1L, 2L));
        given(eventRepository.findIdsCancelledWithPlacedBets()).willReturn(List.of(3L, 4L));
        willThrow(new DataIntegrityViolationException("duplicate event"))
                .given(synchronizationService).synchronizeMatch(firstMatch);
        willThrow(new IllegalStateException("first settlement failed"))
                .given(bettingService).settle(1L);
        willThrow(new IllegalStateException("first refund failed"))
                .given(bettingService).refund(3L);

        scheduler.process();

        InOrder order = inOrder(synchronizationService, bettingService);
        order.verify(synchronizationService).synchronizeMatch(firstMatch);
        order.verify(synchronizationService).synchronizeMatch(secondMatch);
        order.verify(bettingService).settle(1L);
        order.verify(bettingService).settle(2L);
        order.verify(bettingService).refund(3L);
        order.verify(bettingService).refund(4L);
    }

    @Test
    void continuesWithSettlementAndRefundWhenLiveLookupFails() {
        given(liveStateReader.findLiveMatches())
                .willThrow(new IllegalStateException("live lookup failed"));
        given(eventRepository.findIdsReadyToSettle()).willReturn(List.of(1L));
        given(eventRepository.findIdsCancelledWithPlacedBets()).willReturn(List.of(2L));

        scheduler.process();

        verify(bettingService).settle(1L);
        verify(bettingService).refund(2L);
    }

    @Test
    void refreshesPendingResultsUnderSourceReadLock() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(sourceState).withReadLock(any(Runnable.class));

        scheduler.refreshPendingResults();

        verify(resultRefreshService).refreshPendingResults();
    }

    /**
     * 스케줄러 실패 격리 테스트에 사용할 최소 라이브 매치 스냅샷을 생성한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @return 세트 정보가 없는 라이브 매치 스냅샷
     */
    private LiveMatchSnapshot snapshot(String externalMatchId) {
        return new LiveMatchSnapshot(
                externalMatchId,
                java.time.LocalDateTime.of(2026, 8, 14, 10, 0),
                List.of("team-a", "team-b"),
                List.of(),
                false
        );
    }
}
