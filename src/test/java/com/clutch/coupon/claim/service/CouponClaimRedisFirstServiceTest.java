package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.exception.CouponClaimErrorCode;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.outbox.CouponBenefitSnapshotRepository;
import com.clutch.coupon.claim.redis.CouponClaimContext;
import com.clutch.coupon.claim.redis.CouponClaimContextStore;
import com.clutch.coupon.claim.redis.CouponClaimRedisExecutor;
import com.clutch.coupon.claim.redis.CouponClaimRedisResult;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.issuance.CouponIssuer;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 품절·중복 요청이 MySQL에 접근하지 않는 쿠폰 발급 선판단 테스트 */
@ExtendWith(MockitoExtension.class)
class CouponClaimRedisFirstServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EVENT_ID = 10L;
    private static final Long OCCURRENCE_ID = 15L;
    private static final Long ITEM_ID = 20L;

    @Mock
    private CouponEventRepository couponEventRepository;
    @Mock
    private CouponEventOccurrenceRepository couponEventOccurrenceRepository;
    @Mock
    private CouponClaimRequestRepository couponClaimRequestRepository;
    @Mock
    private CouponClaimItemSelector couponClaimItemSelector;
    @Mock
    private CouponBenefitSnapshotRepository benefitSnapshotRepository;
    @Mock
    private CouponIssuer couponIssuer;
    @Mock
    private CouponStockStreamService couponStockStreamService;
    @Mock
    private CouponClaimContextStore couponClaimContextStore;
    @Mock
    private CouponClaimRedisExecutor couponClaimRedisExecutor;
    @Mock
    private CouponStockRecoveryStateManager recoveryStateManager;
    @Mock
    private CouponClaimPersistenceService couponClaimPersistenceService;

    private CouponClaimService couponClaimService;
    private CouponClaimContext context;
    private CouponClaimContext.CouponClaimContextPhase phase;

    @BeforeEach
    void setUp() {
        couponClaimService = new CouponClaimService(
                couponEventRepository,
                couponEventOccurrenceRepository,
                couponClaimRequestRepository,
                couponClaimItemSelector,
                couponClaimRedisExecutor,
                recoveryStateManager,
                benefitSnapshotRepository,
                couponIssuer,
                couponStockStreamService,
                couponClaimContextStore,
                couponClaimPersistenceService
        );
        phase = new CouponClaimContext.CouponClaimContextPhase(
                0,
                ITEM_ID,
                "RATE",
                new BigDecimal("20.00")
        );
        long now = Instant.now().toEpochMilli();
        context = new CouponClaimContext(
                EVENT_ID,
                OCCURRENCE_ID,
                now - 60,
                now + 60,
                List.of(phase)
        );
        when(couponClaimContextStore.get(EVENT_ID, OCCURRENCE_ID))
                .thenReturn(context);
    }

    @Test
    void Redis에서당첨된요청만MySql저장서비스로전달한다() {
        CouponClaimCreateResponse expected =
                new CouponClaimCreateResponse(
                        100L,
                        200L,
                        EVENT_ID,
                        OCCURRENCE_ID,
                        ITEM_ID,
                        ClaimRequestStatus.SUCCEEDED,
                        LocalDateTime.now()
                );
        when(couponClaimRedisExecutor.claim(
                ITEM_ID,
                OCCURRENCE_ID,
                USER_ID
        )).thenReturn(CouponClaimRedisResult.SUCCESS);
        when(couponClaimPersistenceService.persist(
                eq(USER_ID),
                eq(context),
                eq(phase)
        )).thenReturn(expected);

        CouponClaimCreateResponse response = couponClaimService.claim(
                USER_ID,
                EVENT_ID,
                OCCURRENCE_ID
        );

        assertThat(response).isEqualTo(expected);
        verify(couponClaimPersistenceService).persist(
                USER_ID,
                context,
                phase
        );
        verifyNoInteractions(
                couponEventRepository,
                couponEventOccurrenceRepository,
                couponClaimRequestRepository,
                couponClaimItemSelector,
                benefitSnapshotRepository,
                couponIssuer
        );
    }

    @Test
    void Redis에서품절된요청은MySql에접근하지않는다() {
        when(couponClaimRedisExecutor.claim(
                ITEM_ID,
                OCCURRENCE_ID,
                USER_ID
        )).thenReturn(CouponClaimRedisResult.STOCK_EXHAUSTED);

        assertThatThrownBy(() -> couponClaimService.claim(
                USER_ID,
                EVENT_ID,
                OCCURRENCE_ID
        )).isInstanceOfSatisfying(
                CouponClaimException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CouponClaimErrorCode.COUPON_STOCK_EXHAUSTED)
        );

        verify(couponClaimPersistenceService, never()).persist(
                any(), any(), any()
        );
        verifyNoInteractions(
                couponEventRepository,
                couponEventOccurrenceRepository,
                couponClaimRequestRepository,
                couponClaimItemSelector,
                benefitSnapshotRepository,
                couponIssuer
        );
    }

    @Test
    void Redis에서중복으로잘린요청은MySql에접근하지않는다() {
        when(couponClaimRedisExecutor.claim(
                ITEM_ID,
                OCCURRENCE_ID,
                USER_ID
        )).thenReturn(CouponClaimRedisResult.ALREADY_CLAIMED);

        assertThatThrownBy(() -> couponClaimService.claim(
                USER_ID,
                EVENT_ID,
                OCCURRENCE_ID
        )).isInstanceOfSatisfying(
                CouponClaimException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CouponClaimErrorCode.COUPON_ALREADY_CLAIMED)
        );

        verify(couponClaimPersistenceService, never()).persist(
                any(), any(), any()
        );
        verifyNoInteractions(couponClaimRequestRepository, couponIssuer);
    }

    @Test
    void Redis컨텍스트가없으면MySql로우회하지않는다() {
        when(couponClaimContextStore.get(EVENT_ID, OCCURRENCE_ID))
                .thenThrow(new CouponClaimException(
                        CouponClaimErrorCode.COUPON_STOCK_NOT_INITIALIZED
                ));

        assertThatThrownBy(() -> couponClaimService.claim(
                USER_ID,
                EVENT_ID,
                OCCURRENCE_ID
        )).isInstanceOf(CouponClaimException.class);

        verifyNoInteractions(
                couponClaimRedisExecutor,
                couponClaimPersistenceService,
                couponClaimRequestRepository,
                couponIssuer
        );
    }

    @Test
    void Redis장애면저장전에발급을차단한다() {
        when(couponClaimRedisExecutor.claim(
                ITEM_ID,
                OCCURRENCE_ID,
                USER_ID
        )).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> couponClaimService.claim(
                USER_ID,
                EVENT_ID,
                OCCURRENCE_ID
        )).isInstanceOfSatisfying(
                CouponClaimException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CouponClaimErrorCode.COUPON_REDIS_UNAVAILABLE)
        );

        verify(recoveryStateManager).markUnavailable();
        verifyNoInteractions(couponClaimPersistenceService);
    }
}
