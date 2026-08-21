package com.clutch.wallet.web;

import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.service.CouponQueryService;
import com.clutch.wallet.service.CouponUseService;
import com.clutch.wallet.web.exception.InvalidCouponQueryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class MyCouponControllerTest {

    @Mock private CouponQueryService couponQueryService;
    @Mock private CouponUseService couponUseService;

    @Test
    void size가_범위_밖이면_서비스를_호출하지_않는다(){
        MyCouponController controller = new MyCouponController(couponQueryService, couponUseService);

        assertThrows(InvalidCouponQueryException.class, () ->
                controller.getMyCoupons(1L, null, null, 0));
        assertThrows(InvalidCouponQueryException.class, () ->
                controller.getMyCoupons(1L, null, null, 101));

        verifyNoInteractions(couponQueryService);
    }

    @Test
    void 정상_요청이면_파싱된_커서값으로_서비스를_호출한다(){
        MyCouponController controller = new MyCouponController(couponQueryService, couponUseService);

        controller.getMyCoupons(1L, UserCouponStatus.ISSUED, "1734000000000_57", 20);

        verify(couponQueryService).getMyCoupons(1L, UserCouponStatus.ISSUED, Instant.ofEpochMilli(1734000000000L), 57L, 20);
    }
}
