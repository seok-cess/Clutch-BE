package com.clutch.coupon.test.event.scheduler;

import com.clutch.coupon.test.event.service.CouponEventActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 신청 시간이 지난 수동 테스트 회차를 종료 상태로 정리한다. */
@Component
@RequiredArgsConstructor
public class CouponEventExpirationScheduler {

    private final CouponEventActivationService activationService;

    @Scheduled(
            fixedDelayString =
                    "${coupon.test.event.expiration-interval-ms:1000}"
    )
    public void closeExpiredOccurrences() {
        activationService.closeExpiredOccurrences();
    }
}
