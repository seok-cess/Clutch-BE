package com.clutch.coupon.contract.issuance;

import java.util.List;

/** 쿠폰 발급 복구 데이터 조회 계약 */
public interface CouponIssuanceRecoveryReader {

    /** 쿠폰 이벤트 항목별 실제 발급 수량 */
    long countIssuedCoupons(Long couponEventItemId);

    /** 쿠폰 이벤트 회차별 실제 발급 사용자 목록 */
    List<Long> findIssuedUserIds(Long couponEventOccurrenceId);
}
