package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponStockResponse;
import com.clutch.coupon.claim.exception.CouponClaimErrorCode;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Redis 쿠폰 재고 조회 서비스 테스트 */
@ExtendWith(MockitoExtension.class)
class CouponStockServiceTest {

    private static final Long ITEM_ID = 101L;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CouponStockRecoveryStateManager recoveryStateManager;

    @InjectMocks
    private CouponStockService couponStockService;

    @Test
    void readsRemainingStockOnlyFromRedis() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CouponClaimRedisKeys.stock(ITEM_ID)))
                .thenReturn("7");

        CouponStockResponse response = couponStockService.getStock(ITEM_ID);

        assertThat(response.remainingStock()).isEqualTo(7L);
        assertThat(response.exhausted()).isFalse();
        verify(valueOperations).get(CouponClaimRedisKeys.stock(ITEM_ID));
    }

    @Test
    void returnsExhaustedSnapshotWhenStockIsZero() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CouponClaimRedisKeys.stock(ITEM_ID)))
                .thenReturn("0");

        CouponStockResponse response = couponStockService.getStock(ITEM_ID);

        assertThat(response.remainingStock()).isZero();
        assertThat(response.exhausted()).isTrue();
    }

    @Test
    void distinguishesRedisFailureFromExhaustedStock() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CouponClaimRedisKeys.stock(ITEM_ID)))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> couponStockService.getStock(ITEM_ID))
                .isInstanceOfSatisfying(
                        CouponClaimException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponClaimErrorCode.COUPON_STOCK_READ_FAILED)
                );
        verify(recoveryStateManager).markUnavailable();
    }

    @Test
    void reportsMissingRedisStockAsNotInitialized() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CouponClaimRedisKeys.stock(ITEM_ID)))
                .thenReturn(null);

        assertThatThrownBy(() -> couponStockService.getStock(ITEM_ID))
                .isInstanceOfSatisfying(
                        CouponClaimException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponClaimErrorCode.COUPON_STOCK_NOT_INITIALIZED)
                );
        verify(recoveryStateManager).markUnavailable();
    }

    @Test
    void blocksStockReadWhileRecoveryIsRunning() {
        doThrow(new CouponClaimException(
                CouponClaimErrorCode.COUPON_STOCK_RECOVERING
        )).when(recoveryStateManager).requireReady();

        assertThatThrownBy(() -> couponStockService.getStock(ITEM_ID))
                .isInstanceOfSatisfying(
                        CouponClaimException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        CouponClaimErrorCode
                                                .COUPON_STOCK_RECOVERING
                                )
                );

        verifyNoInteractions(stringRedisTemplate);
    }
}
