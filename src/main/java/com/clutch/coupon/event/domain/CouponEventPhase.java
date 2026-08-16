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
 * 차등 혜택 발급 이벤트의 쿠폰 단계.
 *
 * <p>이벤트 오픈 후 경과 시간에 따라 어떤 쿠폰 항목을 발급할지 결정한다.
 * 다음 단계가 시작되면 이전 단계의 잔여 쿠폰은 더 이상 발급하지 않는다.</p>
 */
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

    /**
     * 쿠폰 이벤트 항목에 대응하는 발급 단계를 생성한다.
     *
     * @param couponEventId 소속 쿠폰 이벤트 ID
     * @param couponEventItemId 단계에서 발급할 쿠폰 이벤트 항목 ID
     * @param phaseSequence 단계 실행 순서
     * @param openOffsetSeconds 이벤트 오픈 시점부터 단계 시작까지의 시간(초)
     * @return 생성된 쿠폰 발급 단계
     * @throws IllegalArgumentException ID, 단계 순서 또는 시작 시간이 유효하지 않은 경우
     */
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
