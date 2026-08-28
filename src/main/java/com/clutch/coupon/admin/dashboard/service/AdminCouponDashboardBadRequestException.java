package com.clutch.coupon.admin.dashboard.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 관리자 페이지 운영 홈의 조회 조건이 허용 범위를 벗어났을 때 발생하는 예외다. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AdminCouponDashboardBadRequestException extends RuntimeException {

    /** 잘못된 관리자 운영 홈 조회 조건의 안내 메시지로 예외를 생성한다. */
    public AdminCouponDashboardBadRequestException(String message) {
        super(message);
    }
}
