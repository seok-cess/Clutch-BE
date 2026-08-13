package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateRequest;
import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_ALREADY_CLAIMED;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_EVENT_ITEM_NOT_FOUND;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_EVENT_NOT_FOUND;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_EVENT_NOT_OPEN;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_EVENT_OCCURRENCE_NOT_FOUND;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_EXHAUSTED;

/**
 * 쿠폰 발급 요청 서비스
 */
@Service
@RequiredArgsConstructor
public class CouponClaimService {

    private final CouponEventRepository couponEventRepository;
    private final CouponEventOccurrenceRepository couponEventOccurrenceRepository;
    private final CouponEventItemRepository couponEventItemRepository;
    private final CouponClaimRequestRepository couponClaimRequestRepository;

    /**
     * 쿠폰 발급 요청 처리
     *
     * @param userId 사용자 식별자
     * @param couponEventId 쿠폰 이벤트 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @param request 쿠폰 발급 요청 DTO
     * @return 쿠폰 발급 요청 생성 응답
     */
    @Transactional
    public CouponClaimCreateResponse claim(
            Long userId,
            Long couponEventId,
            Long couponEventOccurrenceId,
            CouponClaimCreateRequest request
    ) {
        couponEventRepository
                .findById(couponEventId)
                .orElseThrow(() ->
                        new CouponClaimException(
                                COUPON_EVENT_NOT_FOUND
                        )
                );

        CouponEventOccurrence couponEventOccurrence =
                couponEventOccurrenceRepository
                        .findByCouponEventIdAndId(
                                couponEventId,
                                couponEventOccurrenceId
                        )
                        .orElseThrow(() ->
                                new CouponClaimException(
                                        COUPON_EVENT_OCCURRENCE_NOT_FOUND
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

        if (!couponEventOccurrence.isOpenAt(
                LocalDateTime.now()
        )) {
            throw new CouponClaimException(
                    COUPON_EVENT_NOT_OPEN
            );
        }

        boolean alreadyClaimed = couponClaimRequestRepository
                .existsByUserIdAndCouponEventOccurrenceId(
                        userId,
                        couponEventOccurrenceId
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
                        couponEventOccurrenceId,
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
