package com.clutch.coupon.event.service;

import com.clutch.coupon.event.api.dto.CouponEventCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventDetailResponse;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventListResponse;
import com.clutch.coupon.event.api.dto.CouponEventUpdateRequest;
import com.clutch.coupon.event.api.dto.CouponEventUpdateResponse;
import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventPhase;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.contract.trigger.CouponTestMatch;
import com.clutch.coupon.event.domain.CouponIssueMode;
import com.clutch.coupon.event.exception.CouponEventErrorCode;
import com.clutch.coupon.event.exception.CouponEventException;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.event.repository.CouponEventPhaseRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.type.domain.CouponDiscountType;
import com.clutch.coupon.type.domain.CouponType;
import com.clutch.coupon.type.repository.CouponTypeRepository;
import com.clutch.wallet.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.stream.StreamSupport;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponEventServiceTest {

    @Mock
    private CouponEventRepository couponEventRepository;

    @Mock
    private CouponEventItemRepository couponEventItemRepository;

    @Mock
    private CouponEventPhaseRepository couponEventPhaseRepository;

    @Mock
    private CouponEventOccurrenceRepository couponEventOccurrenceRepository;

    @Mock
    private CouponClaimRequestRepository couponClaimRequestRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponTypeRepository couponTypeRepository;

    private CouponEventService couponEventService;

    @BeforeEach
    void setUp() {
        couponEventService = new CouponEventService(
                couponEventRepository,
                couponEventItemRepository,
                couponEventPhaseRepository,
                couponEventOccurrenceRepository,
                couponClaimRequestRepository,
                userCouponRepository,
                couponTypeRepository,
                Clock.fixed(
                        Instant.parse("2026-08-19T03:00:00Z"),
                        ZoneOffset.UTC
                )
        );
        lenient().doAnswer(invocation -> StreamSupport.stream(
                                ((Iterable<?>) invocation.getArgument(0))
                                        .spliterator(),
                                false
                        )
                        .map(id -> CouponType.create(
                                "테스트 쿠폰 " + id,
                                CouponDiscountType.RATE,
                                BigDecimal.TEN
                        ))
                        .toList())
                .when(couponTypeRepository).findAllById(any());
    }

    @Test
    void 트리거형_일반_선착순_이벤트를_등록한다() {
        CouponEventCreateRequest request = new CouponEventCreateRequest(
                1L,
                "퍼블 일반 선착순 이벤트",
                CouponIssueMode.SINGLE_FIRST_COME,
                "FIRST_BLOOD",
                60,
                List.of(new CouponEventItemCreateRequest(
                        1L,
                        10_000,
                        0
                ))
        );
        when(couponEventRepository.save(any(CouponEvent.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        when(couponEventItemRepository.save(any(CouponEventItem.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));
        when(couponEventPhaseRepository.save(any(CouponEventPhase.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 20L));

        CouponEventCreateResponse response = couponEventService.create(request);

        assertThat(response.issueMode())
                .isEqualTo(CouponIssueMode.SINGLE_FIRST_COME);
        assertThat(response.esportsMatchId()).isEqualTo(1L);
        assertThat(response.triggerType()).isEqualTo("FIRST_BLOOD");
        assertThat(response.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.quantity()).isEqualTo(10_000);
                    assertThat(item.openOffsetSeconds()).isZero();
                });
        verify(couponEventRepository)
                .existsByEsportsMatchIdAndTriggerType(1L, "FIRST_BLOOD");
    }

    // CLUTCH-216: 수동 테스트 기본값이 실제 저장 트리거로 변환되는지 검증한다.
    @ParameterizedTest
    @CsvSource({
            "0, MANUAL_TEST_20260819_001",
            "2, MANUAL_TEST_20260819_003"
    })
    void 수동_테스트_트리거를_한국_날짜와_당일_순번으로_등록한다(
            int lastSequence,
            String expectedTriggerType
    ) {
        CouponEventCreateRequest request = new CouponEventCreateRequest(
                316L,
                "수동 테스트 이벤트",
                CouponIssueMode.SINGLE_FIRST_COME,
                "MANUAL_TEST",
                30,
                List.of(new CouponEventItemCreateRequest(1L, 500, 0))
        );
        when(couponEventRepository.findMaxManualTestSequence(
                "MANUAL_TEST_20260819_"
        )).thenReturn(lastSequence);
        when(couponEventRepository.existsByEsportsMatchIdAndTriggerType(
                316L,
                expectedTriggerType
        )).thenReturn(false);
        when(couponEventRepository.save(any(CouponEvent.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        when(couponEventItemRepository.save(any(CouponEventItem.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));
        when(couponEventPhaseRepository.save(any(CouponEventPhase.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 20L));

        CouponEventCreateResponse response = couponEventService.create(request);

        assertThat(response.triggerType())
                .isEqualTo(expectedTriggerType);
        verify(couponEventRepository).existsByEsportsMatchIdAndTriggerType(
                316L,
                expectedTriggerType
        );
    }


    @Test
    void 테스트_이벤트는_예약된_경기_ID로_등록한다() {
        // replay 는 실행마다 경기 ID 가 달라져 실제 경기로는 미리 등록할 수 없다.
        // 예약 ID(음수)가 양수 검사에 막히면 테스트 이벤트를 만들 수 없다
        CouponEventCreateRequest request = new CouponEventCreateRequest(
                CouponTestMatch.SAMPLE_MATCH_ID,
                "펜타킬 테스트 이벤트",
                CouponIssueMode.SINGLE_FIRST_COME,
                "PENTAKILL",
                60,
                List.of(new CouponEventItemCreateRequest(1L, 100, 0))
        );
        when(couponEventRepository.save(any(CouponEvent.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        when(couponEventItemRepository.save(any(CouponEventItem.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));
        when(couponEventPhaseRepository.save(any(CouponEventPhase.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 20L));

        CouponEventCreateResponse response = couponEventService.create(request);

        assertThat(response.esportsMatchId())
                .isEqualTo(CouponTestMatch.SAMPLE_MATCH_ID);
        assertThat(response.triggerType()).isEqualTo("PENTAKILL");
    }

    @Test
    void 예약되지_않은_음수_경기_ID는_등록할_수_없다() {
        CouponEventCreateRequest request = new CouponEventCreateRequest(
                -999L,
                "잘못된 경기 이벤트",
                CouponIssueMode.SINGLE_FIRST_COME,
                "PENTAKILL",
                60,
                List.of(new CouponEventItemCreateRequest(1L, 100, 0))
        );

        assertThatThrownBy(() -> couponEventService.create(request))
                .isInstanceOf(CouponEventException.class);
    }

    @Test
    void 첫_단계가_0초가_아니면_등록할_수_없다() {
        CouponEventCreateRequest request = new CouponEventCreateRequest(
                1L,
                "잘못된 단계 이벤트",
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                90,
                List.of(
                        new CouponEventItemCreateRequest(1L, 5_000, 10),
                        new CouponEventItemCreateRequest(2L, 1_000, 30)
                )
        );

        assertThatThrownBy(() -> couponEventService.create(request))
                .isInstanceOf(CouponEventException.class);
        verify(couponEventRepository, never()).save(any());
    }

    @Test
    void 중복된_쿠폰_종류는_등록할_수_없다() {
        CouponEventCreateRequest request = new CouponEventCreateRequest(
                1L,
                "중복 쿠폰 이벤트",
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                90,
                List.of(
                        new CouponEventItemCreateRequest(1L, 5_000, 0),
                        new CouponEventItemCreateRequest(1L, 1_000, 30)
                )
        );

        assertThatThrownBy(() -> couponEventService.create(request))
                .isInstanceOf(CouponEventException.class);
        verify(couponEventRepository, never()).save(any());
    }

    @Test
    void 목록_페이지가_0_미만이면_조회할_수_없다() {
        assertThatThrownBy(() -> couponEventService.findAll(null, -1, 20))
                .isInstanceOf(CouponEventException.class)
                .hasMessage("페이지 번호는 0 이상이어야 합니다.");
    }

    @Test
    void 목록_크기가_허용_범위를_벗어나면_조회할_수_없다() {
        assertThatThrownBy(() -> couponEventService.findAll(null, 0, 101))
                .isInstanceOf(CouponEventException.class)
                .hasMessage("목록 크기는 1개 이상 100개 이하여야 합니다.");
    }

    @Test
    void 수정한_경기와_트리거가_다른_이벤트와_중복되면_수정할_수_없다() {
        CouponEvent event = withId(gameTriggeredEvent("기존 이벤트"), 1L);
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(couponEventRepository
                .existsByEsportsMatchIdAndTriggerTypeAndIdNot(
                        2L,
                        "FIRST_BLOOD",
                        1L
                )).thenReturn(true);

        assertThatThrownBy(() -> couponEventService.update(
                1L,
                updateRequest()
        )).isInstanceOfSatisfying(
                CouponEventException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CouponEventErrorCode.COUPON_EVENT_DUPLICATED)
        );
        verify(couponEventPhaseRepository, never())
                .deleteAllByCouponEventId(any());
    }

    @Test
    void 발급_요청_이력이_있는_이벤트는_삭제할_수_없다() {
        CouponEvent event = withId(gameTriggeredEvent("요청 이력 이벤트"), 1L);
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(couponEventOccurrenceRepository.existsByCouponEventId(1L))
                .thenReturn(false);
        when(couponClaimRequestRepository.existsByCouponEventId(1L))
                .thenReturn(true);

        assertThatThrownBy(() -> couponEventService.delete(1L))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponEventErrorCode.COUPON_EVENT_NOT_DELETABLE)
                );
        verify(userCouponRepository, never()).existsByCouponEventId(any());
        verify(couponEventRepository, never()).delete(any());
    }

    @Test
    void 사용자_쿠폰_이력이_있는_이벤트는_삭제할_수_없다() {
        CouponEvent event = withId(gameTriggeredEvent("발급 이력 이벤트"), 1L);
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(couponEventOccurrenceRepository.existsByCouponEventId(1L))
                .thenReturn(false);
        when(couponClaimRequestRepository.existsByCouponEventId(1L))
                .thenReturn(false);
        when(userCouponRepository.existsByCouponEventId(1L))
                .thenReturn(true);

        assertThatThrownBy(() -> couponEventService.delete(1L))
                .isInstanceOf(CouponEventException.class);
        verify(couponEventRepository, never()).delete(any());
    }

    @Test
    void 존재하지_않는_이벤트는_삭제할_수_없다() {
        when(couponEventRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponEventService.delete(999L))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponEventErrorCode.COUPON_EVENT_NOT_FOUND)
                );
        verify(couponEventRepository, never()).delete(any());
    }

    @Test
    void 이력이_없는_대기_상태_쿠폰_이벤트를_물리_삭제한다() {
        CouponEvent event = withId(gameTriggeredEvent("삭제 이벤트"), 1L);
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(couponEventOccurrenceRepository.existsByCouponEventId(1L))
                .thenReturn(false);
        when(couponClaimRequestRepository.existsByCouponEventId(1L))
                .thenReturn(false);
        when(userCouponRepository.existsByCouponEventId(1L))
                .thenReturn(false);

        couponEventService.delete(1L);

        InOrder deleteOrder = inOrder(
                couponEventPhaseRepository,
                couponEventItemRepository,
                couponEventRepository
        );
        deleteOrder.verify(couponEventPhaseRepository)
                .deleteAllByCouponEventId(1L);
        deleteOrder.verify(couponEventPhaseRepository).flush();
        deleteOrder.verify(couponEventItemRepository)
                .deleteAllByCouponEventId(1L);
        deleteOrder.verify(couponEventItemRepository).flush();
        deleteOrder.verify(couponEventRepository).delete(event);
        deleteOrder.verify(couponEventRepository).flush();
    }

    @Test
    void 발생_이력이_있는_쿠폰_이벤트는_물리_삭제할_수_없다() {
        CouponEvent event = withId(gameTriggeredEvent("발생 이벤트"), 1L);
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(couponEventOccurrenceRepository.existsByCouponEventId(1L))
                .thenReturn(true);

        assertThatThrownBy(() -> couponEventService.delete(1L))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponEventErrorCode.COUPON_EVENT_NOT_DELETABLE)
                );

        verify(couponEventRepository, never()).delete(any());
    }

    @Test
    void 진행_중인_쿠폰_이벤트는_물리_삭제할_수_없다() {
        CouponEvent event = withId(gameTriggeredEvent("진행 이벤트"), 1L);
        ReflectionTestUtils.setField(
                event,
                "eventStatus",
                CouponEventStatus.OPEN
        );
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        assertThatThrownBy(() -> couponEventService.delete(1L))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponEventErrorCode.COUPON_EVENT_NOT_DELETABLE)
                );

        verify(couponEventOccurrenceRepository, never())
                .existsByCouponEventId(any());
        verify(couponEventRepository, never()).delete(any());
    }

    @Test
    void 대기_상태의_쿠폰_이벤트를_수정한다() {
        CouponEvent event = withId(gameTriggeredEvent("기존 이벤트"), 1L);
        CouponEventUpdateRequest request = updateRequest();
        AtomicLong itemId = new AtomicLong(50L);
        AtomicLong phaseId = new AtomicLong(60L);

        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(couponEventRepository
                .existsByEsportsMatchIdAndTriggerTypeAndIdNot(
                        2L,
                        "FIRST_BLOOD",
                        1L
                )).thenReturn(false);
        when(couponEventRepository.saveAndFlush(event)).thenReturn(event);
        when(couponEventItemRepository.save(any(CouponEventItem.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0),
                        itemId.getAndIncrement()
                ));
        when(couponEventPhaseRepository.save(any(CouponEventPhase.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0),
                        phaseId.getAndIncrement()
                ));

        CouponEventUpdateResponse response = couponEventService.update(
                1L,
                request
        );

        assertThat(response.eventName()).isEqualTo("퍼블 이벤트");
        assertThat(response.esportsMatchId()).isEqualTo(2L);
        assertThat(response.triggerType()).isEqualTo("FIRST_BLOOD");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
                .extracting(item -> item.openOffsetSeconds())
                .containsExactly(0, 30);

        InOrder deleteOrder = inOrder(
                couponEventPhaseRepository,
                couponEventItemRepository
        );
        deleteOrder.verify(couponEventPhaseRepository)
                .deleteAllByCouponEventId(1L);
        deleteOrder.verify(couponEventPhaseRepository).flush();
        deleteOrder.verify(couponEventItemRepository)
                .deleteAllByCouponEventId(1L);
        deleteOrder.verify(couponEventItemRepository).flush();
    }

    @Test
    void 진행_중인_쿠폰_이벤트는_수정할_수_없다() {
        CouponEvent event = withId(gameTriggeredEvent("진행 이벤트"), 1L);
        ReflectionTestUtils.setField(
                event,
                "eventStatus",
                CouponEventStatus.OPEN
        );
        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        assertThatThrownBy(() -> couponEventService.update(
                1L,
                updateRequest()
        )).isInstanceOfSatisfying(
                CouponEventException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CouponEventErrorCode.COUPON_EVENT_NOT_EDITABLE)
        );

        verify(couponEventPhaseRepository, never())
                .deleteAllByCouponEventId(any());
    }

    @Test
    void 이벤트_목록을_페이지와_수량_정보로_조회한다() {
        CouponEvent first = withId(gameTriggeredEvent("첫 이벤트"), 3L);
        CouponEvent second = withId(gameTriggeredEvent("두 번째 이벤트"), 2L);
        CouponEventItem firstItem = withId(
                CouponEventItem.create(3L, 1L, 5_000),
                30L
        );
        ReflectionTestUtils.setField(firstItem, "successCount", 1_200);

        when(couponEventRepository.findByEventStatusOrderByIdDesc(
                CouponEventStatus.READY,
                PageRequest.of(0, 2)
        )).thenReturn(new PageImpl<>(
                List.of(first, second),
                PageRequest.of(0, 2),
                5
        ));
        when(couponEventItemRepository.findAllByCouponEventIdIn(
                List.of(3L, 2L)
        )).thenReturn(List.of(firstItem));

        CouponEventListResponse response = couponEventService.findAll(
                CouponEventStatus.READY,
                0,
                2
        );

        assertThat(response.events()).hasSize(2);
        assertThat(response.page()).isZero();
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasPrevious()).isFalse();
        assertThat(response.hasNext()).isTrue();
        assertThat(response.events().getFirst().totalQuantity())
                .isEqualTo(5_000);
        assertThat(response.events().getFirst().remainingQuantity())
                .isEqualTo(3_800);
    }

    @Test
    void 이벤트_상세에서_단계별_발급과_잔여_수량을_조회한다() {
        CouponEvent event = withId(gameTriggeredEvent("펜타킬 이벤트"), 1L);
        CouponEventItem item = withId(
                CouponEventItem.create(1L, 10L, 5_000),
                20L
        );
        ReflectionTestUtils.setField(item, "successCount", 1_500);
        CouponEventPhase phase = withId(
                CouponEventPhase.create(1L, 20L, 1, 0),
                30L
        );

        when(couponEventRepository.findById(1L))
                .thenReturn(Optional.of(event));
        when(couponEventItemRepository.findAllByCouponEventId(1L))
                .thenReturn(List.of(item));
        when(couponEventPhaseRepository
                .findAllByCouponEventIdOrderByOpenOffsetSecondsAsc(1L))
                .thenReturn(List.of(phase));
        when(couponEventOccurrenceRepository
                .findFirstByCouponEventIdOrderByIdDesc(1L))
                .thenReturn(Optional.empty());

        CouponEventDetailResponse response = couponEventService.findById(1L);

        assertThat(response.totalQuantity()).isEqualTo(5_000);
        assertThat(response.issuedQuantity()).isEqualTo(1_500);
        assertThat(response.remainingQuantity()).isEqualTo(3_500);
        assertThat(response.items().getFirst().phaseSequence()).isEqualTo(1);
        assertThat(response.latestOccurrence()).isNull();
    }

    @Test
    void 존재하지_않는_이벤트_상세는_조회할_수_없다() {
        when(couponEventRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponEventService.findById(999L))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponEventErrorCode.COUPON_EVENT_NOT_FOUND)
                );
    }

    @Test
    void 경기_트리거_차등_혜택_이벤트를_등록한다() {
        CouponEventCreateRequest request = gameTriggeredRequest();
        AtomicLong itemId = new AtomicLong(10L);
        AtomicLong phaseId = new AtomicLong(100L);

        when(couponEventRepository.existsByEsportsMatchIdAndTriggerType(
                1L, "PENTA_KILL"
        )).thenReturn(false);
        when(couponEventRepository.save(any(CouponEvent.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        when(couponEventItemRepository.save(any(CouponEventItem.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0), itemId.getAndIncrement()
                ));
        when(couponEventPhaseRepository.save(any(CouponEventPhase.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0), phaseId.getAndIncrement()
                ));

        CouponEventCreateResponse response = couponEventService.create(request);

        assertThat(response.couponEventId()).isEqualTo(1L);
        assertThat(response.eventStatus().name()).isEqualTo("READY");
        assertThat(response.items()).hasSize(3);
        assertThat(response.items())
                .extracting(item -> item.openOffsetSeconds())
                .containsExactly(0, 30, 60);
        verify(couponEventItemRepository, times(3))
                .save(any(CouponEventItem.class));
    }

    @Test
    void 단계_오픈_시간이_신청_가능_시간_이상이면_등록할_수_없다() {
        CouponEventCreateRequest request = new CouponEventCreateRequest(
                1L,
                "펜타킬 이벤트",
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                60,
                List.of(
                        new CouponEventItemCreateRequest(1L, 5_000, 0),
                        new CouponEventItemCreateRequest(2L, 2_500, 60)
                )
        );

        assertThatThrownBy(() -> couponEventService.create(request))
                .isInstanceOf(CouponEventException.class)
                .hasMessage("단계 오픈 시간은 신청 가능 시간보다 작아야 합니다.");
        verify(couponEventRepository, never()).save(any());
    }

    @Test
    void 같은_경기와_트리거의_이벤트는_중복_등록할_수_없다() {
        CouponEventCreateRequest request = gameTriggeredRequest();
        when(couponEventRepository.existsByEsportsMatchIdAndTriggerType(
                1L, "PENTA_KILL"
        )).thenReturn(true);

        assertThatThrownBy(() -> couponEventService.create(request))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponEventErrorCode.COUPON_EVENT_DUPLICATED)
                );
        verify(couponEventRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_쿠폰_종류로_이벤트를_등록할_수_없다() {
        CouponEventCreateRequest request = gameTriggeredRequest();
        doReturn(List.of())
                .when(couponTypeRepository).findAllById(any());

        assertThatThrownBy(() -> couponEventService.create(request))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponEventErrorCode.COUPON_TYPE_NOT_FOUND)
                );
        verify(couponEventRepository, never()).save(any());
    }

    @Test
    void 비활성_쿠폰_종류로_이벤트를_등록할_수_없다() {
        CouponEventCreateRequest request = gameTriggeredRequest();
        CouponType inactiveCouponType = CouponType.create(
                "비활성 쿠폰",
                CouponDiscountType.RATE,
                BigDecimal.TEN
        );
        inactiveCouponType.deactivate();
        doReturn(List.of(
                        inactiveCouponType,
                        CouponType.create(
                                "20% 쿠폰",
                                CouponDiscountType.RATE,
                                BigDecimal.valueOf(20)
                        ),
                        CouponType.create(
                                "30% 쿠폰",
                                CouponDiscountType.RATE,
                                BigDecimal.valueOf(30)
                        )
                ))
                .when(couponTypeRepository).findAllById(any());

        assertThatThrownBy(() -> couponEventService.create(request))
                .isInstanceOfSatisfying(
                        CouponEventException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponEventErrorCode.COUPON_TYPE_INACTIVE)
                );
        verify(couponEventRepository, never()).save(any());
    }

    private CouponEventCreateRequest gameTriggeredRequest() {
        return new CouponEventCreateRequest(
                1L,
                "펜타킬 이벤트",
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                90,
                List.of(
                        new CouponEventItemCreateRequest(1L, 5_000, 0),
                        new CouponEventItemCreateRequest(2L, 2_500, 30),
                        new CouponEventItemCreateRequest(3L, 1_000, 60)
                )
        );
    }

    private CouponEvent gameTriggeredEvent(String eventName) {
        return CouponEvent.create(
                1L,
                eventName,
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                90
        );
    }

    private CouponEventUpdateRequest updateRequest() {
        return new CouponEventUpdateRequest(
                2L,
                "퍼블 이벤트",
                CouponIssueMode.PHASED_FIRST_COME,
                "FIRST_BLOOD",
                60,
                List.of(
                        new CouponEventItemCreateRequest(10L, 5_000, 0),
                        new CouponEventItemCreateRequest(20L, 1_000, 30)
                )
        );
    }

    private <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
