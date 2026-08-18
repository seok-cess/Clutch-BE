package com.clutch.wallet.domain;

import jakarta.persistence.*;

import java.time.Instant;

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

    public static WalletOutbox create(Long aggregateId, String topic, String payload){
        WalletOutbox outbox = new WalletOutbox();
        outbox.aggregateId = aggregateId;
        outbox.topic = topic;
        outbox.payload = payload;
        outbox.status = WalletOutboxStatus.PENDING;
        return outbox;
    }

    public void markSent(Instant sentAt){
        this.status = WalletOutboxStatus.SENT;
        this.sentAt = sentAt;
    }

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
