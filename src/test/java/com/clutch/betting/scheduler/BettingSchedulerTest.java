package com.clutch.betting.scheduler;

import com.clutch.betting.live.LiveBettingDataProvider;
import com.clutch.betting.live.LiveBettingDataProvider.LiveMatchSnapshot;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.service.BetRefundService;
import com.clutch.betting.service.BetSettlementService;
import com.clutch.betting.service.BettingEventSynchronizationService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BettingSchedulerTest {

    private final LiveBettingDataProvider liveBettingDataProvider =
            mock(LiveBettingDataProvider.class);
    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final BettingEventSynchronizationService synchronizationService =
            mock(BettingEventSynchronizationService.class);
    private final BetSettlementService settlementService = mock(BetSettlementService.class);
    private final BetRefundService refundService = mock(BetRefundService.class);
    private final BettingScheduler scheduler = new BettingScheduler(
            liveBettingDataProvider,
            eventRepository,
            synchronizationService,
            settlementService,
            refundService
    );

    @Test
    void processesSynchronizationSettlementAndRefundInOrderDespiteIndividualFailures() {
        LiveMatchSnapshot firstMatch = snapshot("match-1");
        LiveMatchSnapshot secondMatch = snapshot("match-2");
        given(liveBettingDataProvider.findLiveMatches())
                .willReturn(List.of(firstMatch, secondMatch));
        given(eventRepository.findIdsReadyToSettle()).willReturn(List.of(1L, 2L));
        given(eventRepository.findIdsCancelledWithPlacedBets()).willReturn(List.of(3L, 4L));
        willThrow(new DataIntegrityViolationException("duplicate event"))
                .given(synchronizationService).synchronizeMatch(firstMatch);
        willThrow(new IllegalStateException("first settlement failed"))
                .given(settlementService).settle(1L);
        willThrow(new IllegalStateException("first refund failed"))
                .given(refundService).refund(3L);

        scheduler.process();

        InOrder order = inOrder(synchronizationService, settlementService, refundService);
        order.verify(synchronizationService).synchronizeMatch(firstMatch);
        order.verify(synchronizationService).synchronizeMatch(secondMatch);
        order.verify(settlementService).settle(1L);
        order.verify(settlementService).settle(2L);
        order.verify(refundService).refund(3L);
        order.verify(refundService).refund(4L);
    }

    @Test
    void continuesWithSettlementAndRefundWhenLiveLookupFails() {
        given(liveBettingDataProvider.findLiveMatches())
                .willThrow(new IllegalStateException("live lookup failed"));
        given(eventRepository.findIdsReadyToSettle()).willReturn(List.of(1L));
        given(eventRepository.findIdsCancelledWithPlacedBets()).willReturn(List.of(2L));

        scheduler.process();

        verify(settlementService).settle(1L);
        verify(refundService).refund(2L);
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
                List.of("team-a", "team-b"),
                List.of(),
                false
        );
    }
}
