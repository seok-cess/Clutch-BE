package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.repository.CouponSuccessCountSynchronizationLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponSuccessCountSynchronizationSchedulerTest {

    @Mock
    private CouponSuccessCountSynchronizer synchronizer;

    @Mock
    private CouponSuccessCountSynchronizationLock lock;

    private SimpleMeterRegistry meterRegistry;
    private CouponSuccessCountSynchronizationScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new CouponSuccessCountSynchronizationScheduler(
                synchronizer,
                lock,
                meterRegistry
        );
    }

    @Test
    void 잠금을_획득하면_집계_결과와_성공_메트릭을_기록한다() {
        when(lock.tryExecute(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return true;
        });
        when(synchronizer.synchronize()).thenReturn(
                new CouponSuccessCountSynchronizationResult(143, 2)
        );

        scheduler.synchronize();

        verify(synchronizer).synchronize();
        assertThat(meterRegistry.get(
                CouponSuccessCountSynchronizationScheduler
                        .SCANNED_ITEMS_METRIC
        ).summary().totalAmount()).isEqualTo(143);
        assertThat(meterRegistry.get(
                CouponSuccessCountSynchronizationScheduler
                        .UPDATED_ITEMS_METRIC
        ).summary().totalAmount()).isEqualTo(2);
        assertThat(meterRegistry.get(
                CouponSuccessCountSynchronizationScheduler.DURATION_METRIC
        ).tag("outcome", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void 다른_인스턴스가_집계_중이면_실행을_건너뛴다() {
        when(lock.tryExecute(any())).thenReturn(false);

        scheduler.synchronize();

        verify(synchronizer, never()).synchronize();
        assertThat(meterRegistry.get(
                CouponSuccessCountSynchronizationScheduler
                        .LOCK_SKIPPED_METRIC
        ).counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get(
                CouponSuccessCountSynchronizationScheduler.DURATION_METRIC
        ).tag("outcome", "skipped").timer().count()).isEqualTo(1);
    }

    @Test
    void 집계가_실패하면_실패_메트릭을_기록하고_예외를_전파한다() {
        when(lock.tryExecute(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return true;
        });
        when(synchronizer.synchronize())
                .thenThrow(new IllegalStateException("집계 실패"));

        assertThatThrownBy(scheduler::synchronize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("집계 실패");

        assertThat(meterRegistry.get(
                CouponSuccessCountSynchronizationScheduler.FAILURE_METRIC
        ).counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get(
                CouponSuccessCountSynchronizationScheduler.DURATION_METRIC
        ).tag("outcome", "failure").timer().count()).isEqualTo(1);
    }
}
