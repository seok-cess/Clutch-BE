package com.clutch.coupon.statistics.kafka;

import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import com.clutch.coupon.statistics.service.CouponIssueStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 발급 결과 Consumer에서 재시도를 소진한 DLT 메시지를 관리자 오류 통계로 기록한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueResultDltConsumer {

    private final CouponIssueStatisticsService statisticsService;

    @KafkaListener(
            id = "couponIssueResultDltConsumer",
            topics = "${coupon.claim.kafka.issue-result-topic:"
                    + CouponKafkaTopics.ISSUE_RESULT
                    + "}-dlt",
            groupId = "${coupon.claim.kafka.statistics-dlt-group:"
                    + "clutch-coupon-issue-statistics-dlt}",
            containerFactory = "couponStatisticsDltKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        CouponIssueResultDltRecord dltRecord = toDltRecord(record);
        boolean recorded = statisticsService.recordProcessingError(dltRecord);
        log.info(
                "쿠폰 발급 결과 DLT 처리: originalTopic={}, partition={}, offset={}, recorded={}",
                dltRecord.originalTopic(),
                dltRecord.originalPartition(),
                dltRecord.originalOffset(),
                recorded
        );
    }

    CouponIssueResultDltRecord toDltRecord(
            ConsumerRecord<String, String> record
    ) {
        return new CouponIssueResultDltRecord(
                record.key(),
                record.value(),
                stringHeader(
                        record,
                        KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP,
                        "unknown"
                ),
                stringHeader(
                        record,
                        KafkaHeaders.DLT_ORIGINAL_TOPIC,
                        removeDltSuffix(record.topic())
                ),
                intHeader(
                        record,
                        KafkaHeaders.DLT_ORIGINAL_PARTITION,
                        record.partition()
                ),
                longHeader(
                        record,
                        KafkaHeaders.DLT_ORIGINAL_OFFSET,
                        record.offset()
                ),
                stringHeader(record, KafkaHeaders.DLT_EXCEPTION_FQCN, null),
                stringHeader(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE, null),
                originalOccurredAt(record)
        );
    }

    private LocalDateTime originalOccurredAt(
            ConsumerRecord<String, String> record
    ) {
        long timestamp = longHeader(
                record,
                KafkaHeaders.DLT_ORIGINAL_TIMESTAMP,
                record.timestamp()
        );
        if (timestamp < 0) {
            return null;
        }
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneOffset.UTC
        );
    }

    private String stringHeader(
            ConsumerRecord<String, String> record,
            String name,
            String fallback
    ) {
        Header header = record.headers().lastHeader(name);
        return header == null
                ? fallback
                : new String(header.value(), StandardCharsets.UTF_8);
    }

    private int intHeader(
            ConsumerRecord<String, String> record,
            String name,
            int fallback
    ) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value().length != Integer.BYTES
                ? fallback
                : ByteBuffer.wrap(header.value()).getInt();
    }

    private long longHeader(
            ConsumerRecord<String, String> record,
            String name,
            long fallback
    ) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value().length != Long.BYTES
                ? fallback
                : ByteBuffer.wrap(header.value()).getLong();
    }

    private String removeDltSuffix(String topic) {
        if (topic.endsWith("-dlt")) {
            return topic.substring(0, topic.length() - 4);
        }
        if (topic.endsWith(".DLT")) {
            return topic.substring(0, topic.length() - 4);
        }
        return topic;
    }
}
