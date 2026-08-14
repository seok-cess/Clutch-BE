package com.clutch.betting.scheduler;

import com.clutch.betting.live.LiveBettingDataProvider;
import com.clutch.betting.live.LiveBettingDataProvider.LiveMatchSnapshot;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.service.BetRefundService;
import com.clutch.betting.service.BetSettlementService;
import com.clutch.betting.service.BettingEventSynchronizationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 라이브 상태 동기화 이후 정산과 환불을 순서대로 실행하는 배팅 주기 작업이다. */
@Component
@RequiredArgsConstructor
public class BettingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BettingScheduler.class);

    private final LiveBettingDataProvider liveBettingDataProvider;
    private final BettingEventRepository eventRepository;
    private final BettingEventSynchronizationService synchronizationService;
    private final BetSettlementService settlementService;
    private final BetRefundService refundService;

    /** 라이브 상태를 반영한 뒤 새로 확정된 정산과 취소 환불을 같은 주기에서 처리한다. */
    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    public void process() {
        synchronizeEvents();
        settleReadyEvents();
        refundCancelledEvents();
    }

    /** 라이브 매치별 동기화 실패를 격리하고 다음 매치 처리를 계속한다. */
    private void synchronizeEvents() {
        try {
            for (LiveMatchSnapshot liveMatch : liveBettingDataProvider.findLiveMatches()) {
                synchronizeMatch(liveMatch);
            }
        } catch (RuntimeException exception) {
            log.warn("라이브 배팅 대상 조회 실패: {}", exception.toString());
        }
    }

    /**
     * 한 라이브 매치를 동기화하고 중복 생성과 처리 오류를 현재 매치에 한정한다.
     *
     * @param liveMatch 동기화할 라이브 매치 스냅샷
     */
    private void synchronizeMatch(LiveMatchSnapshot liveMatch) {
        try {
            synchronizationService.synchronizeMatch(liveMatch);
        } catch (DataIntegrityViolationException exception) {
            log.debug("중복 캐시 감지로 이미 생성된 배팅 이벤트를 유지합니다: {}",
                    liveMatch.externalMatchId());
        } catch (RuntimeException exception) {
            log.warn("배팅 이벤트 캐시 동기화 실패 (matchId={}): {}",
                    liveMatch.externalMatchId(), exception.toString());
        }
    }

    /** 정산 대상 조회와 이벤트별 실패를 격리하고 가능한 이벤트를 계속 처리한다. */
    private void settleReadyEvents() {
        try {
            for (Long eventId : eventRepository.findIdsReadyToSettle()) {
                try {
                    settlementService.settle(eventId);
                } catch (RuntimeException exception) {
                    log.warn("배팅 이벤트 정산 실패 (eventId={}): {}",
                            eventId, exception.toString());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("배팅 정산 대상 조회 실패: {}", exception.toString());
        }
    }

    /** 환불 대상 조회와 이벤트별 실패를 격리하고 가능한 이벤트를 계속 처리한다. */
    private void refundCancelledEvents() {
        try {
            for (Long eventId : eventRepository.findIdsCancelledWithPlacedBets()) {
                try {
                    refundService.refund(eventId);
                } catch (RuntimeException exception) {
                    log.warn("취소된 배팅 이벤트 환불 실패 (eventId={}): {}",
                            eventId, exception.toString());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("배팅 환불 대상 조회 실패: {}", exception.toString());
        }
    }
}
