package com.clutch.betting.service;

import com.clutch.betting.repository.BettingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BetRefundService {

    private static final Logger log = LoggerFactory.getLogger(BetRefundService.class);

    private final BettingEventRepository eventRepository;
    private final BetRefundProcessor refundProcessor;

    public BetRefundService(
            BettingEventRepository eventRepository,
            BetRefundProcessor refundProcessor
    ) {
        this.eventRepository = eventRepository;
        this.refundProcessor = refundProcessor;
    }

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
