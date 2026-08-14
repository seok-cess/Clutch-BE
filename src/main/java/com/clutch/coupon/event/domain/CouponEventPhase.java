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

@Getter
@Entity
@Table(name = "coupon_event_phase")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEventPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_event_phase_id", nullable = false)
    private Long id;

    @Column(name = "coupon_event_id", nullable = false)
    private Long couponEventId;

    @Column(name = "coupon_event_item_id", nullable = false)
    private Long couponEventItemId;

    @Column(name = "phase_sequence", nullable = false)
    private int phaseSequence;

    @Column(name = "open_offset_seconds", nullable = false)
    private int openOffsetSeconds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private CouponEventPhase(
            Long couponEventId,
            Long couponEventItemId,
            int phaseSequence,
            int openOffsetSeconds
    ) {
        if (couponEventId == null || couponEventId <= 0) {
            throw new IllegalArgumentException("쿠폰 이벤트 ID는 필수입니다.");
        }
        if (couponEventItemId == null || couponEventItemId <= 0) {
            throw new IllegalArgumentException("쿠폰 이벤트 항목 ID는 필수입니다.");
        }
        if (phaseSequence <= 0) {
            throw new IllegalArgumentException("단계 순서는 1 이상이어야 합니다.");
        }
        if (openOffsetSeconds < 0) {
            throw new IllegalArgumentException("단계 오픈 시간은 0초 이상이어야 합니다.");
        }

        this.couponEventId = couponEventId;
        this.couponEventItemId = couponEventItemId;
        this.phaseSequence = phaseSequence;
        this.openOffsetSeconds = openOffsetSeconds;
    }

    public static CouponEventPhase create(
            Long couponEventId,
            Long couponEventItemId,
            int phaseSequence,
            int openOffsetSeconds
    ) {
        return new CouponEventPhase(
                couponEventId,
                couponEventItemId,
                phaseSequence,
                openOffsetSeconds
        );
    }
}

