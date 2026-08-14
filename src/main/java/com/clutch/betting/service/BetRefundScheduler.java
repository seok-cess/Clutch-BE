package com.clutch.betting.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BetRefundScheduler {

    private final BetRefundService refundService;

    public BetRefundScheduler(BetRefundService refundService) {
        this.refundService = refundService;
    }

    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    public void refundCancelledEvents() {
        refundService.refundCancelledEvents();
    }
}
