package com.clutch.wallet.web;

import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** 지갑과 배팅 API에서 {@link CurrentUserId} 파라미터를 공통으로 해석하도록 등록한다. */
@Configuration
public class WalletWebConfig implements WebMvcConfigurer {

    @Override
    /** 사용자 ID 헤더 해석기를 Spring MVC 인자 해석기 목록에 추가한다. */
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers){
        resolvers.add(new CurrentUserIdArgumentResolver());
    }
}
