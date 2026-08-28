package com.clutch.coupon.statistics.kafka;

import com.clutch.coupon.statistics.service.CouponIssueStatisticsService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponIssueResultDltConsumerTest {

    @Mock
    private CouponIssueStatisticsService statisticsService;

    @Test
    void DLT_헤더에서_원본_메시지_좌표와_예외를_추출한다() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "coupon.issue.result-dlt",
                0,
                99L,
                "10",
                "payload"
        );
        record.headers().add(stringHeader(
                KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP,
                "clutch-coupon-issue-result"
        ));
        record.headers().add(stringHeader(
                KafkaHeaders.DLT_ORIGINAL_TOPIC,
                "coupon.issue.result"
        ));
        record.headers().add(new RecordHeader(
                KafkaHeaders.DLT_ORIGINAL_PARTITION,
                ByteBuffer.allocate(Integer.BYTES).putInt(2).array()
        ));
        record.headers().add(new RecordHeader(
                KafkaHeaders.DLT_ORIGINAL_OFFSET,
                ByteBuffer.allocate(Long.BYTES).putLong(33L).array()
        ));
        record.headers().add(stringHeader(
                KafkaHeaders.DLT_EXCEPTION_FQCN,
                "java.lang.IllegalStateException"
        ));
        record.headers().add(stringHeader(
                KafkaHeaders.DLT_EXCEPTION_MESSAGE,
                "통계 저장 실패"
        ));

        new CouponIssueResultDltConsumer(statisticsService).consume(record);

        ArgumentCaptor<CouponIssueResultDltRecord> captor =
                ArgumentCaptor.forClass(CouponIssueResultDltRecord.class);
        verify(statisticsService).recordProcessingError(captor.capture());
        CouponIssueResultDltRecord dltRecord = captor.getValue();
        assertThat(dltRecord.originalConsumerGroup())
                .isEqualTo("clutch-coupon-issue-result");
        assertThat(dltRecord.originalTopic()).isEqualTo("coupon.issue.result");
        assertThat(dltRecord.originalPartition()).isEqualTo(2);
        assertThat(dltRecord.originalOffset()).isEqualTo(33L);
        assertThat(dltRecord.exceptionMessage()).isEqualTo("통계 저장 실패");
    }

    private RecordHeader stringHeader(String name, String value) {
        return new RecordHeader(
                name,
                value.getBytes(StandardCharsets.UTF_8)
        );
    }
}
