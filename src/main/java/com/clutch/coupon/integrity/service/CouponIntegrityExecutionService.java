package com.clutch.coupon.integrity.service;

import com.clutch.coupon.integrity.domain.CouponIntegritySnapshot;
import com.clutch.coupon.integrity.repository.CouponIntegrityQueryRepository;
import com.clutch.coupon.integrity.repository.MySqlCouponIntegrityExecutionLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.locks.LockSupport;

@Service
@RequiredArgsConstructor
public class CouponIntegrityExecutionService {
    private static final int LOCK_RETRY_COUNT = 20;

    private final MySqlCouponIntegrityExecutionLock executionLock;
    private final CouponIntegrityQueryRepository queryRepository;
    private final CouponIntegrityHistoryService historyService;

    public void execute(Long checkId) {
        for (int attempt = 0; attempt < LOCK_RETRY_COUNT; attempt++) {
            if (executionLock.tryExecute(() -> executeLocked(checkId))) {
                return;
            }
            LockSupport.parkNanos(100_000_000L);
        }
        // 다른 인스턴스가 실제 작업을 수행 중일 수 있으므로 이 실행을 실패 처리하지 않는다.
    }

    private void executeLocked(Long checkId) {
        try {
            CouponIntegritySnapshot snapshot = queryRepository.execute();
            historyService.complete(checkId, snapshot, LocalDateTime.now());
        } catch (RuntimeException exception) {
            historyService.fail(checkId, safeMessage(exception), LocalDateTime.now());
        }
    }

    private String safeMessage(RuntimeException exception) {
        return "검증 실행 중 오류가 발생했습니다. ("
                + exception.getClass().getSimpleName() + ")";
    }
}
