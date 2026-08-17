package com.clutch.coupon.claim.result;

import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 쿠폰 생성 결과 Kafka Consumer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueResultConsumer {

    private final ObjectMapper objectMapper;
    private final CouponIssueResultService issueResultService;

    /**
     * 쿠폰 생성 결과 이벤트 소비
     *
     * @param payload 쿠폰 생성 결과 JSON
     */
    @KafkaListener(
            id = "couponIssueResultConsumer",
            topics =
                    "${coupon.claim.kafka.issue-result-topic:"
                            + CouponKafkaTopics.ISSUE_RESULT
                            + "}",
            groupId =
                    "${coupon.claim.kafka.issue-result-group:"
                            + "clutch-coupon-issue-result}"
    )
    public void consume(
            String payload
    ) {
        try {
            CouponIssueResultEvent event =
                    objectMapper.readValue(
                            payload,
                            CouponIssueResultEvent.class
                    );

            issueResultService.handle(event);

            log.info(
                    "쿠폰 생성 결과 처리 완료: claimId={}, status={}",
                    event.claimId(),
                    event.status()
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "쿠폰 생성 결과 이벤트 처리 실패",
                    exception
            );
        }
    }
}