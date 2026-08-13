package com.clutch.coupon.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
     * 쿠폰 이벤트 유형
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * 쿠폰 이벤트 설명
     */
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /**
     * 쿠폰 이벤트 시작 시각
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * 쿠폰 이벤트 종료 시각
     */
    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

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

    /**
     * 쿠폰 이벤트 진행 여부
     *
     * @param currentTime 현재 시각
     * @return 쿠폰 이벤트 진행 여부
     */
    public boolean isOpenAt(LocalDateTime currentTime) {
        return !currentTime.isBefore(startedAt)
                && currentTime.isBefore(closedAt);
    }
}