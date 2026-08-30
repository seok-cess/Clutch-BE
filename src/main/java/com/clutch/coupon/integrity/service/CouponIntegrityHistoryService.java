package com.clutch.coupon.integrity.service;

import com.clutch.coupon.integrity.domain.CouponIntegrityCheck;
import com.clutch.coupon.integrity.domain.CouponIntegrityCheckResult;
import com.clutch.coupon.integrity.domain.CouponIntegritySnapshot;
import com.clutch.coupon.integrity.domain.IntegrityExecutionStatus;
import com.clutch.coupon.integrity.repository.CouponIntegrityCheckRepository;
import com.clutch.coupon.integrity.repository.CouponIntegrityCheckResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponIntegrityHistoryService {
    static final String EXECUTION_FAILED = "INTEGRITY_CHECK_EXECUTION_FAILED";
    static final String ABANDONED = "INTEGRITY_CHECK_ABANDONED";

    private final CouponIntegrityCheckRepository checkRepository;
    private final CouponIntegrityCheckResultRepository resultRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CouponIntegrityCheck create(Long adminId, LocalDateTime startedAt, LocalDateTime staleCutoff) {
        failStaleRunning(staleCutoff);
        if (checkRepository.existsByExecutionStatus(IntegrityExecutionStatus.RUNNING)) {
            throw new CouponIntegrityException(
                    CouponIntegrityErrorCode.INTEGRITY_CHECK_ALREADY_RUNNING
            );
        }
        return checkRepository.saveAndFlush(CouponIntegrityCheck.start(adminId, startedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long checkId, CouponIntegritySnapshot snapshot, LocalDateTime completedAt) {
        CouponIntegrityCheck check = find(checkId);
        resultRepository.saveAll(snapshot.results().stream()
                .map(result -> CouponIntegrityCheckResult.from(checkId, result))
                .toList());
        check.complete(snapshot, completedAt);
        checkRepository.saveAndFlush(check);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long checkId, String message, LocalDateTime completedAt) {
        CouponIntegrityCheck check = find(checkId);
        check.fail(EXECUTION_FAILED, sanitize(message), completedAt);
        checkRepository.saveAndFlush(check);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int failStaleRunning(LocalDateTime cutoff) {
        var stale = checkRepository.findByExecutionStatusAndStartedAtBefore(
                IntegrityExecutionStatus.RUNNING, cutoff
        );
        LocalDateTime now = LocalDateTime.now();
        stale.forEach(check -> check.fail(
                ABANDONED,
                "애플리케이션 종료로 완료되지 않은 실행을 복구했습니다.",
                now
        ));
        checkRepository.saveAll(stale);
        return stale.size();
    }

    private CouponIntegrityCheck find(Long checkId) {
        return checkRepository.findById(checkId).orElseThrow(() ->
                new CouponIntegrityException(CouponIntegrityErrorCode.INTEGRITY_CHECK_NOT_FOUND));
    }

    private String sanitize(String message) {
        String safe = message == null || message.isBlank()
                ? "검증 SQL 실행 중 오류가 발생했습니다."
                : message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
