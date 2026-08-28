package com.clutch.coupon.claim.result;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultStatus;
import com.clutch.coupon.statistics.service.CouponIssueStatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 생성 결과 처리 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponIssueResultServiceTest {

    private static final Long CLAIM_ID = 10L;
    private static final Instant OCCURRED_AT =
            Instant.parse("2026-08-17T00:00:00Z");

    @Mock
    private CouponClaimRequestRepository
            claimRequestRepository;

    @Mock
    private CouponClaimRequest claimRequest;

    @Mock
    private CouponIssueStatisticsService statisticsService;

    @InjectMocks
    private CouponIssueResultService issueResultService;

    /**
     * 쿠폰 생성 성공 처리 검증
     */
    @Test
    void handleSucceedsClaim() {
        // given
        when(claimRequestRepository.findById(CLAIM_ID))
                .thenReturn(Optional.of(claimRequest));
        when(claimRequest.isPending()).thenReturn(true);

        CouponIssueResultEvent event =
                resultEvent(
                        CouponIssueResultStatus.SUCCEEDED,
                        null
                );

        LocalDateTime completedAt =
                LocalDateTime.ofInstant(
                        OCCURRED_AT,
                        ZoneOffset.UTC
                );

        // when
        issueResultService.handle(event);

        // then
        verify(claimRequest).succeed(completedAt);
        verify(claimRequest, never())
                .fail(
                        any(),
                        any(LocalDateTime.class)
                );
        verify(statisticsService).recordResult(event, claimRequest);
    }

    /**
     * 쿠폰 생성 실패 처리 검증
     */
    @Test
    void handleFailsClaim() {
        // given
        when(claimRequestRepository.findById(CLAIM_ID))
                .thenReturn(Optional.of(claimRequest));
        when(claimRequest.isPending()).thenReturn(true);

        CouponIssueResultEvent event =
                resultEvent(
                        CouponIssueResultStatus.FAILED,
                        "쿠폰 생성 실패"
                );

        LocalDateTime completedAt =
                LocalDateTime.ofInstant(
                        OCCURRED_AT,
                        ZoneOffset.UTC
                );

        // when
        issueResultService.handle(event);

        // then
        verify(claimRequest).fail(
                "쿠폰 생성 실패",
                completedAt
        );
        verify(claimRequest, never())
                .succeed(any(LocalDateTime.class));
        verify(statisticsService).recordResult(event, claimRequest);
    }

    /**
     * 중복 결과 이벤트 무시 검증
     */
    @Test
    void handleIgnoresCompletedClaim() {
        // given
        when(claimRequestRepository.findById(CLAIM_ID))
                .thenReturn(Optional.of(claimRequest));
        when(claimRequest.isPending()).thenReturn(false);

        CouponIssueResultEvent event =
                resultEvent(
                        CouponIssueResultStatus.SUCCEEDED,
                        null
                );

        // when
        issueResultService.handle(event);

        // then
        verify(claimRequest, never())
                .succeed(any(LocalDateTime.class));
        verify(claimRequest, never())
                .fail(
                        any(),
                        any(LocalDateTime.class)
                );
        verify(statisticsService).recordResult(event, claimRequest);
    }

    /**
     * 미존재 발급 요청 검증
     */
    @Test
    void handleFailsWhenClaimDoesNotExist() {
        // given
        when(claimRequestRepository.findById(CLAIM_ID))
                .thenReturn(Optional.empty());

        CouponIssueResultEvent event =
                resultEvent(
                        CouponIssueResultStatus.SUCCEEDED,
                        null
                );

        // when & then
        assertThatThrownBy(() ->
                issueResultService.handle(event)
        ).isInstanceOf(CouponClaimException.class);

        verifyNoInteractions(statisticsService);
    }

    /**
     * 미지원 이벤트 버전 검증
     */
    @Test
    void handleRejectsUnsupportedVersion() {
        // given
        CouponIssueResultEvent event =
                new CouponIssueResultEvent(
                        2,
                        "message-1",
                        CLAIM_ID,
                        100L,
                        CouponIssueResultStatus.SUCCEEDED,
                        null,
                        OCCURRED_AT
                );

        // when & then
        assertThatThrownBy(() ->
                issueResultService.handle(event)
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(claimRequestRepository);
        verifyNoInteractions(statisticsService);
    }

    private CouponIssueResultEvent resultEvent(
            CouponIssueResultStatus status,
            String failureReason
    ) {
        return new CouponIssueResultEvent(
                1,
                "message-1",
                CLAIM_ID,
                status == CouponIssueResultStatus.SUCCEEDED
                        ? 100L
                        : null,
                status,
                failureReason,
                OCCURRED_AT
        );
    }
}
