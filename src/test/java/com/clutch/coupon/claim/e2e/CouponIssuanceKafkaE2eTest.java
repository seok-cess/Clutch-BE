package com.clutch.coupon.claim.e2e;

import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.outbox.CouponClaimOutbox;
import com.clutch.coupon.claim.outbox.CouponClaimOutboxRepository;
import com.clutch.coupon.claim.outbox.CouponClaimOutboxSender;
import com.clutch.coupon.claim.outbox.CouponClaimOutboxStatus;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.kafka.CouponClaimAcceptedEvent;
import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import com.clutch.coupon.statistics.service.CouponIssueStatisticsService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.domain.WalletOutbox;
import com.clutch.wallet.domain.WalletOutboxStatus;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.repository.WalletOutboxRepository;
import com.clutch.wallet.service.CouponIssuanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 쿠폰 발급 Kafka 전체 흐름 통합 테스트
 */
@SpringBootTest(
        properties = {
                "coupon.claim.outbox.enabled=false",
                "wallet.outbox.enabled=true",
                "wallet.outbox.publish-interval-ms=100"
        }
)
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class CouponIssuanceKafkaE2eTest {

    private static final String RESULT_GROUP =
            "coupon-issuance-e2e-"
                    + UUID.randomUUID();

    private static final String ACCEPTED_GROUP =
            "coupon-issuance-e2e-accepted-"
                    + UUID.randomUUID();

    @Autowired
    private CouponClaimRequestRepository
            claimRequestRepository;

    @Autowired
    private CouponClaimOutboxRepository
            claimOutboxRepository;

    @Autowired
    private CouponClaimOutboxSender
            claimOutboxSender;

    @Autowired
    private UserCouponRepository
            userCouponRepository;

    @Autowired
    private WalletOutboxRepository
            walletOutboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoSpyBean
    private CouponIssuanceService couponIssuanceService;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    @MockitoBean
    private CouponIssueStatisticsService statisticsService;

    private Long claimId;
    private Long claimOutboxId;

    /**
     * Kafka 결과 Consumer 테스트 설정
     *
     * @param registry 동적 설정 저장소
     */
    @DynamicPropertySource
    static void kafkaProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "coupon.claim.kafka.accepted-group",
                () -> ACCEPTED_GROUP
        );
        registry.add(
                "coupon.claim.kafka.issue-result-group",
                () -> RESULT_GROUP
        );
        registry.add(
                "spring.kafka.consumer.auto-offset-reset",
                () -> "latest"
        );
    }

    /**
     * 쿠폰 발급 Kafka 전체 흐름 검증
     */
    @Test
    void acceptedClaimCompletesWholeKafkaFlow()
            throws Exception {
        // given
        long baseId =
                ThreadLocalRandom.current().nextLong(
                        1_000_000L,
                        Long.MAX_VALUE - 10
                );

        CouponClaimRequest claimRequest =
                CouponClaimRequest.create(
                        baseId,
                        null,
                        baseId + 1,
                        baseId + 2
                );

        CouponClaimRequest savedClaim =
                claimRequestRepository.saveAndFlush(
                        claimRequest
                );

        claimId = savedClaim.getId();

        String messageId =
                UUID.randomUUID().toString();

        Instant expiresAt =
                Instant.now().plus(
                        Duration.ofDays(7)
                );

        CouponClaimAcceptedEvent event =
                new CouponClaimAcceptedEvent(
                        1,
                        messageId,
                        claimId,
                        savedClaim.getUserId(),
                        savedClaim.getCouponEventId(),
                        savedClaim
                                .getCouponEventOccurrenceId(),
                        savedClaim.getCouponEventItemId(),
                        "RATE",
                        new BigDecimal("20.00"),
                        expiresAt,
                        Instant.now()
                );

        String payload =
                objectMapper.writeValueAsString(event);

        CouponClaimOutbox claimOutbox =
                CouponClaimOutbox.create(
                        messageId,
                        claimId,
                        CouponKafkaTopics.CLAIM_ACCEPTED,
                        payload
                );

        CouponClaimOutbox savedOutbox =
                claimOutboxRepository.saveAndFlush(
                        claimOutbox
                );

        claimOutboxId = savedOutbox.getId();

        // when
        claimOutboxSender.send(claimOutboxId);

        awaitCompletedFlow();

        // 동일 Kafka 이벤트 재전송
        kafkaTemplate.send(
                CouponKafkaTopics.CLAIM_ACCEPTED,
                String.valueOf(claimId),
                payload
        ).get(5, TimeUnit.SECONDS);

        // Kafka와 Outbox는 적어도 한 번 전달되므로, 재시도·동시 발행으로
        // 동일 이벤트가 더 소비될 수 있다. 최종 상태의 멱등성은 아래에서 검증한다.
        verify(
                couponIssuanceService,
                timeout(10_000).atLeast(2)
        ).issue(
                argThat((CouponClaimAcceptedEvent acceptedEvent) ->
                        claimId.equals(
                                acceptedEvent.claimId()
                        )
                )
        );

        // then
        CouponClaimRequest completedClaim =
                claimRequestRepository
                        .findById(claimId)
                        .orElseThrow();

        UserCoupon issuedCoupon =
                userCouponRepository
                        .findByClaimId(claimId)
                        .orElseThrow();

        CouponClaimOutbox sentClaimOutbox =
                claimOutboxRepository
                        .findById(claimOutboxId)
                        .orElseThrow();

        WalletOutbox sentResultOutbox =
                findResultOutbox();

        long issuedCouponCount =
                userCouponRepository
                        .findAll()
                        .stream()
                        .filter(coupon ->
                                claimId.equals(
                                        coupon.getClaimId()
                                )
                        )
                        .count();

        long resultOutboxCount =
                walletOutboxRepository
                        .findAll()
                        .stream()
                        .filter(outbox ->
                                claimId.equals(
                                        outbox.getAggregateId()
                                )
                        )
                        .count();

        assertThat(completedClaim.getRequestStatus())
                .isEqualTo(
                        ClaimRequestStatus.SUCCEEDED
                );
        assertThat(completedClaim.getCompletedAt())
                .isNotNull();
        assertThat(completedClaim.getFailureReason())
                .isNull();

        assertThat(issuedCoupon.getClaimId())
                .isEqualTo(claimId);
        assertThat(issuedCoupon.getUserId())
                .isEqualTo(savedClaim.getUserId());
        assertThat(issuedCoupon.getStatus())
                .isEqualTo(UserCouponStatus.ISSUED);
        assertThat(issuedCoupon.getDiscountType())
                .isEqualTo("RATE");
        assertThat(issuedCoupon.getDiscountValue())
                .isEqualByComparingTo("20.00");
        assertThat(issuedCoupon.getExpiresAt())
                .isAfter(
                        Instant.now().plus(
                                Duration.ofDays(6)
                        )
                );

        assertThat(sentClaimOutbox.getStatus())
                .isEqualTo(
                        CouponClaimOutboxStatus.SENT
                );
        assertThat(sentResultOutbox.getStatus())
                .isEqualTo(
                        WalletOutboxStatus.SENT
                );
        assertThat(issuedCouponCount)
                .isEqualTo(1);
        assertThat(resultOutboxCount)
                .isEqualTo(1);
    }

    /**
     * Kafka 전체 흐름 완료 대기
     */
    private void awaitCompletedFlow()
            throws InterruptedException {
        Instant deadline =
                Instant.now().plusSeconds(30);

        while (Instant.now().isBefore(deadline)) {
            boolean claimSucceeded =
                    claimRequestRepository
                            .findById(claimId)
                            .map(request ->
                                    request.getRequestStatus()
                                            == ClaimRequestStatus
                                            .SUCCEEDED
                            )
                            .orElse(false);

            boolean couponIssued =
                    userCouponRepository
                            .existsByClaimId(claimId);

            boolean resultSent =
                    walletOutboxRepository
                            .findAll()
                            .stream()
                            .anyMatch(outbox ->
                                    claimId.equals(
                                            outbox.getAggregateId()
                                    )
                                            && CouponKafkaTopics
                                            .ISSUE_RESULT
                                            .equals(
                                                    outbox.getTopic()
                                            )
                                            && outbox.getStatus()
                                            == WalletOutboxStatus
                                            .SENT
                            );

            if (claimSucceeded
                    && couponIssued
                    && resultSent) {
                return;
            }

            Thread.sleep(200);
        }

        throw new AssertionError(
                "쿠폰 발급 Kafka 전체 흐름 시간 초과"
        );
    }

    /**
     * 발급 결과 Outbox 조회
     *
     * @return 발급 결과 Outbox
     */
    private WalletOutbox findResultOutbox() {
        return walletOutboxRepository
                .findAll()
                .stream()
                .filter(outbox ->
                        claimId.equals(
                                outbox.getAggregateId()
                        )
                                && CouponKafkaTopics
                                .ISSUE_RESULT
                                .equals(outbox.getTopic())
                )
                .findFirst()
                .orElseThrow();
    }

    /**
     * E2E 테스트 데이터 정리
     */
    @AfterEach
    void tearDown() {
        if (claimId != null) {
            walletOutboxRepository.deleteAll(
                    walletOutboxRepository
                            .findAll()
                            .stream()
                            .filter(outbox ->
                                    claimId.equals(
                                            outbox.getAggregateId()
                                    )
                            )
                            .toList()
            );

            userCouponRepository
                    .findByClaimId(claimId)
                    .ifPresent(
                            userCouponRepository::delete
                    );
        }

        if (claimOutboxId != null
                && claimOutboxRepository
                .existsById(claimOutboxId)) {
            claimOutboxRepository
                    .deleteById(claimOutboxId);
        }

        if (claimId != null
                && claimRequestRepository
                .existsById(claimId)) {
            claimRequestRepository
                    .deleteById(claimId);
        }
    }
}
