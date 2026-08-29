package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.exception.CouponClaimErrorCode;

/** 쿠폰 신청 거절을 발급 핵심 흐름과 분리해 후속 통계 경로로 전달하는 계약이다. */
public interface CouponClaimRejectionPublisher {

    /**
     * 거절 결과를 비동기 통계 경로로 전달한다.
     *
     * <p>통계 전달 실패가 원래 쿠폰 신청 응답을 변경해서는 안 된다.</p>
     */
    void publish(
            Long couponEventId,
            Long couponEventOccurrenceId,
            CouponClaimErrorCode errorCode
    );
}
