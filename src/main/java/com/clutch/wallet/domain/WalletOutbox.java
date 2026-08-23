package com.clutch.wallet.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Kafka로 전송할 지갑 도메인 이벤트를 임시 저장하는 아웃박스.
 *
 * <p>도메인 상태 변경과 같은 트랜잭션에서 저장되어 발행 실패에도
 * 이벤트가 유실되지 않도록 한다.</p>
 */
@Entity
@Table(name = "wallet_outbox")
public class WalletOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected WalletOutbox(){}

    /**
     * 미발행({@code PENDING}) 상태의 아웃박스 레코드를 생성한다.
     *
     * @param aggregateId 이벤트가 속한 ID
     * @param topic 발행할 Kafka 토픽
     * @param payload 직렬화된 이벤트 페이로드
     * @return 생성된 아웃박스 레코드
     */
    public static WalletOutbox create(Long aggregateId, String topic, String payload){
        WalletOutbox outbox = new WalletOutbox();
        outbox.aggregateId = aggregateId;
        outbox.topic = topic;
        outbox.payload = payload;
        outbox.status = WalletOutboxStatus.PENDING;
        return outbox;
    }

    /**
     * 아웃박스를 발행 완료 상태로 변경한다.
     *
     * @param sentAt 발행이 완료된 시각
     */
    public void markSent(Instant sentAt){
        this.status = WalletOutboxStatus.SENT;
        this.sentAt = sentAt;
    }

    /** 발행 재시도 횟수를 1 증가시킨다. */
    public void increaseRetryCount(){
        this.retryCount++;
    }

    public Long getId() { return  id; }
    public Long getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public WalletOutboxStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getSentAt() { return sentAt; }
}
