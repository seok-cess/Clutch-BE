package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponStockResponse;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_NOT_INITIALIZED;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_READ_FAILED;

/** Redis 쿠폰 재고 조회 */
@Service
@RequiredArgsConstructor
public class CouponStockService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 쿠폰 이벤트 항목 Redis 재고 조회 */
    public CouponStockResponse getStock(Long couponEventItemId) {
        String value;
        try {
            value = stringRedisTemplate.opsForValue().get(
                    CouponClaimRedisKeys.stock(couponEventItemId)
            );
        } catch (DataAccessException exception) {
            throw new CouponClaimException(
                    COUPON_STOCK_READ_FAILED,
                    exception
            );
        }

        if (value == null) {
            throw new CouponClaimException(
                    COUPON_STOCK_NOT_INITIALIZED
            );
        }

        try {
            long remainingStock = Long.parseLong(value);
            if (remainingStock < 0L) {
                throw new NumberFormatException("negative stock");
            }
            return CouponStockResponse.of(
                    couponEventItemId,
                    remainingStock
            );
        } catch (NumberFormatException exception) {
            throw new CouponClaimException(
                    COUPON_STOCK_READ_FAILED,
                    exception
            );
        }
    }
}
