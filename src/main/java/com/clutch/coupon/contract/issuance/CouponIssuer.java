package com.clutch.coupon.contract.issuance;

/**
 * 동기 쿠폰 발급 계약
 */
public interface CouponIssuer {

    /**
     * 쿠폰 발급
     *
     * @param command 쿠폰 발급 명령
     * @return 쿠폰 발급 결과
     */
    CouponIssuanceResult issue(
            CouponIssuanceCommand command
    );
}