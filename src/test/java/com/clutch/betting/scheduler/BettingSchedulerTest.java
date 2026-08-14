package com.clutch.betting.scheduler;

import com.clutch.betting.live.LiveBettingDataProvider;
import com.clutch.betting.live.LiveBettingDataProvider.LiveMatchSnapshot;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.service.BetRefundService;
import com.clutch.betting.service.BetSettlementService;
import com.clutch.betting.service.BettingEventSynchronizationService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BettingSchedulerTest {

    @Test
    void continuesRefundAfterIndividualFailure() {
        BettingEventRepository eventRepository = mock(BettingEventRepository.class);
        BetRefundService refundService = mock(BetRefundService.class);
        BetRefundScheduler scheduler = new BetRefundScheduler(eventRepository, refundService);
        given(eventRepository.findIdsCancelledWithPlacedBets()).willReturn(List.of(1L, 2L));
        willThrow(new IllegalStateException("first refund failed"))
                .given(refundService).refund(1L);

        scheduler.refundCancelledEvents();

        verify(refundService).refund(1L);
        verify(refundService).refund(2L);
    }

    @Test
    void continuesSettlementAfterIndividualFailure() {
        BettingEventRepository eventRepository = mock(BettingEventRepository.class);
        BetSettlementService settlementService = mock(BetSettlementService.class);
        BetSettlementScheduler scheduler = new BetSettlementScheduler(
                eventRepository,
                settlementService
        );
        given(eventRepository.findIdsReadyToSettle()).willReturn(List.of(1L, 2L));
        willThrow(new IllegalStateException("first settlement failed"))
                .given(settlementService).settle(1L);

        scheduler.settleReadyEvents();

        verify(settlementService).settle(1L);
        verify(settlementService).settle(2L);
    }

    @Test
    void continuesSynchronizationAfterDuplicateEvent() {
        LiveBettingDataProvider liveBettingDataProvider = mock(LiveBettingDataProvider.class);
        BettingEventSynchronizationService synchronizationService =
                mock(BettingEventSynchronizationService.class);
        BettingEventSynchronizationScheduler scheduler = new BettingEventSynchronizationScheduler(
                liveBettingDataProvider,
                synchronizationService
        );
        LiveMatchSnapshot first = snapshot("match-1");
        LiveMatchSnapshot second = snapshot("match-2");
        given(liveBettingDataProvider.findLiveMatches()).willReturn(List.of(first, second));
        willThrow(new DataIntegrityViolationException("duplicate event"))
                .given(synchronizationService).synchronizeMatch(first);

        scheduler.synchronize();

        verify(synchronizationService).synchronizeMatch(first);
        verify(synchronizationService).synchronizeMatch(second);
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
