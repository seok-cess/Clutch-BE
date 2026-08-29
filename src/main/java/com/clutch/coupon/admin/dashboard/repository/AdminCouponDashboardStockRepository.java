package com.clutch.coupon.admin.dashboard.repository;

import com.clutch.coupon.claim.recovery.CouponStockRecoveryState;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 페이지 운영 홈의 재고 소진 이벤트를 Redis MGET으로 판정한다.
 *
 * <p>항목별 Redis 요청을 반복하지 않으며, 정상 재고 0과 키 누락·조회 장애를
 * 구분해 불확실한 재고를 소진으로 표시하지 않는다.</p>
 */
@Repository
@RequiredArgsConstructor
public class AdminCouponDashboardStockRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final CouponStockRecoveryStateManager recoveryStateManager;

    /** 관리자 운영 홈에서 잔여 재고 합계가 0인 진행 중 이벤트 ID를 조회한다. */
    public Set<Long> findSoldOutEventIds(List<OpenEventItemRow> items) {
        if (items.isEmpty()) {
            return Set.of();
        }
        if (recoveryStateManager.current() != CouponStockRecoveryState.READY) {
            throw new AdminCouponDashboardStockException(
                    "쿠폰 재고 복구 상태가 정상이 아닙니다."
            );
        }

        List<String> keys = items.stream()
                .map(item -> CouponClaimRedisKeys.stock(
                        item.couponEventItemId()
                ))
                .toList();
        List<String> values;
        try {
            values = stringRedisTemplate.opsForValue().multiGet(keys);
        } catch (DataAccessException exception) {
            recoveryStateManager.markUnavailable();
            throw new AdminCouponDashboardStockException(
                    "관리자 운영 홈에서 쿠폰 재고를 조회할 수 없습니다.",
                    exception
            );
        }

        if (values == null || values.size() != items.size()
                || values.stream().anyMatch(value -> value == null)) {
            recoveryStateManager.markUnavailable();
            throw new AdminCouponDashboardStockException(
                    "관리자 운영 홈의 쿠폰 재고 키가 누락되었습니다."
            );
        }

        Map<Long, Long> remainingByEvent = new HashMap<>();
        Set<Long> eventsWithItems = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            OpenEventItemRow item = items.get(index);
            long remaining = parseRemainingStock(values.get(index));
            eventsWithItems.add(item.couponEventId());
            remainingByEvent.merge(
                    item.couponEventId(),
                    remaining,
                    Long::sum
            );
        }

        Set<Long> soldOutEventIds = new HashSet<>();
        for (Long eventId : eventsWithItems) {
            if (remainingByEvent.getOrDefault(eventId, 0L) == 0L) {
                soldOutEventIds.add(eventId);
            }
        }
        return Set.copyOf(soldOutEventIds);
    }

    private long parseRemainingStock(String value) {
        try {
            long remaining = Long.parseLong(value);
            if (remaining < 0L) {
                throw new NumberFormatException("negative stock");
            }
            return remaining;
        } catch (NumberFormatException exception) {
            throw new AdminCouponDashboardStockException(
                    "관리자 운영 홈의 쿠폰 재고 값이 올바르지 않습니다.",
                    exception
            );
        }
    }
}
