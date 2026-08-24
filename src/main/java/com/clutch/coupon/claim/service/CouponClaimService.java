package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.outbox.CouponBenefitSnapshot;
import com.clutch.coupon.claim.outbox.CouponBenefitSnapshotRepository;
import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.contract.issuance.CouponIssuanceCommand;
import com.clutch.coupon.contract.issuance.CouponIssuanceResult;
import com.clutch.coupon.contract.issuance.CouponIssuer;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.redis.CouponClaimRedisExecutor;
import com.clutch.coupon.claim.redis.CouponClaimRedisResult;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DataAccessException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;


import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.*;

/**
 * 쿠폰 발급 요청 서비스
 */
@Service
@RequiredArgsConstructor
public class CouponClaimService {

    private static final Logger log =
            LoggerFactory.getLogger(CouponClaimService.class);

    private static final int COUPON_VALID_DAYS = 7;

    private final CouponEventRepository couponEventRepository;
    private final CouponEventOccurrenceRepository couponEventOccurrenceRepository;
    private final CouponClaimRequestRepository couponClaimRequestRepository;
    private final CouponClaimItemSelector couponClaimItemSelector;
    private final CouponClaimRedisExecutor couponClaimRedisExecutor;
    private final CouponStockRecoveryStateManager recoveryStateManager;
    private final CouponEventItemRepository couponEventItemRepository;

    private final CouponBenefitSnapshotRepository
            couponBenefitSnapshotRepository;

    private final CouponIssuer couponIssuer;
    private final CouponStockStreamService couponStockStreamService;

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

        recoveryStateManager.requireReady();

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
        LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);

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

        CouponBenefitSnapshot benefitSnapshot =
                couponBenefitSnapshotRepository
                        .findByCouponEventItemId(
                                couponEventItem.getId()
                        )
                        .orElseThrow(() ->
                                new CouponClaimException(
                                        COUPON_BENEFIT_NOT_FOUND
                                )
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

        Instant issuedAt = Instant.now();

        CouponIssuanceResult issuanceResult =
                couponIssuer.issue(
                        new CouponIssuanceCommand(
                                savedClaimRequest.getId(),
                                userId,
                                couponEventId,
                                couponEventOccurrenceId,
                                couponEventItem.getId(),
                                benefitSnapshot.discountType(),
                                benefitSnapshot.discountValue(),
                                issuedAt.plus(
                                        COUPON_VALID_DAYS,
                                        ChronoUnit.DAYS
                                )
                        )
                );

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

        savedClaimRequest.succeed(
                LocalDateTime.ofInstant(
                        issuedAt,
                        ZoneOffset.UTC
                )
        );

        registerStockNotification(couponEventItem.getId());

        return CouponClaimCreateResponse.from(
                savedClaimRequest,
                issuanceResult.couponId()
        );
    }

    /** 트랜잭션 커밋 후 재고 알림 등록 */
    private void registerStockNotification(Long couponEventItemId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishStockSafely(couponEventItemId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publishStockSafely(couponEventItemId);
                    }
                }
        );
    }

    /** 발급 결과와 분리된 재고 알림 전송 */
    private void publishStockSafely(Long couponEventItemId) {
        try {
            couponStockStreamService.publish(couponEventItemId);
        } catch (RuntimeException exception) {
            log.warn(
                    "쿠폰 재고 SSE 알림 실패: couponEventItemId={}",
                    couponEventItemId,
                    exception
            );
        }
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
                                    try {
                                        couponClaimRedisExecutor.rollback(
                                                    couponEventItemId,
                                                    couponEventOccurrenceId,
                                                    userId
                                        );
                                    } catch (DataAccessException exception) {
                                        recoveryStateManager.markUnavailable();
                                        log.warn(
                                                "Redis 재고 보상 실패: "
                                                        + "couponEventItemId={}, "
                                                        + "occurrenceId={}, userId={}",
                                                couponEventItemId,
                                                couponEventOccurrenceId,
                                                userId,
                                                exception
                                        );
                                    }
                                }
                            }
                        }
                );
    }

    /**
     * 쿠폰 발급 Redis 실행
     *
     * @param couponEventItemId       쿠폰 이벤트 항목 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @param userId                  사용자 식별자
     * @return 쿠폰 발급 Redis 실행 결과
     */
    private CouponClaimRedisResult executeRedisClaim(
            Long couponEventItemId,
            Long couponEventOccurrenceId,
            Long userId
    ) {
        try {
            CouponClaimRedisResult result =
                    couponClaimRedisExecutor.claim(
                    couponEventItemId,
                    couponEventOccurrenceId,
                    userId
            );

            if (result == CouponClaimRedisResult.STOCK_NOT_INITIALIZED) {
                recoveryStateManager.markUnavailable();
            }
            return result;
        } catch (DataAccessException exception) {
            recoveryStateManager.markUnavailable();
            throw new CouponClaimException(
                    COUPON_REDIS_UNAVAILABLE,
                    exception
            );
        }
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
