package com.clutch.betting.service;

import com.clutch.betting.repository.BettingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BetSettlementService {

    private static final Logger log = LoggerFactory.getLogger(BetSettlementService.class);

    private final BettingEventRepository eventRepository;
    private final BetSettlementProcessor settlementProcessor;

    public BetSettlementService(
            BettingEventRepository eventRepository,
            BetSettlementProcessor settlementProcessor
    ) {
        this.eventRepository = eventRepository;
        this.settlementProcessor = settlementProcessor;
    }

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
