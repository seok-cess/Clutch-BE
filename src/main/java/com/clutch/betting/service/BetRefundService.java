package com.clutch.betting.service;

import com.clutch.betting.repository.BettingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
/** 환불 대상 이벤트를 탐색하고 이벤트별 독립 트랜잭션 처리를 조율한다. */
public class BetRefundService {

    private static final Logger log = LoggerFactory.getLogger(BetRefundService.class);

    private final BettingEventRepository eventRepository;
    private final BetRefundProcessor refundProcessor;

    /** 환불 대상 조회 저장소와 개별 처리기를 주입받는다. */
    public BetRefundService(
            BettingEventRepository eventRepository,
            BetRefundProcessor refundProcessor
    ) {
        this.eventRepository = eventRepository;
        this.refundProcessor = refundProcessor;
    }

    /** 한 이벤트의 실패가 나머지 환불을 막지 않도록 대상별 처리를 격리한다. */
    public void refundCancelledEvents() {
        for (Long eventId : eventRepository.findIdsCancelledWithPlacedBets()) {
            try {
                refundProcessor.refund(eventId);
            } catch (RuntimeException exception) {
                log.warn("취소된 배팅 이벤트 환불 실패 (eventId={}): {}",
                        eventId, exception.toString());
            }
        }
    }
}
