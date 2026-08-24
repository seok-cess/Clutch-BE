package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.redis.CouponClaimContext;
import com.clutch.coupon.claim.redis.CouponClaimRedisExecutor;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.issuance.CouponIssuanceCommand;
import com.clutch.coupon.contract.issuance.CouponIssuanceResult;
import com.clutch.coupon.contract.issuance.CouponIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis 당첨 후 MySQL 저장 실패 시 재고를 보상하는 테스트 */
@ExtendWith(MockitoExtension.class)
class CouponClaimPersistenceServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EVENT_ID = 10L;
    private static final Long OCCURRENCE_ID = 15L;
    private static final Long ITEM_ID = 20L;

    @Mock
    private CouponClaimRequestRepository couponClaimRequestRepository;
    @Mock
    private CouponIssuer couponIssuer;
    @Mock
    private CouponClaimRedisExecutor couponClaimRedisExecutor;
    @Mock
    private CouponStockRecoveryStateManager recoveryStateManager;
    @Mock
    private CouponStockStreamService couponStockStreamService;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 저장실패시Redis당첨을보상한다() {
        CouponClaimPersistenceService service = service();
        TransactionSynchronizationManager.initSynchronization();
        when(couponClaimRequestRepository.save(any(CouponClaimRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(couponIssuer.issue(any(CouponIssuanceCommand.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.persist(
                USER_ID,
                context(),
                phase()
        )).isInstanceOf(DataIntegrityViolationException.class);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                ));

        verify(couponClaimRedisExecutor).rollback(
                ITEM_ID,
                OCCURRENCE_ID,
                USER_ID
        );
    }

    @Test
    void 커밋후에만재고알림을전송한다() {
        CouponClaimPersistenceService service = service();
        TransactionSynchronizationManager.initSynchronization();
        when(couponClaimRequestRepository.save(any(CouponClaimRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(couponIssuer.issue(any(CouponIssuanceCommand.class)))
                .thenReturn(new CouponIssuanceResult(100L));

        service.persist(USER_ID, context(), phase());

        ArgumentCaptor<CouponIssuanceCommand> commandCaptor =
                ArgumentCaptor.forClass(CouponIssuanceCommand.class);
        verify(couponIssuer).issue(commandCaptor.capture());
        assertThat(commandCaptor.getValue().couponEventItemId())
                .isEqualTo(ITEM_ID);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(couponStockStreamService).publish(ITEM_ID);
    }

    private CouponClaimPersistenceService service() {
        return new CouponClaimPersistenceService(
                couponClaimRequestRepository,
                couponIssuer,
                couponClaimRedisExecutor,
                recoveryStateManager,
                couponStockStreamService
        );
    }

    private CouponClaimContext context() {
        long now = Instant.now().toEpochMilli();
        return new CouponClaimContext(
                EVENT_ID,
                OCCURRENCE_ID,
                now - 60,
                now + 60,
                List.of(phase())
        );
    }

    private CouponClaimContext.CouponClaimContextPhase phase() {
        return new CouponClaimContext.CouponClaimContextPhase(
                0,
                ITEM_ID,
                "RATE",
                new BigDecimal("20.00")
        );
    }
}
