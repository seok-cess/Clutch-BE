package com.clutch.coupon.claim.result;

import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 생성 결과 Kafka Consumer 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponIssueResultConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CouponIssueResultService issueResultService;

    @InjectMocks
    private CouponIssueResultConsumer issueResultConsumer;

    /**
     * 쿠폰 생성 결과 이벤트 처리 검증
     */
    @Test
    void consumeHandlesIssueResult()
            throws Exception {
        // given
        String payload =
                "{\"claimId\":10,\"status\":\"SUCCEEDED\"}";

        CouponIssueResultEvent event =
                new CouponIssueResultEvent(
                        1,
                        "message-1",
                        10L,
                        100L,
                        CouponIssueResultStatus.SUCCEEDED,
                        null,
                        Instant.parse(
                                "2026-08-17T00:00:00Z"
                        )
                );

        when(objectMapper.readValue(
                payload,
                CouponIssueResultEvent.class
        )).thenReturn(event);

        // when
        issueResultConsumer.consume(payload);

        // then
        verify(issueResultService).handle(event);
    }

    /**
     * 잘못된 JSON 이벤트 처리 실패 검증
     */
    @Test
    void consumeFailsWhenPayloadIsInvalid()
            throws Exception {
        // given
        String payload = "잘못된 JSON";

        when(objectMapper.readValue(
                payload,
                CouponIssueResultEvent.class
        )).thenThrow(
                new IllegalArgumentException(
                        "JSON 변환 실패"
                )
        );

        // when & then
        assertThatThrownBy(() ->
                issueResultConsumer.consume(payload)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "쿠폰 생성 결과 이벤트 처리 실패"
                );

        verifyNoInteractions(issueResultService);
    }
}