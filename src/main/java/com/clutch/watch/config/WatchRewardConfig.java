package com.clutch.watch.config;

import com.clutch.watch.redis.WatchAliveExpirationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 시청 보상 모듈 설정.
 */
@Configuration
@EnableConfigurationProperties(WatchRewardProperties.class)
public class WatchRewardConfig {

    /**
     * Redis 전체 DB의 키 만료 이벤트를 시청 세션 만료 listener에 전달한다.
     * Listener 내부에서 {@code watch:alive:} 키만 선별하여 처리한다.
     *
     * @param connectionFactory Redis Pub/Sub 연결 생성 객체
     * @param listener Alive TTL 만료 이벤트 처리 listener
     * @return 만료 이벤트 구독이 등록된 Redis listener container
     */
    @Bean
    public RedisMessageListenerContainer watchRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            WatchAliveExpirationListener listener
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new PatternTopic("__keyevent@*__:expired"));
        return container;
    }
}
