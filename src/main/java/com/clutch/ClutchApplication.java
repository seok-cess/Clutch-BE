package com.clutch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.ZoneOffset;
import java.util.TimeZone;

/**
 * CLUTCH 애플리케이션의 Spring Boot 시작점.
 */
@SpringBootApplication
public class ClutchApplication {

    static {
        // 로컬 실행 환경과 관계없이 LocalDateTime 및 JDBC가 같은 UTC 기준을 사용하게 한다.
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    /**
     * Spring Boot 애플리케이션을 실행한다.
     *
     * @param args 애플리케이션 실행 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(ClutchApplication.class, args);
    }

}
