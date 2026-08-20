package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponStockResponse;
import com.clutch.coupon.claim.api.dto.CouponClaimErrorResponse;
import com.clutch.coupon.claim.exception.CouponClaimException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** 쿠폰 재고 SSE 구독과 변경 알림 */
@Service
@RequiredArgsConstructor
public class CouponStockStreamService {

    private static final Long TIMEOUT_MILLIS =
            Duration.ofMinutes(30).toMillis();
    private static final String EVENT_NAME = "coupon-stock";
    private static final String ERROR_EVENT_NAME = "coupon-stock-error";

    private final CouponStockService couponStockService;
    private final CouponStockSseEmitterFactory emitterFactory;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>
            emitters = new ConcurrentHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong();

    /** 최신 재고 스냅샷 SSE 구독 */
    public SseEmitter subscribe(
            Long couponEventItemId,
            String lastEventId
    ) {
        SseEmitter emitter = emitterFactory.create(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(
                couponEventItemId,
                ignored -> new CopyOnWriteArrayList<>()
        ).add(emitter);

        emitter.onCompletion(() -> remove(couponEventItemId, emitter));
        emitter.onTimeout(() -> {
            remove(couponEventItemId, emitter);
            emitter.complete();
        });
        emitter.onError(ignored -> remove(couponEventItemId, emitter));

        CouponStockResponse currentStock;
        try {
            currentStock = couponStockService.getStock(couponEventItemId);
        } catch (RuntimeException exception) {
            remove(couponEventItemId, emitter);
            throw exception;
        }

        if (!send(couponEventItemId, emitter, currentStock)) {
            return emitter;
        }
        if (currentStock.exhausted()) {
            emitter.complete();
        }
        return emitter;
    }

    /** 최신 Redis 재고 알림 */
    public void publish(Long couponEventItemId) {
        List<SseEmitter> itemEmitters = emitters.get(couponEventItemId);
        if (itemEmitters == null || itemEmitters.isEmpty()) {
            return;
        }

        CouponStockResponse currentStock;
        try {
            currentStock = couponStockService.getStock(couponEventItemId);
        } catch (CouponClaimException exception) {
            sendErrorAndComplete(
                    couponEventItemId,
                    itemEmitters,
                    exception
            );
            return;
        }
        for (SseEmitter emitter : itemEmitters) {
            if (send(couponEventItemId, emitter, currentStock)
                    && currentStock.exhausted()) {
                emitter.complete();
            }
        }
    }

    /** SSE 재고 조회 오류 전송과 연결 종료 */
    private void sendErrorAndComplete(
            Long couponEventItemId,
            List<SseEmitter> itemEmitters,
            CouponClaimException exception
    ) {
        for (SseEmitter emitter : itemEmitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .id(String.valueOf(
                                        eventSequence.incrementAndGet()
                                ))
                                .name(ERROR_EVENT_NAME)
                                .data(CouponClaimErrorResponse.from(
                                        exception.getErrorCode()
                                ))
                                .reconnectTime(1_000L)
                );
                emitter.complete();
            } catch (IOException | IllegalStateException sendException) {
                emitter.completeWithError(sendException);
            } finally {
                remove(couponEventItemId, emitter);
            }
        }
    }

    private boolean send(
            Long couponEventItemId,
            SseEmitter emitter,
            CouponStockResponse stock
    ) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .id(String.valueOf(eventSequence.incrementAndGet()))
                            .name(EVENT_NAME)
                            .data(stock)
                            .reconnectTime(1_000L)
            );
            return true;
        } catch (IOException | IllegalStateException exception) {
            remove(couponEventItemId, emitter);
            emitter.completeWithError(exception);
            return false;
        }
    }

    private void remove(Long couponEventItemId, SseEmitter emitter) {
        emitters.computeIfPresent(couponEventItemId, (ignored, current) -> {
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
    }

    /** 현재 구독자 수 조회 */
    int subscriberCount(Long couponEventItemId) {
        List<SseEmitter> itemEmitters = emitters.get(couponEventItemId);
        return itemEmitters == null ? 0 : itemEmitters.size();
    }
}
