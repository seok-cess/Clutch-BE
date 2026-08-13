package com.clutch.wallet.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_NAME = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter){
        return parameter.hasParameterAnnotation(CurrentUserId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory){
        String value = webRequest.getHeader(HEADER_NAME);
        if(value == null || value.isBlank()){
            throw new MissingUserIdHeaderException();
        }
        try{
            return Long.valueOf(value);
        }catch(NumberFormatException e){
            throw new MissingUserIdHeaderException();
        }
    }

}
