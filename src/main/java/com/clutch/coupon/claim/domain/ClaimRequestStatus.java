package com.clutch.coupon.claim.domain;

public enum ClaimRequestStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    /** DB에 저장된 취소 요청을 조회·필터링하기 위한 상태. */
    CANCELLED
}
