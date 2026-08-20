package com.clutch.coupon.claim.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 쿠폰 발급 Redis 설정
 */
@Configuration
public class CouponClaimRedisConfig {

    /**
     * 쿠폰 발급 Lua 스크립트
     *
     * @return 쿠폰 발급 Lua 스크립트
     */
    @Bean
    public RedisScript<Long> couponClaimScript() {
        DefaultRedisScript<Long> script =
                new DefaultRedisScript<>();

        script.setLocation(
                new ClassPathResource(
                        "redis/coupon-claim.lua"
                )
        );
        script.setResultType(Long.class);

        return script;
    }

    /** 쿠폰 재고 복구 Lua 스크립트 */
    @Bean
    public RedisScript<Long> couponStockRecoveryScript() {
        DefaultRedisScript<Long> script =
                new DefaultRedisScript<>();

        script.setLocation(
                new ClassPathResource(
                        "redis/coupon-stock-recovery.lua"
                )
        );
        script.setResultType(Long.class);

        return script;
    }
}
