package com.clutch.coupon.statistics.service;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultStatus;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.statistics.api.dto.AdminCouponIssueStatisticsResponse;
import com.clutch.coupon.statistics.kafka.CouponIssueResultDltRecord;
import com.clutch.coupon.statistics.repository.CouponIssueProcessingError;
import com.clutch.coupon.statistics.repository.CouponIssueStatisticsEventRow;
import com.clutch.coupon.statistics.repository.CouponIssueStatisticsRepository;
import com.clutch.coupon.statistics.repository.CouponIssueStatisticsSummaryRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponIssueStatisticsServiceTest {

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-08-28T05:00:00Z");

    @Mock
    private CouponIssueStatisticsRepository statisticsRepository;

    @Mock
    private CouponClaimRequestRepository claimRequestRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CouponClaimRequest claimRequest;

    private CouponIssueStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new CouponIssueStatisticsService(
                statisticsRepository,
                claimRequestRepository,
                objectMapper
        );
    }

    @Test
    void 발급_결과를_messageId_기준으로_Repository에_위임한다() {
        CouponIssueResultEvent event = resultEvent("message-1", 10L);
        when(claimRequest.getId()).thenReturn(10L);
        when(statisticsRepository.recordResult(event, claimRequest))
                .thenReturn(true);

        boolean recorded = service.recordResult(event, claimRequest);

        assertThat(recorded).isTrue();
        verify(statisticsRepository).recordResult(event, claimRequest);
    }

    @Test
    void 발급_결과와_Claim_ID가_다르면_집계하지_않는다() {
        CouponIssueResultEvent event = resultEvent("message-1", 10L);
        when(claimRequest.getId()).thenReturn(11L);

        assertThatThrownBy(() -> service.recordResult(event, claimRequest))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(statisticsRepository);
    }

    @Test
    void DLT_이벤트에서_Claim과_쿠폰_이벤트를_식별해_오류를_기록한다()
            throws Exception {
        CouponIssueResultEvent event = resultEvent("message-2", 20L);
        CouponIssueResultDltRecord dltRecord = dltRecord("20", "payload");
        when(objectMapper.readValue("payload", CouponIssueResultEvent.class))
                .thenReturn(event);
        when(claimRequestRepository.findById(20L))
                .thenReturn(Optional.of(claimRequest));
        when(claimRequest.getCouponEventId()).thenReturn(30L);
        when(statisticsRepository.recordProcessingError(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(true);

        boolean recorded = service.recordProcessingError(dltRecord);

        assertThat(recorded).isTrue();
        ArgumentCaptor<CouponIssueProcessingError> captor =
                ArgumentCaptor.forClass(CouponIssueProcessingError.class);
        verify(statisticsRepository).recordProcessingError(captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo("message-2");
        assertThat(captor.getValue().claimId()).isEqualTo(20L);
        assertThat(captor.getValue().couponEventId()).isEqualTo(30L);
        assertThat(captor.getValue().originalTopic())
                .isEqualTo("coupon.issue.result");
    }

    @Test
    void DLT_페이로드가_깨져도_Key로_Claim을_식별한다() throws Exception {
        CouponIssueResultDltRecord dltRecord = dltRecord("40", "broken");
        when(objectMapper.readValue("broken", CouponIssueResultEvent.class))
                .thenThrow(new IllegalArgumentException("broken"));
        when(claimRequestRepository.findById(40L))
                .thenReturn(Optional.of(claimRequest));
        when(claimRequest.getCouponEventId()).thenReturn(50L);

        service.recordProcessingError(dltRecord);

        ArgumentCaptor<CouponIssueProcessingError> captor =
                ArgumentCaptor.forClass(CouponIssueProcessingError.class);
        verify(statisticsRepository).recordProcessingError(captor.capture());
        assertThat(captor.getValue().messageId()).isNull();
        assertThat(captor.getValue().claimId()).isEqualTo(40L);
        assertThat(captor.getValue().couponEventId()).isEqualTo(50L);
    }

    @Test
    void 전체와_이벤트별_통계를_관리자_응답으로_변환한다() {
        LocalDateTime lastProcessedAt =
                LocalDateTime.of(2026, 8, 28, 14, 0);
        when(statisticsRepository.findSummary()).thenReturn(
                new CouponIssueStatisticsSummaryRow(
                        8,
                        2,
                        3,
                        1,
                        lastProcessedAt
                )
        );
        when(statisticsRepository.findEvents(20)).thenReturn(List.of(
                new CouponIssueStatisticsEventRow(
                        100L,
                        "펜타킬 이벤트",
                        "PENTA_KILL",
                        CouponEventStatus.OPEN,
                        8,
                        2,
                        2,
                        lastProcessedAt,
                        lastProcessedAt
                )
        ));

        AdminCouponIssueStatisticsResponse response = service.findAll(20);

        assertThat(response.summary().totalResultCount()).isEqualTo(10);
        assertThat(response.summary().successCount()).isEqualTo(8);
        assertThat(response.summary().failureCount()).isEqualTo(2);
        assertThat(response.summary().processingErrorCount()).isEqualTo(3);
        assertThat(response.summary().unassignedErrorCount()).isEqualTo(1);
        assertThat(response.events()).singleElement().satisfies(event -> {
            assertThat(event.couponEventId()).isEqualTo(100L);
            assertThat(event.eventName()).isEqualTo("펜타킬 이벤트");
            assertThat(event.totalResultCount()).isEqualTo(10);
            assertThat(event.processingErrorCount()).isEqualTo(2);
        });
    }

    @Test
    void 조회_크기가_범위를_벗어나면_거부한다() {
        assertThatThrownBy(() -> service.findAll(101))
                .isInstanceOf(CouponClaimException.class);

        verifyNoInteractions(statisticsRepository);
    }

    private CouponIssueResultEvent resultEvent(
            String messageId,
            Long claimId
    ) {
        return new CouponIssueResultEvent(
                1,
                messageId,
                claimId,
                100L,
                CouponIssueResultStatus.SUCCEEDED,
                null,
                OCCURRED_AT
        );
    }

    private CouponIssueResultDltRecord dltRecord(
            String key,
            String payload
    ) {
        return new CouponIssueResultDltRecord(
                key,
                payload,
                "clutch-coupon-issue-result",
                "coupon.issue.result",
                1,
                10,
                "java.lang.IllegalStateException",
                "통계 저장 실패",
                LocalDateTime.of(2026, 8, 28, 14, 0)
        );
    }
}
