package com.clutch.coupon.claim.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MySqlCouponSuccessCountSynchronizationLockTest {

    @Autowired
    private CouponSuccessCountSynchronizationLock lock;

    @Test
    void 한_연결이_잠금을_보유하면_다른_연결은_집계를_건너뛴다() {
        AtomicBoolean nestedTaskExecuted = new AtomicBoolean();

        boolean outerExecuted = lock.tryExecute(() -> {
            boolean nestedExecuted = lock.tryExecute(
                    () -> nestedTaskExecuted.set(true)
            );
            assertThat(nestedExecuted).isFalse();
        });

        assertThat(outerExecuted).isTrue();
        assertThat(nestedTaskExecuted).isFalse();
    }

    @Test
    void 작업이_끝나면_다음_실행이_잠금을_획득한다() {
        assertThat(lock.tryExecute(() -> { })).isTrue();
        assertThat(lock.tryExecute(() -> { })).isTrue();
    }
}
