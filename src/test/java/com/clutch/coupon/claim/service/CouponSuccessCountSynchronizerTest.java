package com.clutch.coupon.claim.service;

import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.wallet.repository.UserCouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    void synchronizesCountFromActuallyIssuedCoupons() {
        CouponEventItem item = CouponEventItem.create(10L, 20L, 100);
        ReflectionTestUtils.setField(item, "id", 30L);
        when(couponEventItemRepository.findAll()).thenReturn(List.of(item));
        when(userCouponRepository.countByCouponEventItemId(30L))
                .thenReturn(37L);

        new CouponSuccessCountSynchronizer(
                couponEventItemRepository,
                userCouponRepository
        ).synchronize();

        assertThat(item.getSuccessCount()).isEqualTo(37);
        verify(userCouponRepository).countByCouponEventItemId(30L);
    }
}
