package com.clutch.lolesports.config;

import com.clutch.lolesports.source.ExternalSourceProperties;
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

    /**
     * getSchedule 및 live-stats details 응답은 수 MB가 될 수 있다.
     * 리플레이가 여러 프레임을 한 번에 반환하는 경우도 수용한다.
     */
    private static final int MAX_IN_MEMORY_SIZE = 32 * 1024 * 1024;

    private ExchangeStrategies strategies() {
        return ExchangeStrategies.builder()
                .codecs((ClientCodecConfigurer c) -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }

    /** 실제 esports-api.lolesports.com/persisted/gw — x-api-key 필요 */
    @Bean
    public WebClient realEsportsWebClient(LolesportsProperties props) {
        return WebClient.builder()
                .baseUrl(props.esportsApiBaseUrl())
                .defaultHeader("x-api-key", props.apiKey())
                .exchangeStrategies(strategies())
                .build();
    }

    /** replay 스텁의 persisted API 호환 엔드포인트. */
    @Bean
    public WebClient stubEsportsWebClient(ExternalSourceProperties props) {
        return WebClient.builder()
                .baseUrl(props.stubEsportsApiBaseUrl())
                .exchangeStrategies(strategies())
                .build();
    }

    /** 실제 feed.lolesports.com/livestats/v1 — api key 불필요 */
    @Bean
    public WebClient realLiveStatsWebClient(LolesportsProperties props) {
        return WebClient.builder()
                .baseUrl(props.liveStatsBaseUrl())
                .exchangeStrategies(strategies())
                .build();
    }

    /** replay 스텁의 livestats API 호환 엔드포인트. */
    @Bean
    public WebClient stubLiveStatsWebClient(ExternalSourceProperties props) {
        return WebClient.builder()
                .baseUrl(props.stubLiveStatsBaseUrl())
                .exchangeStrategies(strategies())
                .build();
    }
}
