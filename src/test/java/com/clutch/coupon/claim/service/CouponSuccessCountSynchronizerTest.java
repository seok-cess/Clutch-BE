package com.clutch.coupon.claim.service;

import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.wallet.repository.CouponEventItemIssuedCount;
import com.clutch.wallet.repository.UserCouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 요청 경로 밖 성공 수량 집계 검증 */
@ExtendWith(MockitoExtension.class)
class CouponSuccessCountSynchronizerTest {

    @Mock
    private CouponEventItemRepository couponEventItemRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Test
    void synchronizesCountsFromSingleGroupedQuery() {
        CouponEventItem issuedItem = CouponEventItem.create(10L, 20L, 100);
        ReflectionTestUtils.setField(issuedItem, "id", 30L);
        CouponEventItem emptyItem = CouponEventItem.create(10L, 21L, 100);
        ReflectionTestUtils.setField(emptyItem, "id", 31L);
        ReflectionTestUtils.setField(emptyItem, "successCount", 5);

        CouponEventItemIssuedCount issuedCount =
                mock(CouponEventItemIssuedCount.class);
        when(issuedCount.getCouponEventItemId()).thenReturn(30L);
        when(issuedCount.getIssuedCouponCount()).thenReturn(37L);
        when(userCouponRepository.countIssuedCouponsGroupByEventItem())
                .thenReturn(List.of(issuedCount));
        when(couponEventItemRepository.findAll())
                .thenReturn(List.of(issuedItem, emptyItem));

        CouponSuccessCountSynchronizationResult result =
                new CouponSuccessCountSynchronizer(
                couponEventItemRepository,
                userCouponRepository
        ).synchronize();

        assertThat(issuedItem.getSuccessCount()).isEqualTo(37);
        assertThat(emptyItem.getSuccessCount()).isZero();
        assertThat(result.scannedItemCount()).isEqualTo(2);
        assertThat(result.updatedItemCount()).isEqualTo(2);
        verify(userCouponRepository).countIssuedCouponsGroupByEventItem();
        verify(userCouponRepository, never())
                .countByCouponEventItemId(anyLong());
    }
}
