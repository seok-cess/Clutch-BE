package com.clutch.coupon.claim.redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 발급 Redis 실행기 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponClaimRedisExecutorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisScript<Long> couponClaimScript;

    @InjectMocks
    private CouponClaimRedisExecutor couponClaimRedisExecutor;

    /**
     * 쿠폰 발급 Lua 실행 검증
     */
    @Test
    void claimExecutesLuaScriptWithKeysAndUserId() {
        when(stringRedisTemplate.execute(
                couponClaimScript,
                List.of(
                        "coupon:event-item:100:stock",
                        "coupon:occurrence:200:claimed-users"
                ),
                "300",
                "CLAIM"
        )).thenReturn(1L);

        CouponClaimRedisResult result =
                couponClaimRedisExecutor.claim(
                        100L,
                        200L,
                        300L
                );

        assertThat(result)
                .isEqualTo(CouponClaimRedisResult.SUCCESS);

        verify(stringRedisTemplate).execute(
                couponClaimScript,
                List.of(
                        "coupon:event-item:100:stock",
                        "coupon:occurrence:200:claimed-users"
                ),
                "300",
                     "CLAIM"
        );
    }

    /**
     * Redis 결과 부재 예외 검증
     */
    @Test
    void claimFailsWhenRedisReturnsNull() {
        when(stringRedisTemplate.execute(
                couponClaimScript,
                List.of(
                        "coupon:event-item:100:stock",
                        "coupon:occurrence:200:claimed-users"
                ),
                "300",
                "CLAIM"
        )).thenReturn(null);

        assertThatThrownBy(() ->
                couponClaimRedisExecutor.claim(
                        100L,
                        200L,
                        300L
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("쿠폰 발급 Redis 결과 없음");
    }
    /**
     * Redis 발급 보상 Lua 실행 검증
     */
    @Test
    void rollbackExecutesLuaScriptWithKeysAndUserId() {
        when(stringRedisTemplate.execute(
                couponClaimScript,
                List.of(
                        "coupon:event-item:100:stock",
                        "coupon:occurrence:200:claimed-users"
                ),
                "300",
                "ROLLBACK"
        )).thenReturn(1L);

        boolean result =
                couponClaimRedisExecutor.rollback(
                        100L,
                        200L,
                        300L
                );

        assertThat(result).isTrue();

        verify(stringRedisTemplate).execute(
                couponClaimScript,
                List.of(
                        "coupon:event-item:100:stock",
                        "coupon:occurrence:200:claimed-users"
                ),
                "300",
                "ROLLBACK"
        );
    }
}