package com.clutch.coupon.integrity.service;

import com.clutch.coupon.integrity.api.dto.CouponIntegrityDetailResponse;
import com.clutch.coupon.integrity.api.dto.CouponIntegrityListResponse;
import com.clutch.coupon.integrity.api.dto.CouponIntegrityResultResponse;
import com.clutch.coupon.integrity.api.dto.CouponIntegrityStartResponse;
import com.clutch.coupon.integrity.api.dto.CouponIntegritySummaryResponse;
import com.clutch.coupon.integrity.domain.CouponIntegrityCheck;
import com.clutch.coupon.integrity.repository.CouponIntegrityCheckRepository;
import com.clutch.coupon.integrity.repository.CouponIntegrityCheckResultRepository;
import com.clutch.coupon.integrity.repository.MySqlCouponIntegrityExecutionLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CouponIntegrityCheckService {
    private final CouponIntegrityCheckRepository checkRepository;
    private final CouponIntegrityCheckResultRepository resultRepository;
    private final CouponIntegrityHistoryService historyService;
    private final CouponIntegrityExecutionService executionService;
    private final MySqlCouponIntegrityExecutionLock executionLock;
    private final Executor executor;
    private final Duration recoveryGrace;

    public CouponIntegrityCheckService(
            CouponIntegrityCheckRepository checkRepository,
            CouponIntegrityCheckResultRepository resultRepository,
            CouponIntegrityHistoryService historyService,
            CouponIntegrityExecutionService executionService,
            MySqlCouponIntegrityExecutionLock executionLock,
            @Qualifier("couponIntegrityExecutor") Executor executor,
            @Value("${coupon.integrity-check.recovery-grace:PT5M}") Duration recoveryGrace
    ) {
        this.checkRepository = checkRepository;
        this.resultRepository = resultRepository;
        this.historyService = historyService;
        this.executionService = executionService;
        this.executionLock = executionLock;
        this.executor = executor;
        this.recoveryGrace = recoveryGrace;
    }

    public CouponIntegrityStartResponse start(Long adminId) {
        LocalDateTime startedAt = LocalDateTime.now();
        AtomicReference<CouponIntegrityCheck> created = new AtomicReference<>();
        boolean acquired;
        try {
            acquired = executionLock.tryExecute(() -> created.set(historyService.create(
                    adminId, startedAt, startedAt.minus(recoveryGrace)
            )));
        } catch (CouponIntegrityException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            failCreatedCheck(created.get(), "검증 실행 잠금을 준비하지 못했습니다.");
            throw new CouponIntegrityException(
                    CouponIntegrityErrorCode.INTEGRITY_CHECK_EXECUTION_FAILED
            );
        }
        if (!acquired) {
            throw new CouponIntegrityException(
                    CouponIntegrityErrorCode.INTEGRITY_CHECK_ALREADY_RUNNING
            );
        }

        CouponIntegrityCheck check = created.get();
        try {
            executor.execute(() -> executionService.execute(check.getId()));
        } catch (RejectedExecutionException exception) {
            failCreatedCheck(check, "전용 검증 실행기가 요청을 수락하지 못했습니다.");
            throw new CouponIntegrityException(
                    CouponIntegrityErrorCode.INTEGRITY_CHECK_EXECUTION_FAILED
            );
        }
        return new CouponIntegrityStartResponse(
                check.getId(), check.getExecutionStatus(), check.getStartedAt()
        );
    }

    @Transactional(readOnly = true)
    public CouponIntegrityListResponse findAll(Long cursor, int size) {
        validateListCondition(cursor, size);
        PageRequest pageable = PageRequest.of(0, size);
        Slice<CouponIntegrityCheck> slice = cursor == null
                ? checkRepository.findAllByOrderByIdDesc(pageable)
                : checkRepository.findByIdLessThanOrderByIdDesc(cursor, pageable);
        List<CouponIntegritySummaryResponse> items = slice.getContent().stream()
                .map(this::toSummary)
                .toList();
        Long nextCursor = slice.hasNext() && !items.isEmpty()
                ? items.getLast().checkId() : null;
        return new CouponIntegrityListResponse(items, nextCursor, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public CouponIntegrityDetailResponse findById(Long checkId) {
        CouponIntegrityCheck check = checkRepository.findById(checkId).orElseThrow(() ->
                new CouponIntegrityException(CouponIntegrityErrorCode.INTEGRITY_CHECK_NOT_FOUND));
        List<CouponIntegrityResultResponse> results = resultRepository
                .findByCheckIdOrderByDisplayOrder(checkId)
                .stream()
                .map(result -> new CouponIntegrityResultResponse(
                        result.getCheckCode(), result.getSeverity(), result.getVerdict(),
                        result.getViolationCount(), result.getDescription()
                ))
                .toList();
        return new CouponIntegrityDetailResponse(
                check.getId(), check.getExecutionStatus(), check.getOverallVerdict(),
                check.getRequestedBy(), check.getAsOfUtc(), check.getStartedAt(),
                check.getCompletedAt(), durationSeconds(check), check.getUserCount(),
                check.getClaimRequestCount(), check.getUserCouponCount(),
                check.getCouponEventCount(), check.getOccurrenceCount(),
                check.getEventItemCount(), check.getClaimRequestMinId(),
                check.getClaimRequestMaxId(), check.getClaimRequestFingerprint(),
                check.getUserCouponMinId(), check.getUserCouponMaxId(),
                check.getUserCouponFingerprint(), check.getCheckCount(),
                check.getPassCount(), check.getInfoCount(), check.getWarnCount(),
                check.getFailCount(), check.getErrorCode(), check.getErrorMessage(), results
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAbandonedChecks() {
        executionLock.tryExecute(() -> historyService.failStaleRunning(
                LocalDateTime.now().minus(recoveryGrace)
        ));
    }

    private CouponIntegritySummaryResponse toSummary(CouponIntegrityCheck check) {
        return new CouponIntegritySummaryResponse(
                check.getId(), check.getExecutionStatus(), check.getOverallVerdict(),
                check.getRequestedBy(), check.getAsOfUtc(), check.getStartedAt(),
                check.getCompletedAt(), durationSeconds(check), check.getClaimRequestCount(),
                check.getUserCouponCount(), check.getCheckCount(), check.getPassCount(),
                check.getInfoCount(), check.getWarnCount(), check.getFailCount()
        );
    }

    private Long durationSeconds(CouponIntegrityCheck check) {
        return check.getCompletedAt() == null ? null
                : Duration.between(check.getStartedAt(), check.getCompletedAt()).toSeconds();
    }

    private void validateListCondition(Long cursor, int size) {
        if ((cursor != null && cursor <= 0) || size < 1 || size > 100) {
            throw new CouponIntegrityException(
                    CouponIntegrityErrorCode.INVALID_INTEGRITY_CHECK_LIST_CONDITION
            );
        }
    }

    private void failCreatedCheck(CouponIntegrityCheck check, String message) {
        if (check != null) {
            try {
                historyService.fail(check.getId(), message, LocalDateTime.now());
            } catch (RuntimeException ignored) {
                // 원래 실행 접수 실패 응답을 유지하고, 남은 RUNNING은 복구 정책에 맡긴다.
            }
        }
    }
}
