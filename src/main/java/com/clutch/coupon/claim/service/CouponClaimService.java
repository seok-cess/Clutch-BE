package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.redis.CouponClaimRedisExecutor;
import com.clutch.coupon.claim.redis.CouponClaimRedisResult;
import com.clutch.coupon.claim.redis.CouponStockInitializer;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.*;

/**
 * 쿠폰 발급 요청 서비스
 */
@Service
@RequiredArgsConstructor
public class CouponClaimService {

    private final CouponEventRepository couponEventRepository;
    private final CouponEventOccurrenceRepository couponEventOccurrenceRepository;
    private final CouponClaimRequestRepository couponClaimRequestRepository;
    private final CouponClaimItemSelector couponClaimItemSelector;
    private final CouponStockInitializer couponStockInitializer;
    private final CouponClaimRedisExecutor couponClaimRedisExecutor;
    private final CouponEventItemRepository couponEventItemRepository;

    /**
     * 쿠폰 발급 요청 처리
     *
     * @param userId                  사용자 식별자
     * @param couponEventId           쿠폰 이벤트 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @return 쿠폰 발급 요청 생성 응답
     */
    @Transactional
    public CouponClaimCreateResponse claim(
            Long userId,
            Long couponEventId,
            Long couponEventOccurrenceId
    ){

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
        LocalDateTime currentTime = LocalDateTime.now();

        if (!couponEventOccurrence.isOpenAt(currentTime)) {
            throw new CouponClaimException(
                    COUPON_EVENT_NOT_OPEN
            );
        }
        CouponEventItem couponEventItem =
                couponClaimItemSelector.select(
                        couponEventId,
                        couponEventOccurrence,
                        currentTime
                );

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

        CouponClaimRedisResult redisResult =
                executeRedisClaim(
                        couponEventId,
                        couponEventItem.getId(),
                        couponEventOccurrenceId,
                        userId
                );

        validateRedisResult(redisResult);

        registerRedisCompensation(
                couponEventItem.getId(),
                couponEventOccurrenceId,
                userId
        );


        CouponClaimRequest claimRequest =
                CouponClaimRequest.create(
                        couponEventId,
                        couponEventOccurrenceId,
                        couponEventItem.getId(),
                        userId
                );

        CouponClaimRequest savedClaimRequest =
                couponClaimRequestRepository.save(claimRequest);

        int updatedRows =
                couponEventItemRepository
                        .increaseSuccessCountAtomically(
                                couponEventItem.getId()
                        );

        if (updatedRows != 1) {
            throw new CouponClaimException(
                    COUPON_STOCK_EXHAUSTED
            );
        }
        savedClaimRequest.succeed();

        return CouponClaimCreateResponse.from(savedClaimRequest);
    }

    /**
     * Redis 발급 보상 등록
     *
     * @param couponEventItemId 쿠폰 이벤트 항목 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @param userId 사용자 식별자
     */
    private void registerRedisCompensation(
            Long couponEventItemId,
            Long couponEventOccurrenceId,
            Long userId
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(
                                    int status
                            ) {
                                if (status
                                        == STATUS_ROLLED_BACK) {
                                    couponClaimRedisExecutor
                                            .rollback(
                                                    couponEventItemId,
                                                    couponEventOccurrenceId,
                                                    userId
                                            );
                                }
                            }
                        }
                );
    }

    /**
     * 쿠폰 발급 Redis 실행
     *
     * @param couponEventId           쿠폰 이벤트 식별자
     * @param couponEventItemId       쿠폰 이벤트 항목 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @param userId                  사용자 식별자
     * @return 쿠폰 발급 Redis 실행 결과
     */
    private CouponClaimRedisResult executeRedisClaim(
            Long couponEventId,
            Long couponEventItemId,
            Long couponEventOccurrenceId,
            Long userId
    ) {
        CouponClaimRedisResult result =
                couponClaimRedisExecutor.claim(
                        couponEventItemId,
                        couponEventOccurrenceId,
                        userId
                );

        if (result
                == CouponClaimRedisResult
                .STOCK_NOT_INITIALIZED) {
            couponStockInitializer.initialize(
                    couponEventId
            );

            result = couponClaimRedisExecutor.claim(
                    couponEventItemId,
                    couponEventOccurrenceId,
                    userId
            );
        }

        return result;
    }

    /**
     * 쿠폰 발급 Redis 결과 검증
     *
     * @param result 쿠폰 발급 Redis 실행 결과
     */
    private void validateRedisResult(
            CouponClaimRedisResult result
    ) {
        switch (result) {
            case SUCCESS -> {
            }

            case ALREADY_CLAIMED -> throw new CouponClaimException(
                    COUPON_ALREADY_CLAIMED
            );

            case STOCK_EXHAUSTED -> throw new CouponClaimException(
                    COUPON_STOCK_EXHAUSTED
            );

            case STOCK_NOT_INITIALIZED -> throw new CouponClaimException(
                    COUPON_STOCK_NOT_INITIALIZED
            );
        }
    }
}
