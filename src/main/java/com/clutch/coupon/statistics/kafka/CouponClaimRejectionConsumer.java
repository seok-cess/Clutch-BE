package com.clutch.coupon.statistics.kafka;

import com.clutch.coupon.contract.kafka.CouponClaimRejectedEvent;
import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import com.clutch.coupon.statistics.service.CouponClaimRejectionStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 쿠폰 신청 거절 이벤트를 관리자 일별 통계 원본으로 저장한다. */
@Component
@RequiredArgsConstructor
public class CouponClaimRejectionConsumer {

    private final ObjectMapper objectMapper;
    private final CouponClaimRejectionStatisticsService statisticsService;

    @KafkaListener(
            id = "couponClaimRejectionConsumer",
            topics = "${coupon.claim.kafka.rejected-topic:"
                    + CouponKafkaTopics.CLAIM_REJECTED
                    + "}",
            groupId = "${coupon.claim.kafka.rejected-group:"
                    + "clutch-coupon-claim-rejected}"
    )
    public void consume(String payload) {
        try {
            CouponClaimRejectedEvent event = objectMapper.readValue(
                    payload,
                    CouponClaimRejectedEvent.class
            );
            statisticsService.record(event);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "쿠폰 신청 거절 통계 이벤트 처리 실패",
                    exception
            );
        }
    }
}
