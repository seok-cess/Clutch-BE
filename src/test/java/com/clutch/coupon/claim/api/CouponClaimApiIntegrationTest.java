package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import com.clutch.lolesports.service.PollingScheduler;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쿠폰 발급 요청 API 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CouponClaimApiIntegrationTest {

    private static final Long USER_ID = 9_100_001L;
    private static final Long ESPORTS_MATCH_ID = 9_100_001L;
    private static final Long COUPON_EVENT_ID = 9_100_001L;
    private static final Long COUPON_EVENT_OCCURRENCE_ID = 9_100_001L;
    private static final Long COUPON_TYPE_ID = 9_100_001L;
    private static final Long COUPON_EVENT_ITEM_ID = 9_100_001L;

    /**
     * MVC 요청 실행기
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * SQL 실행기
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Redis 실행기
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 쿠폰 이벤트 회차 저장소
     */
    @Autowired
    private CouponEventOccurrenceRepository couponEventOccurrenceRepository;

    /**
     * JPA 엔티티 관리자
     */
    @Autowired
    private EntityManager entityManager;

    /**
     * JSON 변환기
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 경기 데이터 수집 스케줄러 모의 객체
     */
    @MockitoBean
    private PollingScheduler pollingScheduler;

    /**
     * 통합 테스트 데이터 구성
     */
    @BeforeEach
    void setUp() {
        deleteRedisKeys();

        LocalDateTime currentTime =
                LocalDateTime.now(ZoneOffset.UTC);

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
                "claim-integration-match",
                "claim-test-league",
                "2026",
                currentTime,
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
                "통합 테스트 쿠폰 이벤트",
                "FIRST_BLOOD",
                "OPEN",
                600
        );

        jdbcTemplate.update(
                """
                        INSERT INTO coupon_event_occurrence (
                            coupon_event_occurrence_id,
                            coupon_event_id,
                            source_event_key,
                            detected_at,
                            opened_at,
                            expires_at,
                            occurrence_status
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                COUPON_EVENT_OCCURRENCE_ID,
                COUPON_EVENT_ID,
                "claim-integration-occurrence",
                currentTime.minusMinutes(1),
                currentTime.minusMinutes(1),
                currentTime.plusMinutes(10),
                "OPEN"
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
                "통합 테스트 쿠폰",
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

    /**
     * 정상 쿠폰 발급 요청 저장 검증
     */
    @Test
    void claimPersistsRequestAndIncreasesSuccessCount()
            throws Exception {
        // given
        LocalDateTime currentTime =
                LocalDateTime.now();

        CouponEventOccurrence occurrence =
                couponEventOccurrenceRepository
                        .findById(COUPON_EVENT_OCCURRENCE_ID)
                        .orElseThrow();

        assertThat(occurrence.getOccurrenceStatus())
                .isEqualTo(CouponEventOccurrenceStatus.OPEN);
        assertThat(occurrence.getClosedAt()).isNull();
        assertThat(occurrence.getOpenedAt())
                .isBeforeOrEqualTo(currentTime);
        assertThat(occurrence.getExpiresAt())
                .isAfter(currentTime);

        // when, then
        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}"
                                        + "/occurrences/{occurrenceId}/claims",
                                COUPON_EVENT_ID,
                                COUPON_EVENT_OCCURRENCE_ID
                        )
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.couponEventId")
                                .value(COUPON_EVENT_ID)
                )
                .andExpect(
                        jsonPath("$.couponEventItemId")
                                .value(COUPON_EVENT_ITEM_ID)
                )
                .andExpect(
                        jsonPath("$.couponEventOccurrenceId")
                                .value(COUPON_EVENT_OCCURRENCE_ID)
                )


                .andExpect(
                        jsonPath("$.requestStatus")
                                .value("PENDING")
                );

        entityManager.flush();
        entityManager.clear();

        Integer claimCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM coupon_claim_request
                        WHERE user_id = ?
                          AND coupon_event_item_id = ?
                          AND coupon_event_occurrence_id = ?
                        """,
                Integer.class,
                USER_ID,
                COUPON_EVENT_ITEM_ID,
                COUPON_EVENT_OCCURRENCE_ID
        );

        String requestStatus = jdbcTemplate.queryForObject(
                """
                        SELECT request_status
                        FROM coupon_claim_request
                        WHERE user_id = ?
                          AND coupon_event_item_id = ?
                          AND coupon_event_occurrence_id = ?
                        """,
                String.class,
                USER_ID,
                COUPON_EVENT_ITEM_ID,
                COUPON_EVENT_OCCURRENCE_ID
        );

        Long claimId = jdbcTemplate.queryForObject(
                """
                        SELECT coupon_claim_request_id
                        FROM coupon_claim_request
                        WHERE user_id = ?
                          AND coupon_event_item_id = ?
                          AND coupon_event_occurrence_id = ?
                        """,
                Long.class,
                USER_ID,
                COUPON_EVENT_ITEM_ID,
                COUPON_EVENT_OCCURRENCE_ID
        );

        String outboxStatus = jdbcTemplate.queryForObject(
                """
                        SELECT status
                        FROM coupon_claim_outbox
                        WHERE aggregate_id = ?
                        """,
                String.class,
                claimId
        );

        String outboxTopic = jdbcTemplate.queryForObject(
                """
                        SELECT topic
                        FROM coupon_claim_outbox
                        WHERE aggregate_id = ?
                        """,
                String.class,
                claimId
        );

        String outboxPayload = jdbcTemplate.queryForObject(
                """
                        SELECT payload
                        FROM coupon_claim_outbox
                        WHERE aggregate_id = ?
                        """,
                String.class,
                claimId
        );

        JsonNode payloadJson =
                objectMapper.readTree(outboxPayload);

        Integer successCount = jdbcTemplate.queryForObject(
                """
                        SELECT success_count
                        FROM coupon_event_item
                        WHERE coupon_event_item_id = ?
                        """,
                Integer.class,
                COUPON_EVENT_ITEM_ID
        );

        assertThat(claimCount).isEqualTo(1);
        assertThat(requestStatus).isEqualTo("PENDING");
        assertThat(successCount).isEqualTo(1);
        assertThat(outboxStatus).isEqualTo("PENDING");
        assertThat(outboxTopic)
                .isEqualTo("coupon.claim.accepted");
        assertThat(payloadJson.get("claimId").asLong())
                .isEqualTo(claimId);
        assertThat(payloadJson.get("userId").asLong())
                .isEqualTo(USER_ID);
        assertThat(payloadJson.get("couponEventId").asLong())
                .isEqualTo(COUPON_EVENT_ID);
        assertThat(payloadJson
                .get("couponEventOccurrenceId")
                .asLong())
                .isEqualTo(COUPON_EVENT_OCCURRENCE_ID);
        assertThat(payloadJson.get("couponEventItemId").asLong())
                .isEqualTo(COUPON_EVENT_ITEM_ID);
        assertThat(payloadJson.get("discountType").asText())
                .isEqualTo("RATE");
        assertThat(payloadJson.get("discountValue").decimalValue())
                .isEqualByComparingTo("10.00");
        assertThat(payloadJson.get("expiresAt").asText())
                .isNotBlank();
        assertThat(payloadJson.get("occurredAt").asText())
                .isNotBlank();
    }

    /**
     * 중복 쿠폰 발급 요청 충돌 응답 검증
     */
    @Test
    void duplicateClaimReturnsConflict() throws Exception {
        // given
        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}"
                                        + "/occurrences/{occurrenceId}/claims",
                                COUPON_EVENT_ID,
                                COUPON_EVENT_OCCURRENCE_ID
                        )
                                .header("X-User-Id", USER_ID)

                )
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        // when, then
        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}"
                                        + "/occurrences/{occurrenceId}/claims",
                                COUPON_EVENT_ID,
                                COUPON_EVENT_OCCURRENCE_ID
                        )
                                .header("X-User-Id", USER_ID)

                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("COUPON_ALREADY_CLAIMED")
                );
    }

    /**
     * 쿠폰 재고 소진 충돌 응답 검증
     */
    @Test
    void exhaustedStockReturnsConflict() throws Exception {
        // given
        jdbcTemplate.update(
                """
                        UPDATE coupon_event_item
                        SET success_count = quantity
                        WHERE coupon_event_item_id = ?
                        """,
                COUPON_EVENT_ITEM_ID
        );

        // when, then
        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}"
                                        + "/occurrences/{occurrenceId}/claims",
                                COUPON_EVENT_ID,
                                COUPON_EVENT_OCCURRENCE_ID
                        )
                                .header("X-User-Id", USER_ID)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("COUPON_STOCK_EXHAUSTED")
                );

        Integer claimCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM coupon_claim_request
                        WHERE user_id = ?
                          AND coupon_event_item_id = ?
                          AND coupon_event_occurrence_id = ?
                        """,
                Integer.class,
                USER_ID,
                COUPON_EVENT_ITEM_ID,
                COUPON_EVENT_OCCURRENCE_ID
        );

        assertThat(claimCount).isZero();
    }

    /**
     * 통합 테스트 Redis 데이터 정리
     */
    @AfterEach
    void tearDown() {
        deleteRedisKeys();
    }

    /**
     * 통합 테스트 Redis 키 삭제
     */
    private void deleteRedisKeys() {
        stringRedisTemplate.delete(
                List.of(
                        CouponClaimRedisKeys.stock(
                                COUPON_EVENT_ITEM_ID
                        ),
                        CouponClaimRedisKeys.claimedUsers(
                                COUPON_EVENT_OCCURRENCE_ID
                        )
                )
        );
    }
}
