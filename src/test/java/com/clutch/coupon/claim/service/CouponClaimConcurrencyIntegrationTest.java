package com.clutch.coupon.claim.service;

import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 요청 동시성 통합 테스트
 */
@SpringBootTest
class CouponClaimConcurrencyIntegrationTest {

    private static final Long ESPORTS_MATCH_ID = 9_200_001L;
    private static final Long COUPON_EVENT_ID = 9_200_001L;
    private static final Long COUPON_EVENT_OCCURRENCE_ID = 9_200_001L;
    private static final Long COUPON_TYPE_ID = 9_200_001L;
    private static final Long COUPON_EVENT_ITEM_ID = 9_200_001L;

    private static final int STOCK_QUANTITY = 10;
    private static final int REQUEST_COUNT = 100;

    /**
     * 쿠폰 발급 요청 서비스
     */
    @Autowired
    private CouponClaimService couponClaimService;

    /**
     * SQL 실행기
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 경기 데이터 수집 스케줄러 모의 객체
     */
    @MockitoBean
    private PollingScheduler pollingScheduler;

    /**
     * 동시성 테스트 데이터 구성
     */
    @BeforeEach
    void setUp() {
        cleanUpTestData();

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
                "claim-concurrency-match",
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
                "동시성 테스트 쿠폰 이벤트",
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
                "claim-concurrency-occurrence",
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
                "동시성 테스트 쿠폰",
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
                STOCK_QUANTITY,
                0
        );
    }

    /**
     * 동시성 테스트 데이터 정리
     */
    @AfterEach
    void tearDown() {
        cleanUpTestData();
    }

    /**
     * 동시성 테스트 데이터 구성 검증
     */
    @Test
    void concurrencyTestDataIsPrepared() {
        Integer quantity = jdbcTemplate.queryForObject(
                """
                SELECT quantity
                FROM coupon_event_item
                WHERE coupon_event_item_id = ?
                """,
                Integer.class,
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

        assertThat(quantity).isEqualTo(STOCK_QUANTITY);
        assertThat(successCount).isZero();

        String occurrenceStatus = jdbcTemplate.queryForObject(
                """
                SELECT occurrence_status
                FROM coupon_event_occurrence
                WHERE coupon_event_occurrence_id = ?
                """,
                String.class,
                COUPON_EVENT_OCCURRENCE_ID
        );

        assertThat(occurrenceStatus).isEqualTo("OPEN");
    }

    /**
     * 동시성 테스트 데이터 삭제
     */
    private void cleanUpTestData() {
        jdbcTemplate.update(
                """
                DELETE FROM coupon_claim_request
                WHERE coupon_event_item_id = ?
                """,
                COUPON_EVENT_ITEM_ID
        );

        jdbcTemplate.update(
                """
                DELETE FROM coupon_event_item
                WHERE coupon_event_item_id = ?
                """,
                COUPON_EVENT_ITEM_ID
        );

        jdbcTemplate.update(
                """
                DELETE FROM coupon_event_occurrence
                WHERE coupon_event_occurrence_id = ?
                """,
                COUPON_EVENT_OCCURRENCE_ID
        );

        jdbcTemplate.update(
                """
                DELETE FROM coupon_event
                WHERE coupon_event_id = ?
                """,
                COUPON_EVENT_ID
        );

        jdbcTemplate.update(
                """
                DELETE FROM coupon_type
                WHERE coupon_type_id = ?
                """,
                COUPON_TYPE_ID
        );

        jdbcTemplate.update(
                """
                DELETE FROM esports_match
                WHERE esports_match_id = ?
                """,
                ESPORTS_MATCH_ID
        );
    }
}
