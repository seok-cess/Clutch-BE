package com.clutch.betting.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
/** 설정된 주기마다 정산 가능한 이벤트 탐색을 실행한다. */
public class BetSettlementScheduler {

    private final BetSettlementService settlementService;

    /** 정산 대상 탐색 서비스를 주입받는다. */
    public BetSettlementScheduler(BetSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    /** 이전 실행 종료 후 설정된 간격으로 배팅 정산을 요청한다. */
    public void settleReadyEvents() {
        settlementService.settleReadyEvents();
    }
}
