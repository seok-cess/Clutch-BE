package com.clutch.betting.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
/** 설정된 주기마다 라이브 캐시와 배팅 이벤트 동기화를 실행한다. */
public class BettingEventSynchronizationScheduler {

    private final BettingEventSynchronizationService synchronizationService;

    /** 전체 라이브 매치 동기화 서비스를 주입받는다. */
    public BettingEventSynchronizationScheduler(
            BettingEventSynchronizationService synchronizationService
    ) {
        this.synchronizationService = synchronizationService;
    }

    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    /** 이전 실행 종료 후 설정된 간격으로 라이브 상태 동기화를 요청한다. */
    public void synchronize() {
        synchronizationService.synchronize();
    }
}
