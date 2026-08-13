package com.clutch.coupon.claim.api;

import com.clutch.lolesports.service.PollingScheduler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
     * JPA 엔티티 관리자
     */
    @Autowired
    private EntityManager entityManager;

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
                    event_type,
                    description,
                    started_at,
                    closed_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                COUPON_EVENT_ID,
                ESPORTS_MATCH_ID,
                "FIRST_COME",
                "통합 테스트 쿠폰 이벤트",
                currentTime.minusMinutes(1),
                currentTime.plusMinutes(10)
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
    }

    /**
     * 정상 쿠폰 발급 요청 저장 검증
     */
    @Test
    void claimPersistsRequestAndIncreasesSuccessCount()
            throws Exception {
        // when, then
        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}/claims",
                                COUPON_EVENT_ID
                        )
                                .header("X-User-Id", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "couponEventItemId": 9100001
                                        }
                                        """
                                )
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
                        jsonPath("$.requestStatus")
                                .value("SUCCEEDED")
                );

        entityManager.flush();
        entityManager.clear();

        Integer claimCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM coupon_claim_request
                WHERE user_id = ?
                  AND coupon_event_item_id = ?
                """,
                Integer.class,
                USER_ID,
                COUPON_EVENT_ITEM_ID
        );

        String requestStatus = jdbcTemplate.queryForObject(
                """
                SELECT request_status
                FROM coupon_claim_request
                WHERE user_id = ?
                  AND coupon_event_item_id = ?
                """,
                String.class,
                USER_ID,
                COUPON_EVENT_ITEM_ID
        );

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
        assertThat(requestStatus).isEqualTo("SUCCEEDED");
        assertThat(successCount).isEqualTo(1);
    }
    /**
     * 중복 쿠폰 발급 요청 충돌 응답 검증
     */
    @Test
    void duplicateClaimReturnsConflict() throws Exception {
        // given
        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}/claims",
                                COUPON_EVENT_ID
                        )
                                .header("X-User-Id", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "couponEventItemId": 9100001
                                        }
                                        """
                                )
                )
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        // when, then
        mockMvc.perform(
                        post(
                                "/api/v1/coupon-events/{couponEventId}/claims",
                                COUPON_EVENT_ID
                        )
                                .header("X-User-Id", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "couponEventItemId": 9100001
                                        }
                                        """
                                )
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
                                "/api/v1/coupon-events/{couponEventId}/claims",
                                COUPON_EVENT_ID
                        )
                                .header("X-User-Id", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "couponEventItemId": 9100001
                                        }
                                        """
                                )
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
                """,
                Integer.class,
                USER_ID,
                COUPON_EVENT_ITEM_ID
        );

        assertThat(claimCount).isZero();
    }
}