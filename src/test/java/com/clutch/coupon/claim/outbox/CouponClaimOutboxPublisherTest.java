package com.clutch.coupon.claim.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 발급 Outbox 발행 스케줄러 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponClaimOutboxPublisherTest {

    @Mock
    private CouponClaimOutboxRepository outboxRepository;

    @Mock
    private CouponClaimOutboxSender outboxSender;

    @Mock
    private CouponClaimOutbox firstOutbox;

    @Mock
    private CouponClaimOutbox secondOutbox;

    @InjectMocks
    private CouponClaimOutboxPublisher outboxPublisher;

    /**
     * 발행 대기 Outbox 전달 검증
     */
    @Test
    void publishPendingSendsEveryOutbox() {
        // given
        when(outboxRepository
                .findTop100ByStatusOrderByIdAsc(
                        CouponClaimOutboxStatus.PENDING
                ))
                .thenReturn(
                        List.of(firstOutbox, secondOutbox)
                );

        when(firstOutbox.getId()).thenReturn(1L);
        when(secondOutbox.getId()).thenReturn(2L);

        // when
        outboxPublisher.publishPending();

        // then
        verify(outboxSender).send(1L);
        verify(outboxSender).send(2L);
    }

    /**
     * 개별 Outbox 오류 이후 처리 지속 검증
     */
    @Test
    void publishPendingContinuesAfterOneFailure() {
        // given
        when(outboxRepository
                .findTop100ByStatusOrderByIdAsc(
                        CouponClaimOutboxStatus.PENDING
                ))
                .thenReturn(
                        List.of(firstOutbox, secondOutbox)
                );

        when(firstOutbox.getId()).thenReturn(1L);
        when(secondOutbox.getId()).thenReturn(2L);

        doThrow(new IllegalStateException("처리 실패"))
                .when(outboxSender)
                .send(1L);

        // when
        outboxPublisher.publishPending();

        // then
        verify(outboxSender).send(1L);
        verify(outboxSender).send(2L);
    }
}