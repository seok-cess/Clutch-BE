package com.clutch.betting.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 설정된 주기마다 취소 이벤트 환불 탐색을 실행한다. */
@Component
public class BetRefundScheduler {

    private final BetRefundService refundService;

    /**
     * 실제 환불 탐색 로직을 수행할 서비스를 주입받는다.
     *
     * @param refundService 취소 이벤트 환불 탐색 서비스
     */
    public BetRefundScheduler(BetRefundService refundService) {
        this.refundService = refundService;
    }

    /** 이전 실행 종료 후 설정된 간격으로 취소 배팅 환불을 요청한다. */
    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    public void refundCancelledEvents() {
        refundService.refundCancelledEvents();
    }
}
