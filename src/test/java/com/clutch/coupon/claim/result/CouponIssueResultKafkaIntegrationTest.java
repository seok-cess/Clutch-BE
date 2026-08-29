package com.clutch.coupon.claim.result;

import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultStatus;
import com.clutch.coupon.statistics.service.CouponIssueStatisticsService;
import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 생성 결과 Kafka 통합 테스트
 */
@SpringBootTest(
        properties =
                "coupon.claim.outbox.enabled=false"
)
class CouponIssueResultKafkaIntegrationTest {

    private static final String TEST_TOPIC =
            "coupon.issue.result.integration-"
                    + UUID.randomUUID();

    private static final String TEST_GROUP =
            "coupon.issue.result.group-"
                    + UUID.randomUUID();

    @Autowired
    private CouponClaimRequestRepository
            claimRequestRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    @MockitoBean
    private CouponIssueStatisticsService statisticsService;

    private Long savedClaimId;

    /**
     * 테스트 Kafka 설정
     *
     * @param registry 동적 설정 저장소
     */
    @DynamicPropertySource
    static void kafkaProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "coupon.claim.kafka.issue-result-topic",
                () -> TEST_TOPIC
        );
        registry.add(
                "coupon.claim.kafka.issue-result-group",
                () -> TEST_GROUP
        );
        registry.add(
                "spring.kafka.consumer.auto-offset-reset",
                () -> "earliest"
        );
    }

    /**
     * 쿠폰 생성 성공 결과 처리 검증
     */
    @Test
    void successResultChangesClaimToSucceeded()
            throws Exception {
        // given
        long randomId =
                ThreadLocalRandom.current().nextLong(
                        1_000_000L,
                        Long.MAX_VALUE
                );

        CouponClaimRequest claimRequest =
                CouponClaimRequest.create(
                        randomId,
                        null,
                        randomId + 1,
                        randomId + 2
                );

        CouponClaimRequest savedClaim =
                claimRequestRepository.saveAndFlush(
                        claimRequest
                );

        savedClaimId = savedClaim.getId();

        CouponIssueResultEvent event =
                new CouponIssueResultEvent(
                        1,
                        UUID.randomUUID().toString(),
                        savedClaimId,
                        randomId + 3,
                        CouponIssueResultStatus.SUCCEEDED,
                        null,
                        Instant.now()
                );

        String payload =
                objectMapper.writeValueAsString(event);

        // when
        kafkaTemplate.send(
                TEST_TOPIC,
                String.valueOf(savedClaimId),
                payload
        ).get(5, TimeUnit.SECONDS);

        CouponClaimRequest completedClaim =
                awaitStatus(
                        ClaimRequestStatus.SUCCEEDED
                );


        // then
        assertThat(completedClaim.getRequestStatus())
                .isEqualTo(
                        ClaimRequestStatus.SUCCEEDED
                );
        assertThat(completedClaim.getCompletedAt())
                .isNotNull();
        assertThat(completedClaim.getFailureReason())
                .isNull();
    }

    /**
     * 발급 상태 변경 대기
     *
     * @param expectedStatus 예상 상태
     * @return 쿠폰 발급 요청
     */
    private CouponClaimRequest awaitStatus(
            ClaimRequestStatus expectedStatus
    ) throws InterruptedException {
        Instant deadline =
                Instant.now().plusSeconds(20);

        while (Instant.now().isBefore(deadline)) {
            CouponClaimRequest claimRequest =
                    claimRequestRepository
                            .findById(savedClaimId)
                            .orElseThrow();

            if (claimRequest.getRequestStatus()
                    == expectedStatus) {
                return claimRequest;
            }

            Thread.sleep(200);
        }

        throw new AssertionError(
                "쿠폰 발급 요청 상태 변경 시간 초과"
        );
    }
    
    /**
     * 쿠폰 생성 실패 결과 처리 검증
     */
    @Test
    void failedResultChangesClaimToFailed()
            throws Exception {
        // given
        long randomId =
                ThreadLocalRandom.current().nextLong(
                        1_000_000L,
                        Long.MAX_VALUE
                );

        CouponClaimRequest claimRequest =
                CouponClaimRequest.create(
                        randomId,
                        null,
                        randomId + 1,
                        randomId + 2
                );

        CouponClaimRequest savedClaim =
                claimRequestRepository.saveAndFlush(
                        claimRequest
                );

        savedClaimId = savedClaim.getId();

        CouponIssueResultEvent event =
                new CouponIssueResultEvent(
                        1,
                        UUID.randomUUID().toString(),
                        savedClaimId,
                        null,
                        CouponIssueResultStatus.FAILED,
                        "쿠폰 생성 실패",
                        Instant.now()
                );

        String payload =
                objectMapper.writeValueAsString(event);

        // when
        kafkaTemplate.send(
                TEST_TOPIC,
                String.valueOf(savedClaimId),
                payload
        ).get(5, TimeUnit.SECONDS);

        CouponClaimRequest failedClaim =
                awaitStatus(
                        ClaimRequestStatus.FAILED
                );

        // then
        assertThat(failedClaim.getRequestStatus())
                .isEqualTo(
                        ClaimRequestStatus.FAILED
                );
        assertThat(failedClaim.getCompletedAt())
                .isNotNull();
        assertThat(failedClaim.getFailureReason())
                .isEqualTo("쿠폰 생성 실패");
    }

    /**
     * 테스트 발급 요청 정리
     */
    @AfterEach
    void tearDown() {
        if (savedClaimId != null
                && claimRequestRepository
                .existsById(savedClaimId)) {
            claimRequestRepository
                    .deleteById(savedClaimId);
        }
    }
}
