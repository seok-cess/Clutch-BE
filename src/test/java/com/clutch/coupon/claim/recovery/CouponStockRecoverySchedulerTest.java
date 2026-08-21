package com.clutch.coupon.claim.recovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 쿠폰 재고 자동 복구 스케줄러 테스트 */
@ExtendWith(MockitoExtension.class)
class CouponStockRecoverySchedulerTest {

    @Mock
    private CouponStockRecoveryStateManager stateManager;

    @Mock
    private CouponStockRecoveryService recoveryService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private CouponStockRecoveryScheduler scheduler;

    @Test
    void marksUnavailableWhenRedisHealthCheckFails() {
        when(stateManager.current())
                .thenReturn(CouponStockRecoveryState.READY);
        when(stringRedisTemplate.hasKey(
                "coupon:stock-recovery:health-probe"
        )).thenThrow(new DataAccessResourceFailureException("down"));

        scheduler.recoverWhenRedisReturns();

        verify(stateManager).markUnavailable();
        verify(recoveryService, never()).recoverOpenOccurrences();
    }

    @Test
    void recoversWhenRedisReturns() {
        when(stateManager.current())
                .thenReturn(
                        CouponStockRecoveryState.UNAVAILABLE,
                        CouponStockRecoveryState.UNAVAILABLE
                );
        when(stringRedisTemplate.hasKey(
                "coupon:stock-recovery:health-probe"
        )).thenReturn(false);
        when(recoveryService.recoverOpenOccurrences())
                .thenReturn(new CouponStockRecoveryResult(
                        CouponStockRecoveryState.READY,
                        1,
                        1,
                        3
                ));

        scheduler.recoverWhenRedisReturns();

        verify(recoveryService).recoverOpenOccurrences();
    }
}
