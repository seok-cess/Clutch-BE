package com.clutch.coupon.claim.recovery;

import com.clutch.coupon.claim.exception.CouponClaimException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Redis 연결 감지 및 쿠폰 재고 자동 복구 */
@Component
@RequiredArgsConstructor
public class CouponStockRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            CouponStockRecoveryScheduler.class
    );

    private static final String HEALTH_PROBE_KEY =
            "coupon:stock-recovery:health-probe";

    private final CouponStockRecoveryStateManager stateManager;
    private final CouponStockRecoveryService recoveryService;
    private final StringRedisTemplate stringRedisTemplate;

    /** Redis 상태 확인 및 자동 복구 */
    @Scheduled(
            fixedDelayString =
                    "${coupon.stock-recovery.interval-ms:2000}"
    )
    public void recoverWhenRedisReturns() {
        CouponStockRecoveryState state = stateManager.current();
        if (state == CouponStockRecoveryState.RECOVERING
                || state == CouponStockRecoveryState.FAILED) {
            return;
        }

        try {
            stringRedisTemplate.hasKey(HEALTH_PROBE_KEY);
        } catch (DataAccessException exception) {
            stateManager.markUnavailable();
            return;
        }

        if (stateManager.current()
                == CouponStockRecoveryState.UNAVAILABLE) {
            try {
                CouponStockRecoveryResult result =
                        recoveryService.recoverOpenOccurrences();
                log.info(
                        "쿠폰 Redis 자동 복구 완료: occurrences={}, "
                                + "items={}, users={}",
                        result.recoveredOccurrences(),
                        result.recoveredItems(),
                        result.recoveredUsers()
                );
            } catch (CouponClaimException exception) {
                log.warn(
                        "쿠폰 Redis 자동 복구 실패: code={}",
                        exception.getErrorCode(),
                        exception
                );
            }
        }
    }
}
