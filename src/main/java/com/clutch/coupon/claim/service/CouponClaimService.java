package com.clutch.coupon.claim.service;
import com.clutch.coupon.claim.exception.CouponClaimException;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_ALREADY_CLAIMED;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_EVENT_ITEM_NOT_FOUND;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_EVENT_NOT_FOUND;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_EVENT_NOT_OPEN;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_EXHAUSTED;
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
                        new CouponClaimException(
                                COUPON_EVENT_NOT_FOUND
                        )
                );

        CouponEventItem couponEventItem = couponEventItemRepository
                .findByCouponEventIdAndId(
                        couponEventId,
                        request.couponEventItemId()
                )
                .orElseThrow(() ->
                        new CouponClaimException(
                                COUPON_EVENT_ITEM_NOT_FOUND
                        )
                );

        if (!couponEvent.isOpenAt(LocalDateTime.now())) {
            throw new CouponClaimException(
                    COUPON_EVENT_NOT_OPEN
            );
        }

        boolean alreadyClaimed = couponClaimRequestRepository
                .existsByUserIdAndCouponEventItemId(
                        userId,
                        request.couponEventItemId()
                );

        if (alreadyClaimed) {
            throw new CouponClaimException(
                    COUPON_ALREADY_CLAIMED
            );
        }

        if (!couponEventItem.hasRemainingStock()) {
            throw new CouponClaimException(
                    COUPON_STOCK_EXHAUSTED
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
