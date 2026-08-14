package com.clutch.coupon.claim.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 Redis 실행기 통합 테스트
 */
@SpringBootTest
class CouponClaimRedisExecutorIntegrationTest {

    private static final Long COUPON_EVENT_ITEM_ID =
            9_999_981L;

    private static final Long COUPON_EVENT_OCCURRENCE_ID =
            9_999_982L;

    private static final Long USER_ID = 9_999_983L;

    private static final String STOCK_KEY =
            CouponClaimRedisKeys.stock(
                    COUPON_EVENT_ITEM_ID
            );

    private static final String CLAIMED_USERS_KEY =
            CouponClaimRedisKeys.claimedUsers(
                    COUPON_EVENT_OCCURRENCE_ID
            );

    @Autowired
    private CouponClaimRedisExecutor couponClaimRedisExecutor;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.delete(
                List.of(
                        STOCK_KEY,
                        CLAIMED_USERS_KEY
                )
        );
    }

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete(
                List.of(
                        STOCK_KEY,
                        CLAIMED_USERS_KEY
                )
        );
    }

    /**
     * 쿠폰 발급 성공 검증
     */
    @Test
    void claimDecrementsStockAndRecordsUser() {
        stringRedisTemplate
                .opsForValue()
                .set(STOCK_KEY, "2");

        CouponClaimRedisResult result =
                couponClaimRedisExecutor.claim(
                        COUPON_EVENT_ITEM_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        USER_ID
                );

        assertThat(result)
                .isEqualTo(CouponClaimRedisResult.SUCCESS);

        assertThat(stringRedisTemplate
                .opsForValue()
                .get(STOCK_KEY))
                .isEqualTo("1");

        assertThat(stringRedisTemplate
                .opsForSet()
                .isMember(
                        CLAIMED_USERS_KEY,
                        String.valueOf(USER_ID)
                ))
                .isTrue();
    }

    /**
     * 동일 회차 중복 발급 방지 검증
     */
    @Test
    void duplicateClaimDoesNotDecrementStock() {
        stringRedisTemplate
                .opsForValue()
                .set(STOCK_KEY, "2");

        CouponClaimRedisResult firstResult =
                couponClaimRedisExecutor.claim(
                        COUPON_EVENT_ITEM_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        USER_ID
                );

        CouponClaimRedisResult secondResult =
                couponClaimRedisExecutor.claim(
                        COUPON_EVENT_ITEM_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        USER_ID
                );

        assertThat(firstResult)
                .isEqualTo(CouponClaimRedisResult.SUCCESS);

        assertThat(secondResult)
                .isEqualTo(
                        CouponClaimRedisResult.ALREADY_CLAIMED
                );

        assertThat(stringRedisTemplate
                .opsForValue()
                .get(STOCK_KEY))
                .isEqualTo("1");
    }

    /**
     * 재고 소진 결과 검증
     */
    @Test
    void exhaustedStockIsRejected() {
        stringRedisTemplate
                .opsForValue()
                .set(STOCK_KEY, "0");

        CouponClaimRedisResult result =
                couponClaimRedisExecutor.claim(
                        COUPON_EVENT_ITEM_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        USER_ID
                );

        assertThat(result)
                .isEqualTo(
                        CouponClaimRedisResult.STOCK_EXHAUSTED
                );

        assertThat(stringRedisTemplate
                .opsForValue()
                .get(STOCK_KEY))
                .isEqualTo("0");
    }
    /**
     * 재고 미등록 결과 검증
     */
    @Test
    void uninitializedStockIsRejected() {
        CouponClaimRedisResult result =
                couponClaimRedisExecutor.claim(
                        COUPON_EVENT_ITEM_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        USER_ID
                );

        assertThat(result)
                .isEqualTo(
                        CouponClaimRedisResult.STOCK_NOT_INITIALIZED
                );
    }
    /**
     * 동시 쿠폰 발급 재고 제한 검증
     */
    @Test
    void concurrentClaimsNeverExceedStock()
            throws InterruptedException {
        int stockQuantity = 10;
        int requestCount = 100;

        stringRedisTemplate
                .opsForValue()
                .set(
                        STOCK_KEY,
                        String.valueOf(stockQuantity)
                );

        ExecutorService executorService =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch readyLatch =
                new CountDownLatch(requestCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch doneLatch =
                new CountDownLatch(requestCount);

        AtomicInteger successCount =
                new AtomicInteger();

        AtomicInteger exhaustedCount =
                new AtomicInteger();

        ConcurrentLinkedQueue<Throwable> unexpectedErrors =
                new ConcurrentLinkedQueue<>();

        for (int index = 0;
             index < requestCount;
             index++) {
            long requestUserId = USER_ID + index;

            executorService.submit(() -> {
                readyLatch.countDown();

                try {
                    startLatch.await();

                    CouponClaimRedisResult result =
                            couponClaimRedisExecutor.claim(
                                    COUPON_EVENT_ITEM_ID,
                                    COUPON_EVENT_OCCURRENCE_ID,
                                    requestUserId
                            );

                    if (result
                            == CouponClaimRedisResult.SUCCESS) {
                        successCount.incrementAndGet();
                    } else if (result
                            == CouponClaimRedisResult.STOCK_EXHAUSTED) {
                        exhaustedCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(
                                new IllegalStateException(
                                        "예상하지 못한 결과: "
                                                + result
                                )
                        );
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

        startLatch.countDown();

        boolean allRequestsCompleted =
                doneLatch.await(30, TimeUnit.SECONDS);

        executorService.shutdownNow();

        assertThat(allRequestsReady).isTrue();
        assertThat(allRequestsCompleted).isTrue();
        assertThat(unexpectedErrors).isEmpty();

        assertThat(successCount.get())
                .isEqualTo(stockQuantity);

        assertThat(exhaustedCount.get())
                .isEqualTo(
                        requestCount - stockQuantity
                );

        assertThat(stringRedisTemplate
                .opsForValue()
                .get(STOCK_KEY))
                .isEqualTo("0");

        assertThat(stringRedisTemplate
                .opsForSet()
                .size(CLAIMED_USERS_KEY))
                .isEqualTo(stockQuantity);
    }
    /**
     * Redis 발급 보상 및 중복 보상 방지 검증
     */
    @Test
    void rollbackRestoresStockAndRemovesUserOnce() {
        stringRedisTemplate
                .opsForValue()
                .set(STOCK_KEY, "2");

        CouponClaimRedisResult claimResult =
                couponClaimRedisExecutor.claim(
                        COUPON_EVENT_ITEM_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        USER_ID
                );

        boolean firstRollback =
                couponClaimRedisExecutor.rollback(
                        COUPON_EVENT_ITEM_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        USER_ID
                );

        boolean secondRollback =
                couponClaimRedisExecutor.rollback(
                        COUPON_EVENT_ITEM_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        USER_ID
                );

        assertThat(claimResult)
                .isEqualTo(CouponClaimRedisResult.SUCCESS);

        assertThat(firstRollback).isTrue();
        assertThat(secondRollback).isFalse();

        assertThat(stringRedisTemplate
                .opsForValue()
                .get(STOCK_KEY))
                .isEqualTo("2");

        assertThat(stringRedisTemplate
                .opsForSet()
                .isMember(
                        CLAIMED_USERS_KEY,
                        String.valueOf(USER_ID)
                ))
                .isFalse();
    }
}