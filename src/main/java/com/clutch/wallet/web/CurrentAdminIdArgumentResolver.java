package com.clutch.wallet.web;

import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import com.clutch.wallet.web.exception.ForbiddenException;
import com.clutch.wallet.web.exception.MissingUserIdHeaderException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentAdminIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_NAME = "X-User-Id";
    private final UserRepository userRepository;

    public CurrentAdminIdArgumentResolver(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter){
        return parameter.hasParameterAnnotation(CurrentAdminId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory){
        String value = webRequest.getHeader(HEADER_NAME);
        if(value == null || value.isBlank()){
            throw new MissingUserIdHeaderException();
        }

        Long userId;
        try{
            userId = Long.valueOf(value);
        }catch(NumberFormatException e){
            throw new MissingUserIdHeaderException();
        }

        User user = userRepository.findById(userId).orElseThrow(ForbiddenException::new);
        if(user.getRole() != UserRole.ADMIN){
            throw new ForbiddenException();
        }
        return userId;
    }
}
