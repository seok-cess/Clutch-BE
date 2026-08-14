package com.clutch.betting.scheduler;

import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.service.BetRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 설정된 주기마다 취소 이벤트 환불 탐색을 실행한다. */
@Component
public class BetRefundScheduler {

    private static final Logger log = LoggerFactory.getLogger(BetRefundScheduler.class);

    private final BettingEventRepository eventRepository;
    private final BetRefundService refundService;

    /**
     * 환불 대상 저장소와 이벤트 단위 환불 서비스를 주입받는다.
     *
     * @param eventRepository 배팅 이벤트 저장소
     * @param refundService 이벤트 단위 환불 서비스
     */
    public BetRefundScheduler(
            BettingEventRepository eventRepository,
            BetRefundService refundService
    ) {
        this.eventRepository = eventRepository;
        this.refundService = refundService;
    }

    /** 이전 실행 종료 후 설정된 간격으로 취소 배팅 환불을 요청한다. */
    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    public void refundCancelledEvents() {
        for (Long eventId : eventRepository.findIdsCancelledWithPlacedBets()) {
            try {
                refundService.refund(eventId);
            } catch (RuntimeException exception) {
                log.warn("취소된 배팅 이벤트 환불 실패 (eventId={}): {}",
                        eventId, exception.toString());
            }
        }
    }
}
