package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.repository.CouponSuccessCountSynchronizationLock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** 쿠폰 성공 수량 집계의 실행 잠금과 운영 메트릭을 담당한다. */
@Component
@ConditionalOnProperty(
        name = "coupon.success-count-sync.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CouponSuccessCountSynchronizationScheduler {

    static final String DURATION_METRIC =
            "clutch.coupon.success_count.synchronization.duration";
    static final String FAILURE_METRIC =
            "clutch.coupon.success_count.synchronization.failures";
    static final String LOCK_SKIPPED_METRIC =
            "clutch.coupon.success_count.synchronization.lock.skipped";
    static final String SCANNED_ITEMS_METRIC =
            "clutch.coupon.success_count.synchronization.items.scanned";
    static final String UPDATED_ITEMS_METRIC =
            "clutch.coupon.success_count.synchronization.items.updated";

    private final CouponSuccessCountSynchronizer synchronizer;
    private final CouponSuccessCountSynchronizationLock lock;
    private final MeterRegistry meterRegistry;
    private final Counter failureCounter;
    private final Counter lockSkippedCounter;
    private final DistributionSummary scannedItems;
    private final DistributionSummary updatedItems;

    public CouponSuccessCountSynchronizationScheduler(
            CouponSuccessCountSynchronizer synchronizer,
            CouponSuccessCountSynchronizationLock lock,
            MeterRegistry meterRegistry
    ) {
        this.synchronizer = synchronizer;
        this.lock = lock;
        this.meterRegistry = meterRegistry;
        this.failureCounter = Counter.builder(FAILURE_METRIC)
                .description("쿠폰 성공 수량 동기화 실패 횟수")
                .register(meterRegistry);
        this.lockSkippedCounter = Counter.builder(LOCK_SKIPPED_METRIC)
                .description("다른 인스턴스가 집계 중이어서 건너뛴 횟수")
                .register(meterRegistry);
        this.scannedItems = DistributionSummary
                .builder(SCANNED_ITEMS_METRIC)
                .description("동기화 한 번에 비교한 쿠폰 이벤트 항목 수")
                .register(meterRegistry);
        this.updatedItems = DistributionSummary
                .builder(UPDATED_ITEMS_METRIC)
                .description("동기화 한 번에 갱신한 쿠폰 이벤트 항목 수")
                .register(meterRegistry);
    }

    /** 잠금을 획득한 한 인스턴스에서만 성공 수량 집계를 실행한다. */
    @Scheduled(
            fixedDelayString =
                    "${coupon.success-count-sync.interval-ms:5000}"
    )
    public void synchronize() {
        Timer.Sample sample = Timer.start(meterRegistry);
        AtomicReference<CouponSuccessCountSynchronizationResult> result =
                new AtomicReference<>();
        String outcome = "skipped";

        try {
            boolean executed = lock.tryExecute(
                    () -> result.set(synchronizer.synchronize())
            );
            if (!executed) {
                lockSkippedCounter.increment();
                return;
            }

            CouponSuccessCountSynchronizationResult synchronizationResult =
                    result.get();
            scannedItems.record(
                    synchronizationResult.scannedItemCount()
            );
            updatedItems.record(
                    synchronizationResult.updatedItemCount()
            );
            outcome = "success";
        } catch (RuntimeException exception) {
            outcome = "failure";
            failureCounter.increment();
            throw exception;
        } finally {
            sample.stop(Timer.builder(DURATION_METRIC)
                    .description("쿠폰 성공 수량 동기화 실행 시간")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }
}
