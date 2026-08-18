package com.clutch.coupon.claim.outbox;

import com.clutch.lolesports.service.PollingScheduler;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 Outbox Kafka 통합 테스트
 */
@SpringBootTest(
        properties =
                "coupon.claim.outbox.enabled=false"
)
class CouponClaimOutboxKafkaIntegrationTest {

    private static final String TEST_TOPIC =
            "coupon.claim.accepted.integration-test";

    @Autowired
    private CouponClaimOutboxRepository outboxRepository;

    @Autowired
    private CouponClaimOutboxSender outboxSender;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    private Long savedOutboxId;

    /**
     * 실제 Kafka 발행 및 소비 검증
     */
    @Test
    void sendPublishesMessageToKafka() {
        // given
        String messageId = UUID.randomUUID().toString();

        long aggregateId =
                ThreadLocalRandom.current().nextLong(
                        1_000_000L,
                        Long.MAX_VALUE
                );

        String payload =
                """
                {"messageId":"%s","claimId":%d}
                """.formatted(messageId, aggregateId)
                        .trim();

        CouponClaimOutbox outbox =
                CouponClaimOutbox.create(
                        messageId,
                        aggregateId,
                        TEST_TOPIC,
                        payload
                );

        CouponClaimOutbox savedOutbox =
                outboxRepository.saveAndFlush(outbox);

        savedOutboxId = savedOutbox.getId();

        Properties consumerProperties =
                consumerProperties();

        try (
                KafkaConsumer<String, String> consumer =
                        new KafkaConsumer<>(consumerProperties)
        ) {
            consumer.subscribe(List.of(TEST_TOPIC));

            // when
            outboxSender.send(savedOutboxId);

            ConsumerRecord<String, String> receivedRecord =
                    findMessage(
                            consumer,
                            messageId
                    );

            // then
            CouponClaimOutbox sentOutbox =
                    outboxRepository
                            .findById(savedOutboxId)
                            .orElseThrow();

            assertThat(sentOutbox.getStatus())
                    .isEqualTo(
                            CouponClaimOutboxStatus.SENT
                    );
            assertThat(sentOutbox.getSentAt()).isNotNull();

            assertThat(receivedRecord).isNotNull();
            assertThat(receivedRecord.key())
                    .isEqualTo(
                            String.valueOf(aggregateId)
                    );
            assertThat(receivedRecord.value())
                    .contains(
                            messageId,
                            String.valueOf(aggregateId)
                    );
        }
    }

    private ConsumerRecord<String, String> findMessage(
            KafkaConsumer<String, String> consumer,
            String expectedMessageId
    ) {
        Instant deadline =
                Instant.now().plusSeconds(10);

        while (Instant.now().isBefore(deadline)) {
            for (
                    ConsumerRecord<String, String> record
                    : consumer.poll(Duration.ofMillis(500))
            ) {
                if (record.value().contains(expectedMessageId)) {
                    return record;
                }
            }
        }

        return null;
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "coupon-claim-outbox-test-"
                        + UUID.randomUUID()
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        return properties;
    }

    /**
     * 통합 테스트 Outbox 데이터 정리
     */
    @AfterEach
    void tearDown() {
        if (savedOutboxId != null
                && outboxRepository.existsById(savedOutboxId)) {
            outboxRepository.deleteById(savedOutboxId);
        }
    }
}