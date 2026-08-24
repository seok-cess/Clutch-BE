package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import com.clutch.coupon.claim.redis.CouponClaimContext;
import com.clutch.coupon.claim.redis.CouponClaimContextStore;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_EXHAUSTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 쿠폰 발급 요청 동시성 통합 테스트
 */
@SpringBootTest(
        properties = {
                "spring.datasource.hikari.maximum-pool-size=30",
                "wallet.outbox.enabled=false"
        }
)
class CouponClaimConcurrencyIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(
            CouponClaimConcurrencyIntegrationTest.class
    );

    private static final Long ESPORTS_MATCH_ID = 9_200_001L;
    private static final Long COUPON_EVENT_ID = 9_200_001L;
    private static final Long COUPON_EVENT_OCCURRENCE_ID = 9_200_001L;
    private static final Long COUPON_TYPE_ID = 9_200_001L;
    private static final Long COUPON_EVENT_ITEM_ID = 9_200_001L;

    private static final int STOCK_QUANTITY = 10;
    private static final int REQUEST_COUNT = 100;
    private static final long USER_ID_BASE = 9_300_000L;

    /**
     * 쿠폰 발급 요청 서비스
     */
    @Autowired
    private CouponClaimService couponClaimService;

    @Autowired
    private CouponSuccessCountSynchronizer successCountSynchronizer;

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

    @Autowired
    private CouponClaimContextStore couponClaimContextStore;

    @Autowired
    private CouponStockRecoveryStateManager recoveryStateManager;

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
        recoveryStateManager.markReady();
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

        couponClaimContextStore.save(new CouponClaimContext(
                COUPON_EVENT_ID,
                COUPON_EVENT_OCCURRENCE_ID,
                currentTime.minusMinutes(1).toInstant(ZoneOffset.UTC)
                        .toEpochMilli(),
                currentTime.plusMinutes(10).toInstant(ZoneOffset.UTC)
                        .toEpochMilli(),
                List.of(new CouponClaimContext.CouponClaimContextPhase(
                        0,
                        COUPON_EVENT_ITEM_ID,
                        "RATE",
                        new BigDecimal("10.00")
                ))
        ));

        stringRedisTemplate.opsForValue().set(
                CouponClaimRedisKeys.stock(COUPON_EVENT_ITEM_ID),
                String.valueOf(STOCK_QUANTITY)
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
     * 동시성 테스트 데이터 정리
     */
    @AfterEach
    void tearDown() {
        recoveryStateManager.markReady();
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

        successCountSynchronizer.synchronize();

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
     * Redis 기반 쿠폰 발급 동시성 제어 검증
     */
    @Test
    void concurrentClaimsDoNotExceedStock()
            throws InterruptedException {
        // given
        ExecutorService executorService =
                Executors.newFixedThreadPool(REQUEST_COUNT);

        CountDownLatch readyLatch =
                new CountDownLatch(REQUEST_COUNT);
        CountDownLatch startLatch =
                new CountDownLatch(1);
        CountDownLatch doneLatch =
                new CountDownLatch(REQUEST_COUNT);

        AtomicInteger successResponseCount =
                new AtomicInteger();
        AtomicInteger failureResponseCount =
                new AtomicInteger();
        Queue<Throwable> unexpectedErrors =
                new ConcurrentLinkedQueue<>();


        try {
            for (int requestIndex = 0;
                 requestIndex < REQUEST_COUNT;
                 requestIndex++) {
                long userId = USER_ID_BASE + requestIndex;

                executorService.submit(() -> {
                    readyLatch.countDown();

                    try {
                        startLatch.await();

                        couponClaimService.claim(
                                userId,
                                COUPON_EVENT_ID,
                                COUPON_EVENT_OCCURRENCE_ID
                        );

                        successResponseCount.incrementAndGet();
                    } catch (CouponClaimException exception) {
                        if (exception.getErrorCode()
                                == COUPON_STOCK_EXHAUSTED) {
                            failureResponseCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(exception);
                        }
                    } catch (Throwable throwable) {
                        unexpectedErrors.add(throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean allRequestsReady =
                    readyLatch.await(10, TimeUnit.SECONDS);

            assertThat(allRequestsReady).isTrue();

            // when
            startLatch.countDown();

            boolean allRequestsCompleted =
                    doneLatch.await(30, TimeUnit.SECONDS);

            assertThat(allRequestsCompleted).isTrue();
        } finally {
            executorService.shutdownNow();
        }

        // then
        Integer successfulClaimCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM coupon_claim_request
                        WHERE coupon_event_occurrence_id = ?
                          AND request_status = 'SUCCEEDED'
                        """,
                Integer.class,
                COUPON_EVENT_OCCURRENCE_ID
        );

        Integer issuedCouponCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM user_coupon
                        WHERE coupon_event_occurrence_id = ?
                          AND coupon_status = 'ISSUED'
                        """,
                Integer.class,
                COUPON_EVENT_OCCURRENCE_ID
        );

        Integer walletOutboxCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM wallet_outbox
                        WHERE aggregate_id IN (
                            SELECT coupon_claim_request_id
                            FROM coupon_claim_request
                            WHERE coupon_event_occurrence_id = ?
                        )
                        """,
                Integer.class,
                COUPON_EVENT_OCCURRENCE_ID
        );

        Integer claimOutboxCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM coupon_claim_outbox
                        WHERE aggregate_id IN (
                            SELECT coupon_claim_request_id
                            FROM coupon_claim_request
                            WHERE coupon_event_occurrence_id = ?
                        )
                        """,
                Integer.class,
                COUPON_EVENT_OCCURRENCE_ID
        );

        successCountSynchronizer.synchronize();

        Integer successCount = jdbcTemplate.queryForObject(
                """
                        SELECT success_count
                        FROM coupon_event_item
                        WHERE coupon_event_item_id = ?
                        """,
                Integer.class,
                COUPON_EVENT_ITEM_ID
        );
        String remainingRedisStock =
                stringRedisTemplate
                        .opsForValue()
                        .get(
                                CouponClaimRedisKeys.stock(
                                        COUPON_EVENT_ITEM_ID
                                )
                        );

        Long claimedUserCount =
                stringRedisTemplate
                        .opsForSet()
                        .size(
                                CouponClaimRedisKeys.claimedUsers(
                                        COUPON_EVENT_OCCURRENCE_ID
                                )
                        );


        log.info(
                "Redis 동시성 결과 - 요청: {}, 성공 응답: {}, "
                        + "재고 소진 응답: {}, 성공 요청: {}, "
                        + "실제 쿠폰: {}, DB 성공 수량: {}, Redis 재고: {}, "
                        + "Redis 당첨자: {}",
                REQUEST_COUNT,
                successResponseCount.get(),
                failureResponseCount.get(),
                successfulClaimCount,
                issuedCouponCount,
                successCount,
                remainingRedisStock,
                claimedUserCount
        );
        assertThat(unexpectedErrors).isEmpty();

        assertThat(successResponseCount.get())
                .isEqualTo(STOCK_QUANTITY);

        assertThat(failureResponseCount.get())
                .isEqualTo(
                        REQUEST_COUNT - STOCK_QUANTITY
                );

        assertThat(
                successResponseCount.get()
                        + failureResponseCount.get()
        ).isEqualTo(REQUEST_COUNT);

        assertThat(successfulClaimCount)
                .isEqualTo(STOCK_QUANTITY);

        assertThat(issuedCouponCount)
                .isEqualTo(STOCK_QUANTITY);

        assertThat(walletOutboxCount)
                .isEqualTo(STOCK_QUANTITY);

        assertThat(claimOutboxCount).isZero();

        assertThat(successCount)
                .isEqualTo(STOCK_QUANTITY);

        assertThat(remainingRedisStock)
                .isEqualTo("0");

        assertThat(claimedUserCount)
                .isEqualTo((long)
                        STOCK_QUANTITY);
    }

    /**
     * 쿠폰 생성 실패 시 DB 및 Redis 보상 검증
     */
    @Test
    void couponCreationFailureRollsBackDatabaseAndRedis() {
        // given
        long userId = USER_ID_BASE + REQUEST_COUNT + 1;

        jdbcTemplate.update(
                """
                        INSERT INTO user_coupon (
                            user_id,
                            coupon_event_id,
                            coupon_event_occurrence_id,
                            coupon_event_item_id,
                            claim_id,
                            coupon_code,
                            coupon_status,
                            discount_type,
                            discount_value,
                            expires_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                COUPON_EVENT_ID,
                COUPON_EVENT_OCCURRENCE_ID,
                COUPON_EVENT_ITEM_ID,
                9_299_999L,
                "CPN-ROLLBACK-TEST",
                "ISSUED",
                "RATE",
                10,
                LocalDateTime.now(ZoneOffset.UTC).plusDays(7)
        );

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        userId,
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID
                )
        ).isInstanceOf(DataIntegrityViolationException.class);

        Integer claimCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM coupon_claim_request
                        WHERE user_id = ?
                          AND coupon_event_occurrence_id = ?
                        """,
                Integer.class,
                userId,
                COUPON_EVENT_OCCURRENCE_ID
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

        String remainingRedisStock =
                stringRedisTemplate
                        .opsForValue()
                        .get(
                                CouponClaimRedisKeys.stock(
                                        COUPON_EVENT_ITEM_ID
                                )
                        );

        Boolean remainsClaimed =
                stringRedisTemplate
                        .opsForSet()
                        .isMember(
                                CouponClaimRedisKeys.claimedUsers(
                                        COUPON_EVENT_OCCURRENCE_ID
                                ),
                                String.valueOf(userId)
                        );

        assertThat(claimCount).isZero();
        assertThat(successCount).isZero();
        assertThat(remainingRedisStock)
                .isEqualTo(String.valueOf(STOCK_QUANTITY));
        assertThat(remainsClaimed).isFalse();
    }

    /**
     * 동시성 테스트 데이터 삭제
     */
    private void cleanUpTestData() {
        stringRedisTemplate.delete(
                List.of(
                        CouponClaimRedisKeys.stock(
                                COUPON_EVENT_ITEM_ID
                        ),
                        CouponClaimRedisKeys.claimedUsers(
                                COUPON_EVENT_OCCURRENCE_ID
                        ),
                        CouponClaimRedisKeys.context(
                                COUPON_EVENT_OCCURRENCE_ID
                        )
                )
        );

        jdbcTemplate.update(
                """
                        DELETE FROM wallet_outbox
                        WHERE aggregate_id IN (
                            SELECT coupon_claim_request_id
                            FROM coupon_claim_request
                            WHERE coupon_event_item_id = ?
                        )
                        """,
                COUPON_EVENT_ITEM_ID
        );

        jdbcTemplate.update(
                """
                        DELETE FROM user_coupon
                        WHERE coupon_event_occurrence_id = ?
                        """,
                COUPON_EVENT_OCCURRENCE_ID
        );

        jdbcTemplate.update(
                """
                        DELETE FROM coupon_claim_outbox
                        WHERE aggregate_id IN (
                            SELECT coupon_claim_request_id
                            FROM coupon_claim_request
                            WHERE coupon_event_item_id = ?
                        )
                        """,
                COUPON_EVENT_ITEM_ID
        );

        jdbcTemplate.update(
                """
                        DELETE FROM coupon_claim_request
                        WHERE coupon_event_item_id = ?
                        """,
                COUPON_EVENT_ITEM_ID
        );

        jdbcTemplate.update(
                """
                        DELETE FROM coupon_event_phase
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
