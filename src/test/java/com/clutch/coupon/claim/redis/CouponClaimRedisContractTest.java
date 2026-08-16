package com.clutch.coupon.claim.redis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 쿠폰 발급 Redis 계약 테스트
 */
class CouponClaimRedisContractTest {

    @Test
    void stockKeyContainsCouponEventItemId() {
        String stockKey = CouponClaimRedisKeys.stock(30L);

        assertThat(stockKey)
                .isEqualTo("coupon:event-item:30:stock");
    }

    @Test
    void claimedUsersKeyContainsCouponEventOccurrenceId() {
        String claimedUsersKey =
                CouponClaimRedisKeys.claimedUsers(7L);

        assertThat(claimedUsersKey)
                .isEqualTo(
                        "coupon:occurrence:7:claimed-users"
                );
    }

    @Test
    void redisResultIsConvertedFromCode() {
        assertThat(CouponClaimRedisResult.from(1L))
                .isEqualTo(CouponClaimRedisResult.SUCCESS);

        assertThat(CouponClaimRedisResult.from(-1L))
                .isEqualTo(
                        CouponClaimRedisResult.ALREADY_CLAIMED
                );

        assertThat(CouponClaimRedisResult.from(-2L))
                .isEqualTo(
                        CouponClaimRedisResult.STOCK_EXHAUSTED
                );

        assertThat(CouponClaimRedisResult.from(-3L))
                .isEqualTo(
                        CouponClaimRedisResult.STOCK_NOT_INITIALIZED
                );
    }

    @Test
    void unknownRedisResultCodeIsRejected() {
        assertThatThrownBy(() ->
                CouponClaimRedisResult.from(999L)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "지원하지 않는 Redis 결과 코드"
                );
    }
}