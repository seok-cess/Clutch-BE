package com.clutch.wallet.web;

import com.clutch.user.repository.UserRepository;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

public class WalletWebConfig implements WebMvcConfigurer {

    private final UserRepository userRepository;
    public WalletWebConfig(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers){
        resolvers.add(new CurrentUserIdArgumentResolver());
        resolvers.add(new CurrentAdminIdArgumentResolver(userRepository));
    }
}
