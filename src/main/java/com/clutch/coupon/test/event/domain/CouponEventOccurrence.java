package com.clutch.coupon.test.event.domain;

import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
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

import java.time.LocalDateTime;
import java.util.UUID;

/** 수동 발급 테스트로 생성된 쿠폰 이벤트 회차. */
@Getter
@Entity(name = "CouponTestEventOccurrence")
@Table(name = "coupon_event_occurrence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEventOccurrence {

    private static final String MANUAL_SOURCE_PREFIX = "MANUAL:";
    private static final String EXPIRED_CLOSE_REASON = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_event_occurrence_id", nullable = false)
    private Long id;

    @Column(name = "coupon_event_id", nullable = false)
    private Long couponEventId;

    @Column(name = "source_event_key", length = 100)
    private String sourceEventKey;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "occurrence_status", nullable = false, length = 20)
    private CouponEventOccurrenceStatus occurrenceStatus;

    @Column(name = "close_reason", length = 50)
    private String closeReason;

    private CouponEventOccurrence(
            Long couponEventId,
            String sourceEventKey,
            LocalDateTime openedAt,
            LocalDateTime expiresAt
    ) {
        this.couponEventId = couponEventId;
        this.sourceEventKey = sourceEventKey;
        this.detectedAt = openedAt;
        this.openedAt = openedAt;
        this.expiresAt = expiresAt;
        this.occurrenceStatus = CouponEventOccurrenceStatus.OPEN;
    }

    /** 경기 트리거 없이 테스트용 쿠폰 이벤트 회차를 생성한다. */
    public static CouponEventOccurrence manualOpen(
            Long couponEventId,
            LocalDateTime openedAt,
            int claimWindowSeconds
    ) {
        if (couponEventId == null || couponEventId <= 0) {
            throw new IllegalArgumentException(
                    "쿠폰 이벤트 ID는 필수입니다."
            );
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("오픈 시각은 필수입니다.");
        }
        if (claimWindowSeconds <= 0) {
            throw new IllegalArgumentException(
                    "신청 가능 시간은 1초 이상이어야 합니다."
            );
        }

        return new CouponEventOccurrence(
                couponEventId,
                MANUAL_SOURCE_PREFIX + UUID.randomUUID(),
                openedAt,
                openedAt.plusSeconds(claimWindowSeconds)
        );
    }

    /** 만료된 테스트 회차를 종료한다. */
    public boolean closeIfExpired(LocalDateTime currentTime) {
        if (currentTime == null
                || occurrenceStatus != CouponEventOccurrenceStatus.OPEN
                || currentTime.isBefore(expiresAt)) {
            return false;
        }
        occurrenceStatus = CouponEventOccurrenceStatus.CLOSED;
        closedAt = currentTime;
        closeReason = EXPIRED_CLOSE_REASON;
        return true;
    }
}
