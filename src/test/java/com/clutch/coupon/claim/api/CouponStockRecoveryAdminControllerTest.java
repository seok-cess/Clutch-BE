package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.exception.CouponClaimErrorCode;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryResult;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryService;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryState;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 쿠폰 재고 복구 API 테스트 */
@WebMvcTest(CouponStockRecoveryAdminController.class)
class CouponStockRecoveryAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponStockRecoveryStateManager stateManager;

    @MockitoBean
    private CouponStockRecoveryService recoveryService;

    @Test
    void returnsCurrentRecoveryState() throws Exception {
        when(stateManager.current())
                .thenReturn(CouponStockRecoveryState.RECOVERING);

        mockMvc.perform(get("/api/v1/admin/coupon-stock-recovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RECOVERING"));
    }

    @Test
    void retriesRecovery() throws Exception {
        when(recoveryService.recoverOpenOccurrences())
                .thenReturn(new CouponStockRecoveryResult(
                        CouponStockRecoveryState.READY,
                        1,
                        2,
                        3
                ));

        mockMvc.perform(post("/api/v1/admin/coupon-stock-recovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.recoveredOccurrences").value(1))
                .andExpect(jsonPath("$.recoveredItems").value(2))
                .andExpect(jsonPath("$.recoveredUsers").value(3));
    }

    @Test
    void reportsInconsistentMysqlData() throws Exception {
        when(recoveryService.recoverOpenOccurrences())
                .thenThrow(new CouponClaimException(
                        CouponClaimErrorCode.COUPON_STOCK_INCONSISTENT
                ));

        mockMvc.perform(post("/api/v1/admin/coupon-stock-recovery"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_STOCK_INCONSISTENT"));
    }
}
