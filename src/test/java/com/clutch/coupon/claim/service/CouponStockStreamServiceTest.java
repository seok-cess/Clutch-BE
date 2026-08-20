package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponStockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SSE 연결 자원 정리 테스트 */
@ExtendWith(MockitoExtension.class)
class CouponStockStreamServiceTest {

    private static final Long ITEM_ID = 101L;

    @Mock
    private CouponStockService couponStockService;

    @Mock
    private CouponStockSseEmitterFactory emitterFactory;

    @Mock
    private SseEmitter emitter;

    private CouponStockStreamService streamService;

    @BeforeEach
    void setUp() {
        streamService = new CouponStockStreamService(
                couponStockService,
                emitterFactory
        );
        when(emitterFactory.create(1_800_000L)).thenReturn(emitter);
        when(couponStockService.getStock(ITEM_ID))
                .thenReturn(CouponStockResponse.of(ITEM_ID, 10L));
    }

    /** 정상 종료 시 구독자 제거 검증 */
    @Test
    void completionRemovesSubscriber() {
        ArgumentCaptor<Runnable> callback =
                ArgumentCaptor.forClass(Runnable.class);

        streamService.subscribe(ITEM_ID, null);
        assertThat(streamService.subscriberCount(ITEM_ID)).isEqualTo(1);

        verify(emitter).onCompletion(callback.capture());
        callback.getValue().run();

        assertThat(streamService.subscriberCount(ITEM_ID)).isZero();
    }

    /** 제한 시간 초과 시 구독자 제거 검증 */
    @Test
    void timeoutRemovesSubscriberAndCompletesConnection() {
        ArgumentCaptor<Runnable> callback =
                ArgumentCaptor.forClass(Runnable.class);

        streamService.subscribe(ITEM_ID, null);
        verify(emitter).onTimeout(callback.capture());
        callback.getValue().run();

        assertThat(streamService.subscriberCount(ITEM_ID)).isZero();
        verify(emitter).complete();
    }

    /** 전송 오류 시 구독자 제거 검증 */
    @Test
    void errorRemovesSubscriber() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> callback =
                ArgumentCaptor.forClass(Consumer.class);

        streamService.subscribe(ITEM_ID, null);
        verify(emitter).onError(callback.capture());
        callback.getValue().accept(new IllegalStateException("disconnect"));

        assertThat(streamService.subscriberCount(ITEM_ID)).isZero();
    }
}
