package com.clutch.coupon.claim.redis;

/**
 * 쿠폰 발급 Redis 실행 결과
 */
public enum CouponClaimRedisResult {

    /**
     * 발급 성공
     */
    SUCCESS(1L),

    /**
     * 중복 발급
     */
    ALREADY_CLAIMED(-1L),

    /**
     * 재고 소진
     */
    STOCK_EXHAUSTED(-2L),

    /**
     * 재고 미등록
     */
    STOCK_NOT_INITIALIZED(-3L);

    private final long code;

    CouponClaimRedisResult(long code) {
        this.code = code;
    }

    /**
     * 결과 코드
     *
     * @return 결과 코드
     */
    public long code() {
        return code;
    }

    /**
     * 결과 코드 변환
     *
     * @param code Lua 반환 코드
     * @return Redis 실행 결과
     */
    public static CouponClaimRedisResult from(long code) {
        for (CouponClaimRedisResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }

        throw new IllegalArgumentException(
                "지원하지 않는 Redis 결과 코드: " + code
        );
    }
}