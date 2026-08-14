package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode
        .COUPON_EVENT_ITEM_NOT_AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions
        .catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 발급 항목 선택기 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponClaimItemSelectorTest {

    private static final Long COUPON_EVENT_ID = 10L;

    @Mock
    private CouponEventItemRepository
            couponEventItemRepository;

    @Mock
    private CouponEventOccurrence couponEventOccurrence;

    @Mock
    private CouponEventItem firstCouponEventItem;

    @Mock
    private CouponEventItem secondCouponEventItem;

    @InjectMocks
    private CouponClaimItemSelector couponClaimItemSelector;

    /**
     * 경과 시간 기준 쿠폰 항목 선택 검증
     */
    @Test
    void selectsCouponItemAvailableAtElapsedTime() {
        LocalDateTime openedAt =
                LocalDateTime.of(
                        2026,
                        8,
                        14,
                        10,
                        0
                );

        when(couponEventOccurrence.getOpenedAt())
                .thenReturn(openedAt);

        when(couponEventItemRepository
                .findAllByCouponEventId(COUPON_EVENT_ID))
                .thenReturn(List.of(
                        firstCouponEventItem,
                        secondCouponEventItem
                ));

        when(firstCouponEventItem.isAvailableAt(7L))
                .thenReturn(false);

        when(secondCouponEventItem.isAvailableAt(7L))
                .thenReturn(true);

        CouponEventItem selectedCouponEventItem =
                couponClaimItemSelector.select(
                        COUPON_EVENT_ID,
                        couponEventOccurrence,
                        openedAt.plusSeconds(7)
                );

        assertThat(selectedCouponEventItem)
                .isSameAs(secondCouponEventItem);

        verify(firstCouponEventItem)
                .isAvailableAt(7L);

        verify(secondCouponEventItem)
                .isAvailableAt(7L);
    }

    /**
     * 활성 쿠폰 항목 부재 예외 검증
     */
    @Test
    void selectionFailsWhenNoCouponItemIsAvailable() {
        LocalDateTime openedAt =
                LocalDateTime.of(
                        2026,
                        8,
                        14,
                        10,
                        0
                );

        when(couponEventOccurrence.getOpenedAt())
                .thenReturn(openedAt);

        when(couponEventItemRepository
                .findAllByCouponEventId(COUPON_EVENT_ID))
                .thenReturn(List.of(firstCouponEventItem));

        when(firstCouponEventItem.isAvailableAt(15L))
                .thenReturn(false);

        CouponClaimException exception =
                catchThrowableOfType(
                        () -> couponClaimItemSelector.select(
                                COUPON_EVENT_ID,
                                couponEventOccurrence,
                                openedAt.plusSeconds(15)
                        ),
                        CouponClaimException.class
                );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        COUPON_EVENT_ITEM_NOT_AVAILABLE
                );
    }
}