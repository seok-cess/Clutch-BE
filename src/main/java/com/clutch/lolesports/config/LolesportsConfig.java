package com.clutch.lolesports.config;

import com.clutch.lolesports.source.ExternalSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * lolesports 연동 모듈 설정.
 *
 * 스케줄링·프로퍼티 바인딩을 이 모듈 안에서 켠다 —
 * 메인 클래스(ClutchApplication)를 건드리지 않아 다른 작업과 충돌하지 않는다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({LolesportsProperties.class, ExternalSourceProperties.class})
public class LolesportsConfig {
}
