package com.clutch.wallet.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link CurrentUserId} 파라미터를 X-User-Id 요청 헤더의 양수 사용자 ID로 변환한다. */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_NAME = "X-User-Id";

    /** 상태 없는 사용자 ID 인자 해석기를 생성한다. */
    public CurrentUserIdArgumentResolver() {
    }

    /**
     * 현재 파라미터가 사용자 ID 주입 대상인지 확인한다.
     *
     * @param parameter 컨트롤러 메서드 파라미터
     * @return CurrentUserId 애노테이션이 있으면 true
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter){
        return parameter.hasParameterAnnotation(CurrentUserId.class);
    }

    /**
     * 필수 헤더를 양수 Long 값으로 검증하고 컨트롤러 인자로 반환한다.
     *
     * @param parameter 해석할 컨트롤러 메서드 파라미터
     * @param mavContainer 현재 요청의 모델·뷰 컨테이너
     * @param webRequest 현재 웹 요청
     * @param binderFactory 데이터 바인더 팩토리
     * @return X-User-Id 헤더에서 변환한 양수 사용자 ID
     * @throws MissingUserIdHeaderException 헤더가 없거나 양수 Long 값이 아닐 때
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory){
        String value = webRequest.getHeader(HEADER_NAME);
        if(value == null || value.isBlank()){
            throw new MissingUserIdHeaderException();
        }
        try{
            Long userId = Long.valueOf(value);
            // 지갑과 배팅 API가 존재하지 않는 비양수 사용자 ID로 처리되는 것을 공통 차단한다.
            if (userId < 1L) {
                throw new MissingUserIdHeaderException();
            }
            return userId;
        }catch(NumberFormatException e){
            throw new MissingUserIdHeaderException();
        }
    }

}
