package com.clutch.coupon.statistics.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 거절 통계 Kafka 발행이 사용자 쿠폰 신청 thread를 점유하지 않게 격리한다. */
@Slf4j
@EnableAsync
@Configuration
public class CouponClaimRejectionAsyncConfig {

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 20_000;

    @Bean(name = "couponClaimRejectionExecutor")
    public Executor couponClaimRejectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("coupon-rejection-");
        executor.setRejectedExecutionHandler((task, threadPool) -> log.warn(
                "쿠폰 신청 거절 통계 비동기 큐가 가득 차 이벤트를 건너뜁니다."
        ));
        executor.initialize();
        return executor;
    }
}
