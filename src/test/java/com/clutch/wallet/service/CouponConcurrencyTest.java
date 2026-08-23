package com.clutch.wallet.service;

import com.clutch.coupon.contract.kafka.CouponClaimAcceptedEvent;
import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.web.dto.CouponResponse;
import com.clutch.wallet.web.exception.CouponAlreadyUsedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private CouponUseService couponUseService;
    @Autowired private AdminCouponService adminCouponService;
    @Autowired private CouponIssuanceService couponIssuanceService;

    private static final List<Long> TEST_CLAIM_IDS = List.of(7001L, 7002L, 7003L);

    @AfterEach
    void cleanUp() {
        TEST_CLAIM_IDS.forEach(claimId ->
                userCouponRepository.findByClaimId(claimId)
                        .ifPresent(userCouponRepository::delete));
    }

    private UserCoupon newCoupon(long claimId) {
        return new UserCoupon(
                claimId, 1L, 10L, null, 100L,
                "CPN-" + claimId, "RATE", new BigDecimal("50.00"),
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
    }

    @Test
    void 동시에_사용_요청하면_하나만_성공한다() throws InterruptedException {
        UserCoupon saved = userCouponRepository.saveAndFlush(newCoupon(7001L));
        Long couponId = saved.getId();
        Long userId = saved.getUserId();

        int threadCount = 8;
        List<Object> results = runConcurrently(threadCount,
                () -> couponUseService.use(userId, couponId));

        long successCount = results.stream().filter(r -> r instanceof CouponResponse).count();
        long alreadyUsedCount = results.stream().filter(r -> r instanceof CouponAlreadyUsedException).count();

        assertEquals(1, successCount);
        assertEquals(threadCount - 1, alreadyUsedCount);
        assertEquals(UserCouponStatus.USED,
                userCouponRepository.findById(couponId).orElseThrow().getStatus());
    }

    @Test
    void 사용과_취소가_동시에_오면_하나만_성공한다() throws InterruptedException {
        UserCoupon saved = userCouponRepository.saveAndFlush(newCoupon(7002L));
        Long couponId = saved.getId();
        Long userId = saved.getUserId();

        List<Object> results = runConcurrently(2, List.of(
                () -> couponUseService.use(userId, couponId),
                () -> adminCouponService.cancel(couponId, "동시성 테스트 취소")
        ));

        long successCount = results.stream().filter(r -> r instanceof CouponResponse).count();
        assertEquals(1, successCount);

        UserCouponStatus finalStatus = userCouponRepository.findById(couponId).orElseThrow().getStatus();
        assertTrue(finalStatus == UserCouponStatus.USED || finalStatus == UserCouponStatus.CANCELLED);
    }

    @Test
    void 같은_claimId_발급이_동시에_두번_들어와도_하나만_생성된다() throws InterruptedException {
        CouponClaimAcceptedEvent event = new CouponClaimAcceptedEvent(
                1, UUID.randomUUID().toString(),
                7003L, 1L, 10L, null, 100L,
                "RATE", new BigDecimal("50.00"),
                Instant.now().plus(7, ChronoUnit.DAYS), Instant.now()
        );

        List<Object> results = runConcurrently(2,
                () -> { couponIssuanceService.issue(event); return "OK"; });

        long successCount = results.stream().filter(r -> "OK".equals(r)).count();
        long conflictCount = results.stream().filter(r -> r instanceof DataIntegrityViolationException).count();

        assertEquals(1, successCount);
        assertEquals(1, conflictCount);
        assertTrue(userCouponRepository.existsByClaimId(7003L));
    }

    private List<Object> runConcurrently(int threadCount, java.util.function.Supplier<Object> task) throws InterruptedException {
        return runConcurrently(threadCount, Collections.nCopies(threadCount, task));
    }

    private List<Object> runConcurrently(int threadCount, List<java.util.function.Supplier<Object>> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Object> results = Collections.synchronizedList(new ArrayList<>());

        for (java.util.function.Supplier<Object> task : tasks) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results.add(task.get());
                } catch (Exception e) {
                    results.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        return results;
    }
}