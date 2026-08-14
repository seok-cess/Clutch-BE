package com.clutch.betting.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BetSettlementScheduler {

    private final BetSettlementService settlementService;

    public BetSettlementScheduler(BetSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    public void settleReadyEvents() {
        settlementService.settleReadyEvents();
    }
}
