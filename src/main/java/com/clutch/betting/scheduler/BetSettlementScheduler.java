package com.clutch.betting.scheduler;

import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.service.BetSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 설정된 주기마다 정산 가능한 이벤트 탐색을 실행한다. */
@Component
public class BetSettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(BetSettlementScheduler.class);

    private final BettingEventRepository eventRepository;
    private final BetSettlementService settlementService;

    /**
     * 정산 대상 저장소와 이벤트 단위 정산 서비스를 주입받는다.
     *
     * @param eventRepository 배팅 이벤트 저장소
     * @param settlementService 이벤트 단위 정산 서비스
     */
    public BetSettlementScheduler(
            BettingEventRepository eventRepository,
            BetSettlementService settlementService
    ) {
        this.eventRepository = eventRepository;
        this.settlementService = settlementService;
    }

    /** 이전 실행 종료 후 설정된 간격으로 배팅 정산을 요청한다. */
    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    public void settleReadyEvents() {
        for (Long eventId : eventRepository.findIdsReadyToSettle()) {
            try {
                settlementService.settle(eventId);
            } catch (RuntimeException exception) {
                log.warn("배팅 이벤트 정산 실패 (eventId={}): {}", eventId, exception.toString());
            }
        }
    }
}
