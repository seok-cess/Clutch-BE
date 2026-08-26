package com.clutch.coupon.claim.service;

import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.wallet.repository.CouponEventItemIssuedCount;
import com.clutch.wallet.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 실제 쿠폰 발급 결과를 성공 수량 집계에 반영한다.
 *
 * <p>발급 요청은 Redis에서 재고를 확정하고 MySQL에는 개별 쿠폰만 저장한다. 이 작업은
 * 공통 {@code coupon_event_item} 행 갱신을 요청 경로에서 분리해 잠금 경쟁을 막는다.</p>
 */
@Component
@RequiredArgsConstructor
public class CouponSuccessCountSynchronizer {

    private final CouponEventItemRepository couponEventItemRepository;
    private final UserCouponRepository userCouponRepository;

    /** 실제 쿠폰 수로 성공 집계를 단일 스케줄러에서 보정한다. */
    @Scheduled(
            fixedDelayString =
                    "${coupon.success-count-sync.interval-ms:5000}"
    )
    @Transactional
    public void synchronize() {
        Map<Long, Long> issuedCounts = new HashMap<>();
        for (CouponEventItemIssuedCount count : userCouponRepository
                .countIssuedCouponsGroupByEventItem()) {
            issuedCounts.put(
                    count.getCouponEventItemId(),
                    count.getIssuedCouponCount()
            );
        }

        for (CouponEventItem item : couponEventItemRepository.findAll()) {
            long issuedCouponCount = issuedCounts.getOrDefault(
                    item.getId(),
                    0L
            );

            if (item.getSuccessCount() != issuedCouponCount) {
                item.synchronizeSuccessCount(
                        Math.toIntExact(issuedCouponCount)
                );
            }
        }
    }
}
