package com.clutch.coupon.integrity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class CouponIntegrityAsyncConfig {
    @Bean(name = "couponIntegrityExecutor")
    public Executor couponIntegrityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("coupon-integrity-");
        executor.initialize();
        return executor;
    }
}
