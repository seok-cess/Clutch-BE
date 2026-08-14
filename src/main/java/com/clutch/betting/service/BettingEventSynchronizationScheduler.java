package com.clutch.betting.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 설정된 주기마다 라이브 캐시와 배팅 이벤트 동기화를 실행한다. */
@Component
public class BettingEventSynchronizationScheduler {

    private final BettingEventSynchronizationService synchronizationService;

    /**
     * 전체 라이브 매치 동기화 서비스를 주입받는다.
     *
     * @param synchronizationService 라이브 매치 동기화 서비스
     */
    public BettingEventSynchronizationScheduler(
            BettingEventSynchronizationService synchronizationService
    ) {
        this.synchronizationService = synchronizationService;
    }

    /** 이전 실행 종료 후 설정된 간격으로 라이브 상태 동기화를 요청한다. */
    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    public void synchronize() {
        synchronizationService.synchronize();
    }
}
