package com.clutch.betting.service;

import com.clutch.betting.repository.BettingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 정산 대상 이벤트를 탐색하고 이벤트별 독립 트랜잭션 처리를 조율한다. */
@Service
public class BetSettlementService {

    private static final Logger log = LoggerFactory.getLogger(BetSettlementService.class);

    private final BettingEventRepository eventRepository;
    private final BetSettlementProcessor settlementProcessor;

    /**
     * 정산 대상 조회 저장소와 개별 처리기를 주입받는다.
     *
     * @param eventRepository 배팅 이벤트 저장소
     * @param settlementProcessor 이벤트 단위 정산 처리기
     */
    public BetSettlementService(
            BettingEventRepository eventRepository,
            BetSettlementProcessor settlementProcessor
    ) {
        this.eventRepository = eventRepository;
        this.settlementProcessor = settlementProcessor;
    }

    /** 한 이벤트의 실패가 다음 이벤트 정산을 막지 않도록 대상별 처리를 격리한다. */
    public void settleReadyEvents() {
        for (Long eventId : eventRepository.findIdsReadyToSettle()) {
            try {
                settlementProcessor.settle(eventId);
            } catch (RuntimeException exception) {
                log.warn("배팅 이벤트 정산 실패 (eventId={}): {}", eventId, exception.toString());
            }
        }
    }
}
