package com.clutch.lolesports.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * lolesports 외부 API 호출용 WebClient.
 *
 * 이 앱은 서블릿(WebMVC) 기반이라 WebClient.Builder 빈이 자동 등록되지 않는다.
 * (WebFlux 는 WebClient 를 쓰기 위해 의존성으로만 추가한 것)
 * 그래서 Builder 를 주입받지 않고 직접 생성한다.
 */
@Configuration
public class WebClientConfig {

    /** getSchedule 등 응답이 커서 기본 256KB 버퍼로는 부족할 수 있음 */
    private static final int MAX_IN_MEMORY_SIZE = 4 * 1024 * 1024;

    private ExchangeStrategies strategies() {
        return ExchangeStrategies.builder()
                .codecs((ClientCodecConfigurer c) -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }

    /** esports-api.lolesports.com/persisted/gw — x-api-key 필요 */
    @Bean
    public WebClient esportsWebClient(LolesportsProperties props) {
        return WebClient.builder()
                .baseUrl(props.esportsApiBaseUrl())
                .defaultHeader("x-api-key", props.apiKey())
                .exchangeStrategies(strategies())
                .build();
    }

    /** feed.lolesports.com/livestats/v1 — api key 불필요 */
    @Bean
    public WebClient liveStatsWebClient(LolesportsProperties props) {
        return WebClient.builder()
                .baseUrl(props.liveStatsBaseUrl())
                .exchangeStrategies(strategies())
                .build();
    }
}
