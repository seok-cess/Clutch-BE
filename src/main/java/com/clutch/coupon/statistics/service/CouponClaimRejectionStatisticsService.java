package com.clutch.coupon.statistics.service;

import com.clutch.coupon.contract.kafka.CouponClaimRejectedEvent;
import com.clutch.coupon.statistics.repository.CouponClaimRejectionStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 쿠폰 신청 거절 이벤트를 검증하고 멱등 저장한다. */
@Service
@RequiredArgsConstructor
public class CouponClaimRejectionStatisticsService {

    private static final int SUPPORTED_EVENT_VERSION = 1;
    private static final int MAX_MESSAGE_ID_LENGTH = 100;
    private static final int MAX_REASON_LENGTH = 100;

    private final CouponClaimRejectionStatisticsRepository repository;

    @Transactional
    public boolean record(CouponClaimRejectedEvent event) {
        validate(event);
        return repository.record(event);
    }

    private void validate(CouponClaimRejectedEvent event) {
        if (event == null
                || event.eventVersion() != SUPPORTED_EVENT_VERSION
                || event.messageId() == null
                || event.messageId().isBlank()
                || event.messageId().length() > MAX_MESSAGE_ID_LENGTH
                || event.couponEventId() == null
                || event.couponEventOccurrenceId() == null
                || event.reason() == null
                || event.reason().isBlank()
                || event.reason().length() > MAX_REASON_LENGTH
                || event.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "유효하지 않은 쿠폰 신청 거절 이벤트입니다."
            );
        }
    }
}
