package com.clutch.coupon.test.event.service;

import com.clutch.coupon.claim.outbox.CouponBenefitSnapshot;
import com.clutch.coupon.claim.outbox.CouponBenefitSnapshotRepository;
import com.clutch.coupon.claim.redis.CouponStockInitializer;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
import com.clutch.coupon.event.domain.CouponEventPhase;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventPhaseRepository;
import com.clutch.coupon.test.event.api.dto.CouponEventActivationResponse;
import com.clutch.coupon.test.event.domain.CouponEvent;
import com.clutch.coupon.test.event.domain.CouponEventOccurrence;
import com.clutch.coupon.test.event.domain.CouponEventTrigger;
import com.clutch.coupon.test.event.exception.CouponEventErrorCode;
import com.clutch.coupon.test.event.exception.CouponEventException;
import com.clutch.coupon.test.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.test.event.repository.CouponEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponEventActivationServiceTest {

    private static final Instant NOW = Instant.parse(
            "2026-08-18T03:00:00Z"
    );
    private static final LocalDateTime NOW_UTC = LocalDateTime.ofInstant(
            NOW,
            ZoneOffset.UTC
    );

    @Mock
    private CouponEventRepository couponEventRepository;

    @Mock
    private CouponEventItemRepository couponEventItemRepository;

    @Mock
    private CouponEventOccurrenceRepository occurrenceRepository;

    @Mock
    private CouponEventPhaseRepository couponEventPhaseRepository;

    @Mock
    private CouponBenefitSnapshotRepository benefitSnapshotRepository;

    @Mock
    private CouponStockInitializer couponStockInitializer;

    @Mock
    private CouponStockRecoveryStateManager recoveryStateManager;

    private CouponEventActivationService activationService;

    @BeforeEach
    void setUp() {
        activationService = new CouponEventActivationService(
                couponEventRepository,
                couponEventItemRepository,
                couponEventPhaseRepository,
                benefitSnapshotRepository,
                occurrenceRepository,
                couponStockInitializer,
                recoveryStateManager,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 관리자가_대기_이벤트를_수동으로_오픈한다() {
        CouponEvent event = event(1L, CouponEventStatus.READY);
        CouponEventItem item = CouponEventItem.create(1L, 10L, 100);
        when(couponEventRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(event));
        when(occurrenceRepository
                .findFirstByCouponEventIdAndOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                        1L,
                        CouponEventOccurrenceStatus.OPEN,
                        NOW_UTC,
                        NOW_UTC
                )).thenReturn(Optional.empty());
        when(couponEventItemRepository.findAllByCouponEventId(1L))
                .thenReturn(List.of(item));
        when(occurrenceRepository.save(any(CouponEventOccurrence.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0),
                        20L
                ));

        CouponEventActivationResponse response = activationService
                .manualOpen(1L);

        assertThat(event.getEventStatus()).isEqualTo(CouponEventStatus.OPEN);
        assertThat(response.couponEventOccurrenceId()).isEqualTo(20L);
        assertThat(response.openedAt()).isEqualTo(NOW_UTC);
        assertThat(response.expiresAt()).isEqualTo(NOW_UTC.plusSeconds(60));
        assertThat(response.remainingQuantity()).isEqualTo(100L);
        assertThat(response.claimable()).isTrue();
        verify(couponStockInitializer).initialize(
                eq(1L),
                eq(20L),
                eq(NOW_UTC),
                eq(NOW_UTC.plusSeconds(60))
        );
    }

    @Test
    void Redis_초기화에_실패하면_발급을_차단할_상태로_전환한다() {
        CouponEvent event = event(1L, CouponEventStatus.READY);
        CouponEventItem item = CouponEventItem.create(1L, 10L, 100);
        when(couponEventRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(event));
        when(occurrenceRepository
                .findFirstByCouponEventIdAndOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                        1L,
                        CouponEventOccurrenceStatus.OPEN,
                        NOW_UTC,
                        NOW_UTC
                )).thenReturn(Optional.empty());
        when(couponEventItemRepository.findAllByCouponEventId(1L))
                .thenReturn(List.of(item));
        when(occurrenceRepository.save(any(CouponEventOccurrence.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0),
                        20L
                ));
        doThrow(new RedisConnectionFailureException("Redis 연결 실패"))
                .when(couponStockInitializer)
                .initialize(
                        eq(1L),
                        eq(20L),
                        eq(NOW_UTC),
                        eq(NOW_UTC.plusSeconds(60))
                );

        assertThatThrownBy(() -> activationService.manualOpen(1L))
                .isInstanceOf(RedisConnectionFailureException.class);

        verify(recoveryStateManager).markUnavailable();
    }

    @Test
    void 이미_열린_회차가_있으면_중복_오픈할_수_없다() {
        CouponEvent event = event(1L, CouponEventStatus.READY);
        CouponEventOccurrence occurrence = CouponEventOccurrence.manualOpen(
                1L,
                NOW_UTC.minusSeconds(10),
                60
        );
        when(couponEventRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(event));
        when(occurrenceRepository
                .findFirstByCouponEventIdAndOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                        1L,
                        CouponEventOccurrenceStatus.OPEN,
                        NOW_UTC,
                        NOW_UTC
                )).thenReturn(Optional.of(occurrence));

        assertThatThrownBy(() -> activationService.manualOpen(1L))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        CouponEventErrorCode
                                                .COUPON_EVENT_ALREADY_OPEN
                                )
                );
        verify(occurrenceRepository, never()).save(any());
    }

    @Test
    void 재고가_없으면_이벤트를_오픈할_수_없다() {
        CouponEvent event = event(1L, CouponEventStatus.READY);
        CouponEventItem item = CouponEventItem.create(1L, 10L, 1);
        item.increaseSuccessCount();
        when(couponEventRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(event));
        when(occurrenceRepository
                .findFirstByCouponEventIdAndOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                        1L,
                        CouponEventOccurrenceStatus.OPEN,
                        NOW_UTC,
                        NOW_UTC
                )).thenReturn(Optional.empty());
        when(couponEventItemRepository.findAllByCouponEventId(1L))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> activationService.manualOpen(1L))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        CouponEventErrorCode
                                                .COUPON_EVENT_STOCK_EXHAUSTED
                                )
                );
        assertThat(event.getEventStatus()).isEqualTo(CouponEventStatus.READY);
    }

    @Test
    void 가장_최근의_활성_회차를_사용자에게_제공한다() {
        CouponEvent event = event(1L, CouponEventStatus.OPEN);
        CouponEventItem item = CouponEventItem.create(1L, 10L, 100);
        item.increaseSuccessCount();
        CouponEventOccurrence occurrence = withId(
                CouponEventOccurrence.manualOpen(
                        1L,
                        NOW_UTC.minusSeconds(10),
                        60
                ),
                20L
        );
        when(occurrenceRepository
                .findFirstByOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                        CouponEventOccurrenceStatus.OPEN,
                        NOW_UTC,
                        NOW_UTC
                )).thenReturn(Optional.of(occurrence));
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(couponEventItemRepository.findAllByCouponEventId(1L))
                .thenReturn(List.of(item));

        Optional<CouponEventActivationResponse> response = activationService
                .findActive();

        assertThat(response).isPresent().get()
                .satisfies(active -> {
                    assertThat(active.couponEventOccurrenceId())
                            .isEqualTo(20L);
                    assertThat(active.remainingQuantity()).isEqualTo(99L);
                    assertThat(active.claimable()).isTrue();
                });
    }

    @Test
    void 단계별_이벤트는_단계마다_남은_수량과_전체_수량을_함께_내려준다() {
        CouponEvent event = event(1L, CouponEventStatus.OPEN);
        CouponEventItem first = item(11L, 150, 108);
        CouponEventItem second = item(12L, 88, 0);
        CouponEventOccurrence occurrence = withId(
                CouponEventOccurrence.manualOpen(
                        1L,
                        NOW_UTC.minusSeconds(10),
                        60
                ),
                20L
        );
        when(occurrenceRepository
                .findFirstByOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                        CouponEventOccurrenceStatus.OPEN,
                        NOW_UTC,
                        NOW_UTC
                )).thenReturn(Optional.of(occurrence));
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(couponEventItemRepository.findAllByCouponEventId(1L))
                .thenReturn(List.of(first, second));
        when(couponEventPhaseRepository
                .findAllByCouponEventIdOrderByOpenOffsetSecondsAsc(1L))
                .thenReturn(List.of(
                        CouponEventPhase.create(1L, 11L, 1, 0),
                        CouponEventPhase.create(1L, 12L, 2, 10)
                ));
        when(benefitSnapshotRepository.findByCouponEventItemId(11L))
                .thenReturn(Optional.of(new CouponBenefitSnapshot(
                        "RATE",
                        BigDecimal.valueOf(20)
                )));
        when(benefitSnapshotRepository.findByCouponEventItemId(12L))
                .thenReturn(Optional.of(new CouponBenefitSnapshot(
                        "RATE",
                        BigDecimal.valueOf(10)
                )));

        Optional<CouponEventActivationResponse> response = activationService
                .findActive();

        assertThat(response).isPresent().get()
                .extracting(CouponEventActivationResponse::phases)
                .asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.list(
                                CouponEventActivationResponse.Phase.class
                        )
                )
                .satisfiesExactly(
                        phase -> {
                            assertThat(phase.couponEventItemId())
                                    .isEqualTo(11L);
                            assertThat(phase.openOffsetSeconds()).isZero();
                            assertThat(phase.remainingStock()).isEqualTo(42L);
                            assertThat(phase.totalStock()).isEqualTo(150L);
                        },
                        phase -> {
                            assertThat(phase.couponEventItemId())
                                    .isEqualTo(12L);
                            assertThat(phase.openOffsetSeconds())
                                    .isEqualTo(10);
                            assertThat(phase.remainingStock()).isEqualTo(88L);
                            assertThat(phase.totalStock()).isEqualTo(88L);
                        }
                );
    }

    @Test
    void 트리거로_대기_이벤트를_열고_회차_기준으로_재고를_초기화한다() {
        CouponEvent event = event(1L, CouponEventStatus.READY);
        CouponEventItem item = CouponEventItem.create(1L, 10L, 100);
        when(couponEventRepository.findReadyByMatchAndTriggerForUpdate(
                500L,
                CouponEventTrigger.PENTAKILL.name(),
                CouponEventStatus.READY
        )).thenReturn(Optional.of(event));
        when(couponEventItemRepository.findAllByCouponEventId(1L))
                .thenReturn(List.of(item));
        when(occurrenceRepository.save(any(CouponEventOccurrence.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0),
                        20L
                ));

        Optional<CouponEventActivationResponse> response = activationService
                .openByTrigger(
                        CouponEventTrigger.PENTAKILL,
                        500L,
                        "GAME-1",
                        720
                );

        assertThat(event.getEventStatus()).isEqualTo(CouponEventStatus.OPEN);
        assertThat(response).isPresent()
                .hasValueSatisfying(opened -> {
                    assertThat(opened.couponEventOccurrenceId()).isEqualTo(20L);
                    assertThat(opened.openedAt()).isEqualTo(NOW_UTC);
                    assertThat(opened.expiresAt())
                            .isEqualTo(NOW_UTC.plusSeconds(60));
                    assertThat(opened.remainingQuantity()).isEqualTo(100L);
                    assertThat(opened.claimable()).isTrue();
                });
        verify(couponStockInitializer).initialize(
                eq(1L),
                eq(20L),
                eq(NOW_UTC),
                eq(NOW_UTC.plusSeconds(60))
        );
    }

    @Test
    void 트리거로_열_대기_이벤트가_없으면_빈_값을_돌려준다() {
        when(couponEventRepository.findReadyByMatchAndTriggerForUpdate(
                500L,
                CouponEventTrigger.PENTAKILL.name(),
                CouponEventStatus.READY
        )).thenReturn(Optional.empty());

        Optional<CouponEventActivationResponse> response = activationService
                .openByTrigger(
                        CouponEventTrigger.PENTAKILL,
                        500L,
                        "GAME-1",
                        720
                );

        assertThat(response).isEmpty();
        verify(occurrenceRepository, never()).save(any());
        verify(couponStockInitializer, never())
                .initialize(any(), any(), any(), any());
    }

    @Test
    void 만료된_회차와_이벤트를_종료한다() {
        CouponEvent event = event(1L, CouponEventStatus.OPEN);
        CouponEventOccurrence occurrence = CouponEventOccurrence.manualOpen(
                1L,
                NOW_UTC.minusSeconds(61),
                60
        );
        when(occurrenceRepository
                .findAllByOccurrenceStatusAndClosedAtIsNullAndExpiresAtLessThanEqual(
                        CouponEventOccurrenceStatus.OPEN,
                        NOW_UTC
                )).thenReturn(List.of(occurrence));
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        int closedCount = activationService.closeExpiredOccurrences();

        assertThat(closedCount).isEqualTo(1);
        assertThat(occurrence.getOccurrenceStatus())
                .isEqualTo(CouponEventOccurrenceStatus.CLOSED);
        assertThat(occurrence.getCloseReason()).isEqualTo("EXPIRED");
        assertThat(event.getEventStatus()).isEqualTo(CouponEventStatus.CLOSED);
    }

    private CouponEvent event(Long id, CouponEventStatus status) {
        CouponEvent event = newInstance(CouponEvent.class);
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "eventName", "테스트 쿠폰");
        ReflectionTestUtils.setField(event, "eventStatus", status);
        ReflectionTestUtils.setField(event, "claimWindowSeconds", 60);
        return event;
    }

    private CouponEventItem item(Long id, int quantity, int successCount) {
        CouponEventItem item = CouponEventItem.create(1L, 10L, quantity);
        ReflectionTestUtils.setField(item, "id", id);
        item.synchronizeSuccessCount(successCount);
        return item;
    }

    private CouponEventOccurrence withId(
            CouponEventOccurrence occurrence,
            Long id
    ) {
        ReflectionTestUtils.setField(occurrence, "id", id);
        return occurrence;
    }

    private <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
