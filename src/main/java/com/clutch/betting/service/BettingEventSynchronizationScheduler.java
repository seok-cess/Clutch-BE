package com.clutch.betting.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BettingEventSynchronizationScheduler {

    private final BettingEventSynchronizationService synchronizationService;

    public BettingEventSynchronizationScheduler(
            BettingEventSynchronizationService synchronizationService
    ) {
        this.synchronizationService = synchronizationService;
    }

    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    public void synchronize() {
        synchronizationService.synchronize();
    }
}
