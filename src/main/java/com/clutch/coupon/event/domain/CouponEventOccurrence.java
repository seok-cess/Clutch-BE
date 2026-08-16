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
 * 경기 트리거가 감지되어 실제로 열린 쿠폰 이벤트의 발생 회차.
 *
 * <p>외부 경기 이벤트의 식별 정보와 감지·오픈·만료 시각을 보관하여
 * 중복 트리거를 방지하고 현재 신청 가능 여부를 판단한다.</p>
 */
@Getter
@Entity
@Table(name = "coupon_event_occurrence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEventOccurrence {

    /**
     * 쿠폰 이벤트 회차 식별자
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_event_occurrence_id", nullable = false)
    private Long id;

    /**
     * 쿠폰 이벤트 식별자
     */
    @Column(name = "coupon_event_id", nullable = false)
    private Long couponEventId;

    /**
     * 경기 이벤트 식별자
     */
    @Column(name = "match_event_id")
    private Long matchEventId;

    /**
     * 원본 이벤트 키
     */
    @Column(name = "source_event_key", length = 100)
    private String sourceEventKey;

    /**
     * 경기 진행 시간
     */
    @Column(name = "game_time_seconds")
    private Integer gameTimeSeconds;

    /**
     * 원본 이벤트 발생 시각
     */
    @Column(name = "source_occurred_at")
    private LocalDateTime sourceOccurredAt;

    /**
     * 이벤트 감지 시각
     */
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    /**
     * 발급 요청 시작 시각
     */
    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    /**
     * 발급 요청 만료 시각
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 실제 종료 시각
     */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * 쿠폰 이벤트 회차 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "occurrence_status", nullable = false, length = 20)
    private CouponEventOccurrenceStatus occurrenceStatus;

    /**
     * 종료 사유
     */
    @Column(name = "close_reason", length = 50)
    private String closeReason;

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
     * 주어진 시각에 이 발생 회차가 쿠폰 신청을 받을 수 있는지 확인한다.
     *
     * @param currentTime 신청 가능 여부를 판단할 현재 시각
     * @return 상태가 열림이고 오픈 시각 이상, 만료 시각 미만이면 {@code true}
     */
    public boolean isOpenAt(LocalDateTime currentTime) {
        return occurrenceStatus == CouponEventOccurrenceStatus.OPEN
                && closedAt == null
                && !currentTime.isBefore(openedAt)
                && currentTime.isBefore(expiresAt);
    }
}
