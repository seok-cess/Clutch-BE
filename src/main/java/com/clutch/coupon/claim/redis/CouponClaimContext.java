package com.clutch.coupon.claim.redis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 발급 요청을 Redis에서 선판단하기 위해 회차 오픈 시 준비하는 불변 정보다.
 *
 * <p>품절 및 중복 요청은 이 정보를 사용해 MySQL 조회 없이 종료한다. 실제 쿠폰 저장은
 * Redis Lua가 성공을 반환한 경우에만 수행한다.</p>
 */
public record CouponClaimContext(
        Long couponEventId,
        Long couponEventOccurrenceId,
        long openedAtEpochMilli,
        long expiresAtEpochMilli,
        List<CouponClaimContextPhase> phases
) {

    /** 현재 시각에 발급 가능한 단계를 찾는다. */
    public Optional<CouponClaimContextPhase> findActivePhase(
            Instant currentTime
    ) {
        long currentEpochMilli = currentTime.toEpochMilli();
        if (currentEpochMilli < openedAtEpochMilli
                || currentEpochMilli >= expiresAtEpochMilli) {
            return Optional.empty();
        }

        long elapsedSeconds = (currentEpochMilli - openedAtEpochMilli)
                / 1_000L;
        return phases.stream()
                .filter(phase -> phase.openOffsetSeconds()
                        <= elapsedSeconds)
                .max(Comparator.comparingInt(
                        CouponClaimContextPhase::openOffsetSeconds
                ));
    }

    /** 단계별 발급 항목과 혜택 스냅샷 */
    public record CouponClaimContextPhase(
            int openOffsetSeconds,
            Long couponEventItemId,
            String discountType,
            BigDecimal discountValue
    ) {
    }
}
