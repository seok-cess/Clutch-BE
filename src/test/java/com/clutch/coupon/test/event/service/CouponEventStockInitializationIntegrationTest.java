package com.clutch.coupon.test.event.service;

import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import com.clutch.coupon.test.event.api.dto.CouponEventActivationResponse;
import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회차 오픈 직후 첫 발급 요청이 Redis 초기화 누락으로 실패하지 않는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class CouponEventStockInitializationIntegrationTest {

    private static final Long USER_ID = 9_800_001L;
    private static final Long ESPORTS_MATCH_ID = 9_800_001L;
    private static final Long COUPON_EVENT_ID = 9_800_001L;
    private static final Long COUPON_TYPE_ID = 9_800_001L;
    private static final Long COUPON_EVENT_ITEM_ID = 9_800_001L;

    @Autowired
    private CouponEventActivationService activationService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CouponStockRecoveryStateManager recoveryStateManager;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    @BeforeEach
    void setUp() {
        cleanUpTestData();
        recoveryStateManager.markReady();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        jdbcTemplate.update(
                """
                        INSERT INTO esports_match (
                            esports_match_id,
                            external_match_id,
                            league_external_id,
                            season_key,
                            scheduled_at,
                            lifecycle_status
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                ESPORTS_MATCH_ID,
                "stock-initialization-match",
                "claim-test-league",
                "2026",
                now,
                "inProgress"
        );

        jdbcTemplate.update(
                """
                        INSERT INTO coupon_event (
                            coupon_event_id,
                            esports_match_id,
                            event_name,
                            trigger_type,
                            event_status,
                            claim_window_seconds
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                COUPON_EVENT_ID,
                ESPORTS_MATCH_ID,
                "Redis 초기화 통합 테스트 쿠폰 이벤트",
                "FIRST_BLOOD",
                "READY",
                600
        );

        jdbcTemplate.update(
                """
                        INSERT INTO coupon_type (
                            coupon_type_id,
                            coupon_name,
                            discount_value,
                            status
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                COUPON_TYPE_ID,
                "Redis 초기화 통합 테스트 쿠폰",
                10,
                "ACTIVE"
        );

        jdbcTemplate.update(
                """
                        INSERT INTO coupon_event_item (
                            coupon_event_item_id,
                            coupon_event_id,
                            coupon_type_id,
                            quantity,
                            success_count
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                COUPON_EVENT_ITEM_ID,
                COUPON_EVENT_ID,
                COUPON_TYPE_ID,
                10,
                0
        );

        jdbcTemplate.update(
                """
                        INSERT INTO coupon_event_phase (
                            coupon_event_id,
                            coupon_event_item_id,
                            phase_sequence,
                            open_offset_seconds
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                COUPON_EVENT_ID,
                COUPON_EVENT_ITEM_ID,
                1,
                0
        );
    }

    @AfterEach
    void tearDown() {
        recoveryStateManager.markReady();
        cleanUpTestData();
    }

    @Test
    void 회차_오픈_직후_첫_발급_요청이_성공한다() throws Exception {
        CouponEventActivationResponse opened = activationService.manualOpen(
                COUPON_EVENT_ID
        );

        assertThat(stringRedisTemplate.opsForValue().get(
                CouponClaimRedisKeys.stock(COUPON_EVENT_ITEM_ID)
        )).isEqualTo("10");

        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}"
                                        + "/occurrences/{occurrenceId}/claims",
                                COUPON_EVENT_ID,
                                opened.couponEventOccurrenceId()
                        ).header("X-User-Id", USER_ID)
                )
                .andExpect(status().isCreated());

        Integer successCount = jdbcTemplate.queryForObject(
                """
                        SELECT success_count
                        FROM coupon_event_item
                        WHERE coupon_event_item_id = ?
                        """,
                Integer.class,
                COUPON_EVENT_ITEM_ID
        );

        assertThat(successCount).isEqualTo(1);
    }

    private void cleanUpTestData() {
        deleteRedisKeys();

        jdbcTemplate.update(
                """
                        DELETE FROM wallet_outbox
                        WHERE aggregate_id IN (
                            SELECT coupon_claim_request_id
                            FROM coupon_claim_request
                            WHERE coupon_event_id = ?
                        )
                        """,
                COUPON_EVENT_ID
        );
        jdbcTemplate.update(
                """
                        DELETE FROM coupon_claim_outbox
                        WHERE aggregate_id IN (
                            SELECT coupon_claim_request_id
                            FROM coupon_claim_request
                            WHERE coupon_event_id = ?
                        )
                        """,
                COUPON_EVENT_ID
        );
        jdbcTemplate.update(
                "DELETE FROM user_coupon WHERE coupon_event_id = ?",
                COUPON_EVENT_ID
        );
        jdbcTemplate.update(
                "DELETE FROM coupon_claim_request WHERE coupon_event_id = ?",
                COUPON_EVENT_ID
        );
        jdbcTemplate.update(
                "DELETE FROM coupon_event_phase WHERE coupon_event_id = ?",
                COUPON_EVENT_ID
        );
        jdbcTemplate.update(
                "DELETE FROM coupon_event_occurrence WHERE coupon_event_id = ?",
                COUPON_EVENT_ID
        );
        jdbcTemplate.update(
                "DELETE FROM coupon_event_item WHERE coupon_event_id = ?",
                COUPON_EVENT_ID
        );
        jdbcTemplate.update(
                "DELETE FROM coupon_event WHERE coupon_event_id = ?",
                COUPON_EVENT_ID
        );
        jdbcTemplate.update(
                "DELETE FROM coupon_type WHERE coupon_type_id = ?",
                COUPON_TYPE_ID
        );
        jdbcTemplate.update(
                "DELETE FROM esports_match WHERE esports_match_id = ?",
                ESPORTS_MATCH_ID
        );
    }

    private void deleteRedisKeys() {
        List<Long> occurrenceIds = jdbcTemplate.queryForList(
                """
                        SELECT coupon_event_occurrence_id
                        FROM coupon_event_occurrence
                        WHERE coupon_event_id = ?
                        """,
                Long.class,
                COUPON_EVENT_ID
        );

        List<String> keys = new java.util.ArrayList<>();
        keys.add(CouponClaimRedisKeys.stock(COUPON_EVENT_ITEM_ID));
        occurrenceIds.forEach(occurrenceId -> keys.add(
                CouponClaimRedisKeys.claimedUsers(occurrenceId)
        ));
        stringRedisTemplate.delete(keys);
    }
}
