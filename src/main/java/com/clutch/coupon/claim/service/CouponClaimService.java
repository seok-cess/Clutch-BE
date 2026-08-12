package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateRequest;
import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 요청 서비스
 */
@Service
@RequiredArgsConstructor
public class CouponClaimService {

    private final CouponEventRepository couponEventRepository;
    private final CouponEventItemRepository couponEventItemRepository;
    private final CouponClaimRequestRepository couponClaimRequestRepository;

    /**
     * 쿠폰 발급 요청 처리
     *
     * @param userId 사용자 식별자
     * @param couponEventId 쿠폰 이벤트 식별자
     * @param request 쿠폰 발급 요청 DTO
     * @return 쿠폰 발급 요청 생성 응답
     */
    @Transactional
    public CouponClaimCreateResponse claim(
            Long userId,
            Long couponEventId,
            CouponClaimCreateRequest request
    ) {
        CouponEvent couponEvent = couponEventRepository
                .findById(couponEventId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 쿠폰 이벤트입니다."
                        )
                );

        CouponEventItem couponEventItem = couponEventItemRepository
                .findByCouponEventIdAndId(
                        couponEventId,
                        request.couponEventItemId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 쿠폰 이벤트 항목입니다."
                        )
                );

        if (!couponEvent.isOpenAt(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "진행 중인 쿠폰 이벤트가 아닙니다."
            );
        }

        boolean alreadyClaimed = couponClaimRequestRepository
                .existsByUserIdAndCouponEventItemId(
                        userId,
                        request.couponEventItemId()
                );

        if (alreadyClaimed) {
            throw new IllegalStateException(
                    "이미 발급을 요청한 쿠폰입니다."
            );
        }

        if (!couponEventItem.hasRemainingStock()) {
            throw new IllegalStateException(
                    "쿠폰 재고가 소진되었습니다."
            );
        }

        CouponClaimRequest claimRequest =
                CouponClaimRequest.create(
                        couponEventId,
                        request.couponEventItemId(),
                        userId
                );

        CouponClaimRequest savedClaimRequest =
                couponClaimRequestRepository.save(claimRequest);

        couponEventItem.increaseSuccessCount();
        savedClaimRequest.succeed();

        return CouponClaimCreateResponse.from(savedClaimRequest);
    }
}