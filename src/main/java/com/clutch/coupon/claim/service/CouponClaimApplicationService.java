package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.exception.CouponClaimErrorCode;
import com.clutch.coupon.claim.exception.CouponClaimException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 사용자 쿠폰 신청과 운영 통계용 거절 기록을 조율한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponClaimApplicationService {

    private final CouponClaimService couponClaimService;
    private final CouponClaimRejectionPublisher rejectionPublisher;

    /**
     * 기존 발급 결과를 그대로 반환하고 거절만 별도 통계 경로로 전달한다.
     */
    public CouponClaimCreateResponse claim(
            Long userId,
            Long couponEventId,
            Long couponEventOccurrenceId
    ) {
        try {
            return couponClaimService.claim(
                    userId,
                    couponEventId,
                    couponEventOccurrenceId
            );
        } catch (CouponClaimException exception) {
            publishRejectionSafely(
                    couponEventId,
                    couponEventOccurrenceId,
                    exception.getErrorCode()
            );
            throw exception;
        }
    }

    private void publishRejectionSafely(
            Long couponEventId,
            Long couponEventOccurrenceId,
            CouponClaimErrorCode errorCode
    ) {
        try {
            rejectionPublisher.publish(
                    couponEventId,
                    couponEventOccurrenceId,
                    errorCode
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "쿠폰 신청 거절 통계 전달 실패: eventId={}, occurrenceId={}, reason={}",
                    couponEventId,
                    couponEventOccurrenceId,
                    errorCode,
                    exception
            );
        }
    }
}
