package com.clutch.wallet.web;

import com.clutch.wallet.service.CouponQueryService;
import com.clutch.wallet.service.CouponUseService;
import com.clutch.wallet.web.exception.InvalidCouponQueryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

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
}
