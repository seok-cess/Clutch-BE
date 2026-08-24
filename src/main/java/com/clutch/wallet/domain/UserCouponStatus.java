package com.clutch.wallet.domain;

/**
 * 사용자 쿠폰의 상태.
 */
public enum UserCouponStatus {
    /** 발급되어 사용 가능한 상태. */
    ISSUED,
    /** 사용이 완료된 상태. */
    USED,
    /** 취소된 상태. */
    CANCELLED
}
