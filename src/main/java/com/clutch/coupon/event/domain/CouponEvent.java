package com.clutch.coupon.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 쿠폰 이벤트 엔티티
 */
@Getter
@Entity
@Table(name = "coupon_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEvent {

    /**
     * 쿠폰 이벤트 식별자
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_event_id", nullable = false)
    private Long id;

    /**
     * 이스포츠 경기 식별자
     */
    @Column(name = "esports_match_id", nullable = false)
    private Long esportsMatchId;

    /**
     * 쿠폰 이벤트 이름
     */
    @Column(name = "event_name", nullable = false, length = 200)
    private String eventName;

    /**
     * 쿠폰 이벤트 발동 조건
     */
    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    /**
     * 쿠폰 이벤트 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    private CouponEventStatus eventStatus;

    /**
     * 쿠폰 발급 요청 가능 시간
     */
    @Column(name = "claim_window_seconds", nullable = false)
    private int claimWindowSeconds;


    /**
     * 쿠폰 발급 방식
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "issuance_mode", nullable = false, length = 20)
    private CouponIssuanceMode issuanceMode;

    /**
     * 생성 시각
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 시각
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
