package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.outbox.CouponBenefitSnapshot;
import com.clutch.coupon.claim.outbox.CouponBenefitSnapshotRepository;
import com.clutch.coupon.claim.outbox.CouponClaimOutboxWriter;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import com.clutch.coupon.claim.redis.CouponClaimRedisExecutor;
import com.clutch.coupon.claim.redis.CouponClaimRedisResult;
import com.clutch.coupon.claim.redis.CouponStockInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_EVENT_ITEM_NOT_AVAILABLE;
/**
 * 쿠폰 발급 요청 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponClaimServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long COUPON_EVENT_ID = 10L;
    private static final Long COUPON_EVENT_OCCURRENCE_ID = 15L;
    private static final Long COUPON_EVENT_ITEM_ID = 20L;
    private static final CouponBenefitSnapshot BENEFIT_SNAPSHOT =
            new CouponBenefitSnapshot(
                    "RATE",
                    new BigDecimal("20.00")
            );

    @Mock
    private CouponEventRepository couponEventRepository;

    @Mock
    private CouponEventOccurrenceRepository couponEventOccurrenceRepository;

    @Mock
    private CouponEventItemRepository couponEventItemRepository;

    @Mock
    private CouponClaimRequestRepository couponClaimRequestRepository;

    @Mock
    private CouponBenefitSnapshotRepository
            couponBenefitSnapshotRepository;

    @Mock
    private CouponClaimOutboxWriter couponClaimOutboxWriter;

    @Mock
    private CouponClaimItemSelector couponClaimItemSelector;

    @Mock
    private CouponStockInitializer couponStockInitializer;

    @Mock
    private CouponClaimRedisExecutor couponClaimRedisExecutor;

    @Mock
    private CouponEvent couponEvent;

    @Mock
    private CouponEventOccurrence couponEventOccurrence;

    @Mock
    private CouponEventItem couponEventItem;

    @InjectMocks
    private CouponClaimService couponClaimService;

    /**
     * 정상 쿠폰 발급 요청 검증
     */
    @Test
    void claimSucceeds() {
        // given
        givenOpenEventAndItem();

        when(couponEventItem.getId())
                .thenReturn(COUPON_EVENT_ITEM_ID);

        when(couponBenefitSnapshotRepository
                .findByCouponEventItemId(
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(Optional.of(BENEFIT_SNAPSHOT));

        when(couponClaimRequestRepository
                .existsByUserIdAndCouponEventOccurrenceId(
                        USER_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .thenReturn(false);

        when(couponClaimRedisExecutor.claim(
                COUPON_EVENT_ITEM_ID,
                COUPON_EVENT_OCCURRENCE_ID,
                USER_ID
        ))
                .thenReturn(CouponClaimRedisResult.SUCCESS);

        when(couponEventItemRepository
                .increaseSuccessCountAtomically(
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(1);

        when(couponClaimRequestRepository
                .save(any(CouponClaimRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));


        // when
        CouponClaimCreateResponse response =
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                );

        // then
        assertThat(response.couponEventId())
                .isEqualTo(COUPON_EVENT_ID);
        assertThat(response.couponEventOccurrenceId())
                .isEqualTo(COUPON_EVENT_OCCURRENCE_ID);
        assertThat(response.couponEventItemId())
                .isEqualTo(COUPON_EVENT_ITEM_ID);
        assertThat(response.requestStatus())
                .isEqualTo(ClaimRequestStatus.PENDING);

        ArgumentCaptor<CouponClaimRequest> captor =
                ArgumentCaptor.forClass(
                        CouponClaimRequest.class
                );

        verify(couponClaimRequestRepository)
                .save(captor.capture());

        CouponClaimRequest savedClaimRequest =
                captor.getValue();

        assertThat(savedClaimRequest.getUserId())
                .isEqualTo(USER_ID);
        assertThat(savedClaimRequest.getCouponEventOccurrenceId())
                .isEqualTo(COUPON_EVENT_OCCURRENCE_ID);
        assertThat(savedClaimRequest.getRequestStatus())
                .isEqualTo(ClaimRequestStatus.PENDING);

        verify(couponClaimOutboxWriter)
                .writeAcceptedEvent(
                        eq(savedClaimRequest),
                        eq(BENEFIT_SNAPSHOT),
                        any(Instant.class)
                );

        verify(couponClaimRedisExecutor).claim(
                COUPON_EVENT_ITEM_ID,
                COUPON_EVENT_OCCURRENCE_ID,
                USER_ID
        );

        verify(couponEventItemRepository)
                .increaseSuccessCountAtomically(
                        COUPON_EVENT_ITEM_ID
                );
    }

    /**
     * 미존재 쿠폰 이벤트 실패 검증
     */
    @Test
    void claimFailsWhenEventDoesNotExist() {
        // given
        when(couponEventRepository.findById(COUPON_EVENT_ID))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .isInstanceOf(CouponClaimException.class);

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * 미존재 쿠폰 이벤트 회차 실패 검증
     */
    @Test
    void claimFailsWhenEventOccurrenceDoesNotExist() {
        // given
        when(couponEventRepository.findById(COUPON_EVENT_ID))
                .thenReturn(Optional.of(couponEvent));

        when(couponEventOccurrenceRepository
                .findByCouponEventIdAndId(
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .isInstanceOf(CouponClaimException.class);

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * 발급 가능 쿠폰 이벤트 항목 없음 검증
     */
    @Test
    void claimFailsWhenEventItemIsNotAvailable() {
        // given
        when(couponEventRepository.findById(COUPON_EVENT_ID))
                .thenReturn(Optional.of(couponEvent));

        when(couponEventOccurrenceRepository
                .findByCouponEventIdAndId(
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .thenReturn(Optional.of(couponEventOccurrence));

        when(couponEventOccurrence
                .isOpenAt(any(LocalDateTime.class)))
                .thenReturn(true);

        when(couponClaimItemSelector.select(
                any(Long.class),
                any(CouponEventOccurrence.class),
                any(LocalDateTime.class)
        ))
                .thenThrow(
                        new CouponClaimException(
                                COUPON_EVENT_ITEM_NOT_AVAILABLE
                        )
                );

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .isInstanceOf(CouponClaimException.class);

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * 종료 쿠폰 이벤트 실패 검증
     */
    @Test
    void claimFailsWhenEventIsClosed() {
        // given
        when(couponEventRepository.findById(COUPON_EVENT_ID))
                .thenReturn(Optional.of(couponEvent));

        when(couponEventOccurrenceRepository
                .findByCouponEventIdAndId(
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .thenReturn(Optional.of(couponEventOccurrence));

        when(couponEventOccurrence.isOpenAt(any(LocalDateTime.class)))
                .thenReturn(false);

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .isInstanceOf(CouponClaimException.class);

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * 중복 쿠폰 발급 요청 실패 검증
     */
    @Test
    void claimFailsWhenAlreadyClaimed() {
        // given
        givenOpenEventAndItem();

        when(couponEventItem.getId())
                .thenReturn(COUPON_EVENT_ITEM_ID);

        when(couponBenefitSnapshotRepository
                .findByCouponEventItemId(
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(Optional.of(BENEFIT_SNAPSHOT));

        when(couponClaimRequestRepository
                .existsByUserIdAndCouponEventOccurrenceId(
                        USER_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .thenReturn(true);

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .isInstanceOf(CouponClaimException.class);

        verify(couponEventItem, never())
                .increaseSuccessCount();

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * Redis 쿠폰 재고 소진 검증
     */
    @Test
    void claimFailsWhenStockIsEmpty() {
        // given
        givenOpenEventAndItem();

        when(couponEventItem.getId())
                .thenReturn(COUPON_EVENT_ITEM_ID);

        when(couponBenefitSnapshotRepository
                .findByCouponEventItemId(
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(Optional.of(BENEFIT_SNAPSHOT));

        when(couponClaimRequestRepository
                .existsByUserIdAndCouponEventOccurrenceId(
                        USER_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .thenReturn(false);

        when(couponClaimRedisExecutor.claim(
                COUPON_EVENT_ITEM_ID,
                COUPON_EVENT_OCCURRENCE_ID,
                USER_ID
        ))
                .thenReturn(
                        CouponClaimRedisResult.STOCK_EXHAUSTED
                );

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .isInstanceOf(CouponClaimException.class);

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));

        verify(couponEventItemRepository, never())
                .increaseSuccessCountAtomically(
                        COUPON_EVENT_ITEM_ID
                );
    }

    /**
     * 진행 중 쿠폰 이벤트 및 항목 조건
     */
    private void givenOpenEventAndItem() {
        when(couponEventRepository.findById(COUPON_EVENT_ID))
                .thenReturn(Optional.of(couponEvent));

        when(couponEventOccurrenceRepository
                .findByCouponEventIdAndId(
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .thenReturn(Optional.of(couponEventOccurrence));
        when(couponClaimItemSelector.select(
                any(Long.class),
                any(CouponEventOccurrence.class),
                any(LocalDateTime.class)
        ))
                .thenReturn(couponEventItem);


        when(couponEventOccurrence.isOpenAt(any(LocalDateTime.class)))
                .thenReturn(true);
    }
    /**
     * DB 롤백 Redis 발급 보상 검증
     */
    @Test
    void databaseRollbackCompensatesRedisClaim() {
        // given
        givenOpenEventAndItem();

        when(couponEventItem.getId())
                .thenReturn(COUPON_EVENT_ITEM_ID);

        when(couponBenefitSnapshotRepository
                .findByCouponEventItemId(
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(Optional.of(BENEFIT_SNAPSHOT));

        when(couponClaimRequestRepository
                .existsByUserIdAndCouponEventOccurrenceId(
                        USER_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                ))
                .thenReturn(false);

        when(couponClaimRedisExecutor.claim(
                COUPON_EVENT_ITEM_ID,
                COUPON_EVENT_OCCURRENCE_ID,
                USER_ID
        ))
                .thenReturn(CouponClaimRedisResult.SUCCESS);

        when(couponEventItemRepository
                .increaseSuccessCountAtomically(
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(0);

        TransactionSynchronizationManager
                .initSynchronization();

        try {
            // when
            assertThatThrownBy(() ->
                    couponClaimService.claim(
                            USER_ID,
                            COUPON_EVENT_ID,
                            COUPON_EVENT_OCCURRENCE_ID
                    ))
                    .isInstanceOf(CouponClaimException.class);

            assertThat(
                    TransactionSynchronizationManager
                            .getSynchronizations()
            ).hasSize(1);

            TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(synchronization ->
                            synchronization.afterCompletion(
                                    TransactionSynchronization
                                            .STATUS_ROLLED_BACK
                            )
                    );

            // then
            verify(couponClaimRedisExecutor).rollback(
                    COUPON_EVENT_ITEM_ID,
                    COUPON_EVENT_OCCURRENCE_ID,
                    USER_ID
            );
        } finally {
            TransactionSynchronizationManager
                    .clearSynchronization();
        }
    }
}
