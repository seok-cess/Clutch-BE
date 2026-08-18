package com.clutch.wallet.web;

import com.clutch.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** 지갑과 배팅 API에서 {@link CurrentUserId} 파라미터를 공통으로 해석하도록 등록한다. */
@Configuration
public class WalletWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<UserRepository> userRepositoryProvider;

    /** 사용자와 관리자 ID 인자 해석기 등록 구성을 생성한다. */
    public WalletWebConfig(ObjectProvider<UserRepository> userRepositoryProvider) {
        this.userRepositoryProvider = userRepositoryProvider;
    }

    /**
     * 사용자와 관리자 ID 헤더 해석기를 Spring MVC 인자 해석기 목록에 추가한다.
     *
     * @param resolvers 애플리케이션에 등록할 MVC 인자 해석기 목록
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
        userRepositoryProvider.ifAvailable(userRepository ->
                resolvers.add(new CurrentAdminIdArgumentResolver(userRepository))
        );
    }
}
