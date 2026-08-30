package com.clutch.coupon.admin.dashboard.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 관리자 페이지 운영 홈의 정확한 집계를 생성할 수 없을 때 발생하는 예외다. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AdminCouponDashboardUnavailableException extends RuntimeException {

    /** 관리자 운영 홈 집계 불가 사유로 예외를 생성한다. */
    public AdminCouponDashboardUnavailableException(String message) {
        super(message);
    }

    /** 관리자 운영 홈 집계 불가 사유와 하위 시스템 원인으로 예외를 생성한다. */
    public AdminCouponDashboardUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
