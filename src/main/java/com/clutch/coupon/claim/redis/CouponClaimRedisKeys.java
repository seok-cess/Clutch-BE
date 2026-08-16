package com.clutch.coupon.claim.redis;

/**
 * 쿠폰 발급 Redis 키
 */
public final class CouponClaimRedisKeys {

    private static final String STOCK_KEY_FORMAT =
            "coupon:event-item:%d:stock";

    private static final String CLAIMED_USERS_KEY_FORMAT =
            "coupon:occurrence:%d:claimed-users";

    private CouponClaimRedisKeys() {
    }

    /**
     * 쿠폰 이벤트 항목 재고 키
     *
     * @param couponEventItemId 쿠폰 이벤트 항목 식별자
     * @return 재고 Redis 키
     */
    public static String stock(Long couponEventItemId) {
        return STOCK_KEY_FORMAT.formatted(couponEventItemId);
    }

    /**
     * 쿠폰 이벤트 회차 발급 사용자 키
     *
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @return 발급 사용자 Redis 키
     */
    public static String claimedUsers(
            Long couponEventOccurrenceId
    ) {
        return CLAIMED_USERS_KEY_FORMAT.formatted(
                couponEventOccurrenceId
        );
    }
}