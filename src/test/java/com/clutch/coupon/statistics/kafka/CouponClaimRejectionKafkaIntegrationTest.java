package com.clutch.coupon.statistics.kafka;

import com.clutch.coupon.admin.dashboard.repository.AdminCouponDashboardQueryRepository;
import com.clutch.coupon.admin.dashboard.repository.CouponDashboardAggregateRow;
import com.clutch.coupon.contract.kafka.CouponClaimRejectedEvent;
import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.AfterEach;
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
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "coupon.claim.outbox.enabled=false",
        "wallet.outbox.enabled=false",
        "coupon.success-count-sync.enabled=false",
        "spring.task.scheduling.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CouponClaimRejectionKafkaIntegrationTest {

    private static final String TEST_TOPIC =
            "coupon.claim.rejected.integration-" + UUID.randomUUID();
    private static final String TEST_GROUP =
            "coupon.claim.rejected.integration-" + UUID.randomUUID();
    private static final LocalDateTime START_UTC =
            LocalDateTime.of(2099, 1, 1, 15, 0);
    private static final LocalDateTime END_UTC =
            LocalDateTime.of(2099, 1, 2, 15, 0);
    private static final long REJECTION_EVENT_ID = 9_910_001L;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private AdminCouponDashboardQueryRepository dashboardRepository;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    private String messageId;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("coupon.claim.kafka.rejected-topic", () -> TEST_TOPIC);
        registry.add("coupon.claim.kafka.rejected-group", () -> TEST_GROUP);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @AfterEach
    void tearDown() {
        if (messageId != null) {
            int deleted = jdbcTemplate.update("""
                    DELETE FROM coupon_claim_rejection_message
                     WHERE message_id = :messageId
                    """, new MapSqlParameterSource(
                    "messageId",
                    messageId
            ));
            if (deleted == 1) {
                jdbcTemplate.update("""
                        UPDATE coupon_issue_daily_statistics
                           SET rejection_count = rejection_count - 1
                         WHERE coupon_event_id = :couponEventId
                           AND statistics_date = '2099-01-02'
                        """, new MapSqlParameterSource(
                        "couponEventId",
                        REJECTION_EVENT_ID
                ));
                jdbcTemplate.update("""
                        DELETE FROM coupon_issue_daily_statistics
                         WHERE coupon_event_id = :couponEventId
                           AND statistics_date = '2099-01-02'
                           AND success_count = 0
                           AND failure_count = 0
                           AND rejection_count = 0
                        """, new MapSqlParameterSource(
                        "couponEventId",
                        REJECTION_EVENT_ID
                ));
            }
        }
    }

    @Test
    void Kafka_재전달에도_거절을_한_번만_실패_통계에_반영한다()
            throws Exception {
        CouponDashboardAggregateRow before = dashboardRepository
                .findAggregate(START_UTC, END_UTC);
        messageId = UUID.randomUUID().toString();
        CouponClaimRejectedEvent event = new CouponClaimRejectedEvent(
                1,
                messageId,
                REJECTION_EVENT_ID,
                9_910_002L,
                "COUPON_ALREADY_CLAIMED",
                Instant.parse("2099-01-02T01:00:00Z")
        );
        String payload = objectMapper.writeValueAsString(event);

        kafkaTemplate.send(TEST_TOPIC, "9910001", payload)
                .get(5, TimeUnit.SECONDS);
        kafkaTemplate.send(TEST_TOPIC, "9910001", payload)
                .get(5, TimeUnit.SECONDS);

        CouponDashboardAggregateRow aggregate = awaitAggregate(before);
        assertThat(aggregate.requestCount())
                .isEqualTo(before.requestCount() + 1);
        assertThat(aggregate.failedCount())
                .isEqualTo(before.failedCount() + 1);
    }

    private CouponDashboardAggregateRow awaitAggregate(
            CouponDashboardAggregateRow before
    )
            throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            CouponDashboardAggregateRow aggregate = dashboardRepository
                    .findAggregate(START_UTC, END_UTC);
            if (aggregate.failedCount() == before.failedCount() + 1) {
                return aggregate;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("쿠폰 신청 거절 Kafka 통계 반영 시간 초과");
    }
}
