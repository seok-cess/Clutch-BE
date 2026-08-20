package com.clutch.coupon.claim.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE 연결 객체 생성 */
@Component
public class CouponStockSseEmitterFactory {

    /** 제한 시간이 적용된 SSE 연결 객체 생성 */
    public SseEmitter create(Long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }
}
