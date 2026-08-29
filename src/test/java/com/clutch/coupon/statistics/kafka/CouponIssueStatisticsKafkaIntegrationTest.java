package com.clutch.coupon.statistics.kafka;

import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultStatus;
import com.clutch.coupon.statistics.repository.CouponIssueStatisticsEventRow;
import com.clutch.coupon.statistics.repository.CouponIssueStatisticsRepository;
import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "coupon.claim.outbox.enabled=false",
        "wallet.outbox.enabled=false",
        "coupon.success-count-sync.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CouponIssueStatisticsKafkaIntegrationTest {

    private static final String TEST_TOPIC =
            "coupon.issue.statistics.integration-" + UUID.randomUUID();
    private static final String TEST_RESULT_GROUP =
            "coupon.issue.statistics.result-" + UUID.randomUUID();
    private static final String TEST_DLT_GROUP =
            "coupon.issue.statistics.dlt-" + UUID.randomUUID();

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CouponClaimRequestRepository claimRequestRepository;

    @Autowired
    private CouponIssueStatisticsRepository statisticsRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    private long couponEventId;
    private final List<Long> claimIds = new ArrayList<>();

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("coupon.claim.kafka.issue-result-topic", () -> TEST_TOPIC);
        registry.add(
                "coupon.claim.kafka.issue-result-group",
                () -> TEST_RESULT_GROUP
        );
        registry.add(
                "coupon.claim.kafka.statistics-dlt-group",
                () -> TEST_DLT_GROUP
        );
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @BeforeEach
    void setUp() {
        couponEventId = ThreadLocalRandom.current().nextLong(
                7_100_000L,
                7_900_000L
        );
        jdbcTemplate.update("""
                INSERT INTO coupon_event (
                    coupon_event_id,
                    esports_match_id,
                    event_name,
                    issue_mode,
                    trigger_type,
                    event_status,
                    claim_window_seconds
                ) VALUES (
                    :couponEventId,
                    -1,
                    'Kafka 통계 통합 테스트',
                    'SINGLE_FIRST_COME',
                    :triggerType,
                    'OPEN',
                    300
                )
                """, new MapSqlParameterSource()
                .addValue("couponEventId", couponEventId)
                .addValue("triggerType", "KAFKA_STAT_" + UUID.randomUUID()));
    }

    @AfterEach
    void tearDown() {
        claimRequestRepository.deleteAllById(claimIds);
        MapSqlParameterSource parameters =
                new MapSqlParameterSource("couponEventId", couponEventId);
        jdbcTemplate.update("""
                DELETE FROM coupon_kafka_processing_error
                 WHERE coupon_event_id = :couponEventId
                """, parameters);
        jdbcTemplate.update("""
                DELETE FROM coupon_event
                 WHERE coupon_event_id = :couponEventId
                """, parameters);
    }

    @Test
    void 결과는_한_번만_집계하고_재시도_소진은_DLT_오류로_기록한다()
            throws Exception {
        CouponClaimRequest successClaim = saveClaim(100L);
        CouponIssueResultEvent successEvent = new CouponIssueResultEvent(
                1,
                UUID.randomUUID().toString(),
                successClaim.getId(),
                200L,
                CouponIssueResultStatus.SUCCEEDED,
                null,
                Instant.now()
        );
        String successPayload = objectMapper.writeValueAsString(successEvent);

        kafkaTemplate.send(
                TEST_TOPIC,
                String.valueOf(successClaim.getId()),
                successPayload
        ).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send(
                TEST_TOPIC,
                String.valueOf(successClaim.getId()),
                successPayload
        ).get(5, TimeUnit.SECONDS);

        awaitStatistics(1, 0);
        assertThat(claimRequestRepository.findById(successClaim.getId()))
                .get()
                .extracting(CouponClaimRequest::getRequestStatus)
                .isEqualTo(ClaimRequestStatus.SUCCEEDED);

        CouponClaimRequest errorClaim = saveClaim(101L);
        CouponIssueResultEvent unsupportedEvent = new CouponIssueResultEvent(
                2,
                UUID.randomUUID().toString(),
                errorClaim.getId(),
                null,
                CouponIssueResultStatus.FAILED,
                "지원하지 않는 버전",
                Instant.now()
        );
        kafkaTemplate.send(
                TEST_TOPIC,
                String.valueOf(errorClaim.getId()),
                objectMapper.writeValueAsString(unsupportedEvent)
        ).get(5, TimeUnit.SECONDS);

        CouponIssueStatisticsEventRow statistics = awaitStatistics(1, 1);
        assertThat(statistics.successCount()).isEqualTo(1);
        assertThat(statistics.failureCount()).isZero();
        assertThat(statistics.processingErrorCount()).isEqualTo(1);
        assertThat(claimRequestRepository.findById(errorClaim.getId()))
                .get()
                .extracting(CouponClaimRequest::getRequestStatus)
                .isEqualTo(ClaimRequestStatus.PENDING);
    }

    private CouponClaimRequest saveClaim(long userId) {
        CouponClaimRequest saved = claimRequestRepository.saveAndFlush(
                CouponClaimRequest.create(
                        couponEventId,
                        null,
                        couponEventId + 1,
                        userId
                )
        );
        claimIds.add(saved.getId());
        return saved;
    }

    private CouponIssueStatisticsEventRow awaitStatistics(
            long successCount,
            long processingErrorCount
    ) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(25));
        while (Instant.now().isBefore(deadline)) {
            CouponIssueStatisticsEventRow row = statisticsRepository
                    .findEvents(100)
                    .stream()
                    .filter(event -> event.couponEventId().equals(couponEventId))
                    .findFirst()
                    .orElse(null);
            if (row != null
                    && row.successCount() == successCount
                    && row.processingErrorCount() == processingErrorCount) {
                return row;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("쿠폰 Kafka 발급 통계 반영 시간 초과");
    }
}
