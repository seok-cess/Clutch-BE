package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.redis.CouponClaimContext;
import com.clutch.coupon.claim.redis.CouponClaimRedisExecutor;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.issuance.CouponIssuanceCommand;
import com.clutch.coupon.contract.issuance.CouponIssuanceResult;
import com.clutch.coupon.contract.issuance.CouponIssuer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Redis 당첨 확정 후 성공 요청만 MySQL에 저장하는 서비스 */
@Service
@RequiredArgsConstructor
public class CouponClaimPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(
            CouponClaimPersistenceService.class
    );
    private static final int COUPON_VALID_DAYS = 7;

    private final CouponClaimRequestRepository couponClaimRequestRepository;
    private final CouponIssuer couponIssuer;
    private final CouponClaimRedisExecutor couponClaimRedisExecutor;
    private final CouponStockRecoveryStateManager recoveryStateManager;
    private final CouponStockStreamService couponStockStreamService;

    /** 실제 쿠폰 저장과 Redis 보상을 하나의 MySQL transaction에 묶는다. */
    @Transactional
    public CouponClaimCreateResponse persist(
            Long userId,
            CouponClaimContext context,
            CouponClaimContext.CouponClaimContextPhase phase
    ) {
        registerRedisCompensation(
                phase.couponEventItemId(),
                context.couponEventOccurrenceId(),
                userId
        );

        CouponClaimRequest claimRequest = CouponClaimRequest.create(
                context.couponEventId(),
                context.couponEventOccurrenceId(),
                phase.couponEventItemId(),
                userId
        );
        CouponClaimRequest savedClaimRequest = couponClaimRequestRepository
                .save(claimRequest);

        Instant issuedAt = Instant.now();
        CouponIssuanceResult issuanceResult = couponIssuer.issue(
                new CouponIssuanceCommand(
                        savedClaimRequest.getId(),
                        userId,
                        context.couponEventId(),
                        context.couponEventOccurrenceId(),
                        phase.couponEventItemId(),
                        phase.discountType(),
                        phase.discountValue(),
                        issuedAt.plus(COUPON_VALID_DAYS, ChronoUnit.DAYS)
                )
        );

        savedClaimRequest.succeed(
                LocalDateTime.ofInstant(issuedAt, ZoneOffset.UTC)
        );
        registerStockNotification(phase.couponEventItemId());

        return CouponClaimCreateResponse.from(
                savedClaimRequest,
                issuanceResult.couponId()
        );
    }

    private void registerRedisCompensation(
            Long couponEventItemId,
            Long couponEventOccurrenceId,
            Long userId
    ) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            rollbackRedisClaim(
                                    couponEventItemId,
                                    couponEventOccurrenceId,
                                    userId
                            );
                        }
                    }
                }
        );
    }

    private void rollbackRedisClaim(
            Long couponEventItemId,
            Long couponEventOccurrenceId,
            Long userId
    ) {
        try {
            couponClaimRedisExecutor.rollback(
                    couponEventItemId,
                    couponEventOccurrenceId,
                    userId
            );
        } catch (DataAccessException exception) {
            recoveryStateManager.markUnavailable();
            log.warn(
                    "Redis 재고 보상 실패: couponEventItemId={}, occurrenceId={}, userId={}",
                    couponEventItemId,
                    couponEventOccurrenceId,
                    userId,
                    exception
            );
        }
    }

    private void registerStockNotification(Long couponEventItemId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            couponStockStreamService.publish(
                                    couponEventItemId
                            );
                        } catch (RuntimeException exception) {
                            log.warn(
                                    "쿠폰 재고 SSE 알림 실패: couponEventItemId={}",
                                    couponEventItemId,
                                    exception
                            );
                        }
                    }
                }
        );
    }
}
