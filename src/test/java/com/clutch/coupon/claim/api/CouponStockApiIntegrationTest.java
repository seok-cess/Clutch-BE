package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import com.clutch.coupon.claim.service.CouponStockStreamService;
import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 Redis 기반 쿠폰 재고 조회·SSE 통합 테스트 */
@SpringBootTest
@AutoConfigureMockMvc
class CouponStockApiIntegrationTest {

    private static final Long ITEM_ID = 9_200_001L;
    private static final String STOCK_KEY =
            CouponClaimRedisKeys.stock(ITEM_ID);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CouponStockStreamService couponStockStreamService;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete(STOCK_KEY);
    }

    @Test
    void readsStockWithoutMySqlAndReturnsExhaustedState()
            throws Exception {
        stringRedisTemplate.opsForValue().set(STOCK_KEY, "0");

        mockMvc.perform(get(
                        "/api/v1/coupon-event-items/{itemId}/stock",
                        ITEM_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingStock").value(0))
                .andExpect(jsonPath("$.exhausted").value(true));
    }

    @Test
    void reconnectSendsLatestSnapshotAndCompletesWhenExhausted()
            throws Exception {
        stringRedisTemplate.opsForValue().set(STOCK_KEY, "0");

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/coupon-event-items/{itemId}/stock/stream",
                        ITEM_ID
                ).header("Last-Event-ID", "previous-event"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn();

        String stream = completed.getResponse().getContentAsString();
        assertThat(stream).contains("event:coupon-stock");
        assertThat(stream).contains("\"remainingStock\":0");
        assertThat(stream).contains("\"exhausted\":true");
    }

    @Test
    void streamsEveryStockChangeUntilExhausted() throws Exception {
        stringRedisTemplate.opsForValue().set(STOCK_KEY, "3");

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/coupon-event-items/{itemId}/stock/stream",
                        ITEM_ID
                ))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        for (int remainingStock = 2;
             remainingStock >= 0;
             remainingStock--) {
            stringRedisTemplate.opsForValue().set(
                    STOCK_KEY,
                    String.valueOf(remainingStock)
            );
            couponStockStreamService.publish(ITEM_ID);
        }

        MvcResult completed = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn();

        String stream = completed.getResponse().getContentAsString();
        assertThat(stream)
                .containsSubsequence(
                        "\"remainingStock\":3",
                        "\"remainingStock\":2",
                        "\"remainingStock\":1",
                        "\"remainingStock\":0"
                );
        assertThat(stream).contains("\"exhausted\":true");
    }

    @Test
    void missingRedisStockIsDifferentFromExhaustedStock()
            throws Exception {
        mockMvc.perform(get(
                        "/api/v1/coupon-event-items/{itemId}/stock",
                        ITEM_ID
                ))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("COUPON_STOCK_NOT_INITIALIZED"));
    }
}
