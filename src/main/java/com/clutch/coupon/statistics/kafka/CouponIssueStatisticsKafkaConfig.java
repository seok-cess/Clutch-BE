package com.clutch.coupon.statistics.kafka;

import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** DLT 기록이 DB 일시 장애로 유실되지 않도록 별도 무제한 재시도 Listener를 구성한다. */
@Configuration
public class CouponIssueStatisticsKafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object>
    couponStatisticsDltKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(
                        5_000L,
                        FixedBackOff.UNLIMITED_ATTEMPTS
                )
        ));
        return factory;
    }
}
