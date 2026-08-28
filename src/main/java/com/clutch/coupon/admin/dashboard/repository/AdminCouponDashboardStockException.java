package com.clutch.coupon.admin.dashboard.repository;

/** 관리자 페이지 운영 홈의 Redis 재고 일괄 조회가 불가능할 때 발생하는 예외다. */
public class AdminCouponDashboardStockException extends RuntimeException {

    /** 관리자 운영 홈 재고 조회 실패 사유로 예외를 생성한다. */
    public AdminCouponDashboardStockException(String message) {
        super(message);
    }

    /** 관리자 운영 홈 재고 조회 실패 사유와 Redis 원인으로 예외를 생성한다. */
    public AdminCouponDashboardStockException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
