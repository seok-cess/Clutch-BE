package com.clutch.coupon.statistics.service;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.statistics.api.dto.AdminCouponIssueStatisticsEventResponse;
import com.clutch.coupon.statistics.api.dto.AdminCouponIssueStatisticsResponse;
import com.clutch.coupon.statistics.api.dto.AdminCouponIssueStatisticsSummaryResponse;
import com.clutch.coupon.statistics.kafka.CouponIssueResultDltRecord;
import com.clutch.coupon.statistics.repository.CouponIssueProcessingError;
import com.clutch.coupon.statistics.repository.CouponIssueStatisticsEventRow;
import com.clutch.coupon.statistics.repository.CouponIssueStatisticsRepository;
import com.clutch.coupon.statistics.repository.CouponIssueStatisticsSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.INVALID_ADMIN_STATISTICS_QUERY;

/** 쿠폰 발급 결과 집계와 관리자 대시보드 조회를 처리한다. */
@Service
@RequiredArgsConstructor
public class CouponIssueStatisticsService {

    private static final int SUPPORTED_EVENT_VERSION = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_MESSAGE_ID_LENGTH = 100;
    private static final int MAX_EXCEPTION_TYPE_LENGTH = 500;
    private static final int MAX_EXCEPTION_MESSAGE_LENGTH = 1000;
    private static final int MAX_PAYLOAD_LENGTH = 4000;

    private final CouponIssueStatisticsRepository statisticsRepository;
    private final CouponClaimRequestRepository claimRequestRepository;
    private final ObjectMapper objectMapper;

    /** 발급 결과 메시지를 messageId 기준으로 한 번만 집계한다. */
    @Transactional
    public boolean recordResult(
            CouponIssueResultEvent event,
            CouponClaimRequest claimRequest
    ) {
        validateResultEvent(event);
        if (!event.claimId().equals(claimRequest.getId())) {
            throw new IllegalArgumentException(
                    "쿠폰 결과 이벤트의 발급 요청 ID가 일치하지 않습니다."
            );
        }
        return statisticsRepository.recordResult(event, claimRequest);
    }

    /** DLT에 도달한 결과 메시지를 원본 토픽 좌표 기준으로 한 번만 기록한다. */
    @Transactional
    public boolean recordProcessingError(CouponIssueResultDltRecord dltRecord) {
        CouponIssueResultEvent event = parseResultEvent(dltRecord.payload());
        Long claimId = event == null
                ? parsePositiveLong(dltRecord.key())
                : event.claimId();
        CouponClaimRequest claimRequest = claimId == null
                ? null
                : claimRequestRepository.findById(claimId).orElse(null);

        CouponIssueProcessingError error = new CouponIssueProcessingError(
                truncate(dltRecord.originalConsumerGroup(), 200),
                truncate(dltRecord.originalTopic(), 200),
                dltRecord.originalPartition(),
                dltRecord.originalOffset(),
                event == null
                        ? null
                        : truncate(event.messageId(), MAX_MESSAGE_ID_LENGTH),
                claimId,
                claimRequest == null ? null : claimRequest.getCouponEventId(),
                truncate(dltRecord.exceptionType(), MAX_EXCEPTION_TYPE_LENGTH),
                truncate(
                        dltRecord.exceptionMessage(),
                        MAX_EXCEPTION_MESSAGE_LENGTH
                ),
                truncate(dltRecord.payload(), MAX_PAYLOAD_LENGTH),
                dltRecord.originalOccurredAt()
        );
        return statisticsRepository.recordProcessingError(error);
    }

    /** 전체 요약과 최근 쿠폰 이벤트별 통계를 반환한다. */
    @Transactional(readOnly = true)
    public AdminCouponIssueStatisticsResponse findAll(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CouponClaimException(INVALID_ADMIN_STATISTICS_QUERY);
        }

        CouponIssueStatisticsSummaryRow summary =
                statisticsRepository.findSummary();
        return new AdminCouponIssueStatisticsResponse(
                new AdminCouponIssueStatisticsSummaryResponse(
                        summary.successCount() + summary.failureCount(),
                        summary.successCount(),
                        summary.failureCount(),
                        summary.processingErrorCount(),
                        summary.unassignedErrorCount(),
                        summary.lastProcessedAt()
                ),
                statisticsRepository.findEvents(size).stream()
                        .map(this::toEventResponse)
                        .toList()
        );
    }

    private AdminCouponIssueStatisticsEventResponse toEventResponse(
            CouponIssueStatisticsEventRow row
    ) {
        return new AdminCouponIssueStatisticsEventResponse(
                row.couponEventId(),
                row.eventName(),
                row.triggerType(),
                row.eventStatus(),
                row.successCount() + row.failureCount(),
                row.successCount(),
                row.failureCount(),
                row.processingErrorCount(),
                row.lastResultAt(),
                row.lastErrorAt()
        );
    }

    private void validateResultEvent(CouponIssueResultEvent event) {
        if (event == null
                || event.eventVersion() != SUPPORTED_EVENT_VERSION
                || event.messageId() == null
                || event.messageId().isBlank()
                || event.messageId().length() > MAX_MESSAGE_ID_LENGTH
                || event.claimId() == null
                || event.claimId() <= 0
                || event.status() == null
                || event.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "유효하지 않은 쿠폰 결과 이벤트입니다."
            );
        }
    }

    private CouponIssueResultEvent parseResultEvent(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, CouponIssueResultEvent.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parsePositiveLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
