package com.clutch.coupon.claim.result;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode
        .COUPON_CLAIM_REQUEST_NOT_FOUND;

/**
 * 쿠폰 생성 결과 처리 서비스
 */
@Service
@RequiredArgsConstructor
public class CouponIssueResultService {

    private static final int SUPPORTED_EVENT_VERSION = 1;

    private final CouponClaimRequestRepository
            claimRequestRepository;

    /**
     * 쿠폰 생성 결과 처리
     *
     * @param event 쿠폰 생성 결과 이벤트
     */
    @Transactional
    public void handle(
            CouponIssueResultEvent event
    ) {
        validateVersion(event);

        CouponClaimRequest claimRequest =
                claimRequestRepository
                        .findById(event.claimId())
                        .orElseThrow(() ->
                                new CouponClaimException(
                                        COUPON_CLAIM_REQUEST_NOT_FOUND
                                )
                        );

        if (!claimRequest.isPending()) {
            return;
        }

        LocalDateTime completedAt =
                LocalDateTime.ofInstant(
                        event.occurredAt(),
                        ZoneOffset.UTC
                );

        switch (event.status()) {
            case SUCCEEDED ->
                    claimRequest.succeed(completedAt);

            case FAILED ->
                    claimRequest.fail(
                            event.failureReason(),
                            completedAt
                    );
        }
    }

    /**
     * 이벤트 버전 검증
     *
     * @param event 쿠폰 생성 결과 이벤트
     */
    private void validateVersion(
            CouponIssueResultEvent event
    ) {
        if (event.eventVersion()
                != SUPPORTED_EVENT_VERSION) {
            throw new IllegalArgumentException(
                    "지원하지 않는 쿠폰 결과 이벤트 버전"
            );
        }
    }
}