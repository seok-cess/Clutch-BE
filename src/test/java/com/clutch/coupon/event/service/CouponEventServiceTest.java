package com.clutch.coupon.event.service;

import com.clutch.coupon.event.api.dto.CouponEventCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateRequest;
import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOpenMode;
import com.clutch.coupon.event.domain.CouponEventPhase;
import com.clutch.coupon.event.domain.CouponIssueMode;
import com.clutch.coupon.event.exception.CouponEventErrorCode;
import com.clutch.coupon.event.exception.CouponEventException;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventPhaseRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private CouponEventService couponEventService;

    @BeforeEach
    void setUp() {
        couponEventService = new CouponEventService(
                couponEventRepository,
                couponEventItemRepository,
                couponEventPhaseRepository
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
                CouponEventOpenMode.GAME_TRIGGERED,
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                60,
                null,
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

    private CouponEventCreateRequest gameTriggeredRequest() {
        return new CouponEventCreateRequest(
                1L,
                "펜타킬 이벤트",
                CouponEventOpenMode.GAME_TRIGGERED,
                CouponIssueMode.PHASED_FIRST_COME,
                "PENTA_KILL",
                90,
                null,
                List.of(
                        new CouponEventItemCreateRequest(1L, 5_000, 0),
                        new CouponEventItemCreateRequest(2L, 2_500, 30),
                        new CouponEventItemCreateRequest(3L, 1_000, 60)
                )
        );
    }

    private <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
