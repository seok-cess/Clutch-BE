package com.clutch.lolesports.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 외부 데이터 소스 전환 뒤의 백그라운드 워밍업 실행기를 제공한다. */
@Configuration
public class ExternalSourceAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(ExternalSourceAsyncConfig.class);

    private static final int QUEUE_CAPACITY = 10;

    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("external-source-refresh-");
        executor.setRejectedExecutionHandler((task, threadPool) -> log.warn(
                "외부 데이터 소스 전환 워밍업 대기열이 가득 차 요청을 건너뜁니다."
        ));
        executor.initialize();
        return executor;
    }
}
