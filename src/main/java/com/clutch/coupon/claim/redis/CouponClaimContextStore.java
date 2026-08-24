package com.clutch.coupon.claim.redis;

import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_REDIS_UNAVAILABLE;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_NOT_INITIALIZED;

/** 쿠폰 발급 회차 컨텍스트의 Redis 저장과 조회 */
@Component
@RequiredArgsConstructor
public class CouponClaimContextStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CouponStockRecoveryStateManager recoveryStateManager;

    /** 회차 오픈 또는 Redis 복구 시 발급 컨텍스트를 저장한다. */
    public void save(CouponClaimContext context) {
        try {
            stringRedisTemplate.opsForValue().set(
                    CouponClaimRedisKeys.context(
                            context.couponEventOccurrenceId()
                    ),
                    objectMapper.writeValueAsString(context)
            );
        } catch (DataAccessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "쿠폰 발급 Redis 컨텍스트 직렬화 실패",
                    exception
            );
        }
    }

    /**
     * Redis 컨텍스트만으로 요청 대상 회차를 검증한다.
     *
     * <p>키가 없으면 DB 조회로 우회하지 않는다. Redis 재구축 전 잘못된 발급을 막기 위해
     * fail-closed로 처리한다.</p>
     */
    public CouponClaimContext get(
            Long couponEventId,
            Long couponEventOccurrenceId
    ) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(
                    CouponClaimRedisKeys.context(
                            couponEventOccurrenceId
                    )
            );
            if (payload == null) {
                recoveryStateManager.markUnavailable();
                throw new CouponClaimException(
                        COUPON_STOCK_NOT_INITIALIZED
                );
            }

            CouponClaimContext context = objectMapper.readValue(
                    payload,
                    CouponClaimContext.class
            );
            if (!couponEventId.equals(context.couponEventId())
                    || !couponEventOccurrenceId.equals(
                    context.couponEventOccurrenceId())) {
                throw new CouponClaimException(
                        COUPON_STOCK_NOT_INITIALIZED
                );
            }
            return context;
        } catch (CouponClaimException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            recoveryStateManager.markUnavailable();
            throw new CouponClaimException(
                    COUPON_REDIS_UNAVAILABLE,
                    exception
            );
        } catch (Exception exception) {
            recoveryStateManager.markUnavailable();
            throw new CouponClaimException(
                    COUPON_REDIS_UNAVAILABLE,
                    exception
            );
        }
    }
}
