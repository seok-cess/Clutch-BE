package com.clutch.coupon.claim.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 쿠폰 발급 Redis 실행기
 */
@Component
@RequiredArgsConstructor
public class CouponClaimRedisExecutor {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> couponClaimScript;

    /**
     * 쿠폰 발급 실행
     *
     * @param couponEventItemId 쿠폰 이벤트 항목 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @param userId 사용자 식별자
     * @return 쿠폰 발급 Redis 실행 결과
     */
    public CouponClaimRedisResult claim(
            Long couponEventItemId,
            Long couponEventOccurrenceId,
            Long userId
    ) {
        List<String> keys = List.of(
                CouponClaimRedisKeys.stock(
                        couponEventItemId
                ),
                CouponClaimRedisKeys.claimedUsers(
                        couponEventOccurrenceId
                )
        );

        Long resultCode = stringRedisTemplate.execute(
                couponClaimScript,
                keys,
                String.valueOf(userId)
        );

        if (resultCode == null) {
            throw new IllegalStateException(
                    "쿠폰 발급 Redis 결과 없음"
            );
        }

        return CouponClaimRedisResult.from(resultCode);
    }
}