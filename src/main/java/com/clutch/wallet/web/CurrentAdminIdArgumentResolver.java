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

/** {@link CurrentAdminId} 파라미터를 X-User-Id 요청 헤더 기반 관리자 ID로 변환한다. */
public class CurrentAdminIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_NAME = "X-User-Id";
    private final UserRepository userRepository;

    /**
     * 관리자 권한 검증에 사용할 사용자 조회 저장소로 인자 해석기를 생성한다.
     *
     * @param userRepository 사용자 조회에 사용할 저장소
     */
    public CurrentAdminIdArgumentResolver(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    /**
     * 현재 파라미터가 관리자 ID 주입 대상인지 확인한다.
     *
     * @param parameter 컨트롤러 메서드 파라미터
     * @return CurrentAdminId 애노테이션이 있으면 true
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter){
        return parameter.hasParameterAnnotation(CurrentAdminId.class);
    }

    /**
     * 헤더의 사용자 ID를 조회해 관리자 권한을 검증하고 컨트롤러 인자로 반환한다.
     *
     * @param parameter 해석할 컨트롤러 메서드 파라미터
     * @param mavContainer 현재 요청의 모델·뷰 컨테이너
     * @param webRequest 현재 웹 요청
     * @param binderFactory 데이터 바인더 팩토리
     * @return X-User-Id 헤더로 확인된 관리자 사용자 ID
     * @throws MissingUserIdHeaderException 헤더가 없거나 Long 값이 아닐 때
     * @throws ForbiddenException 사용자가 없거나 관리자 권한이 아닐 때
     */
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
