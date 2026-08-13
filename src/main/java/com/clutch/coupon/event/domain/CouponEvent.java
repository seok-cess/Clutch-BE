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
    @Column(name = "esports_match_id")
    private Long esportsMatchId;

    /**
     * 쿠폰 이벤트 이름
     */
    @Column(name = "event_name", nullable = false, length = 200)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(name = "open_mode", nullable = false, length = 30)
    private CouponEventOpenMode openMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_mode", nullable = false, length = 30)
    private CouponIssueMode issueMode;

    /**
     * 쿠폰 이벤트 발동 조건
     */
    @Column(name = "trigger_type", length = 50)
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

    @Column(name = "scheduled_open_at")
    private LocalDateTime scheduledOpenAt;

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

    private CouponEvent(
            Long esportsMatchId,
            String eventName,
            CouponEventOpenMode openMode,
            CouponIssueMode issueMode,
            String triggerType,
            int claimWindowSeconds,
            LocalDateTime scheduledOpenAt
    ) {
        this.esportsMatchId = esportsMatchId;
        this.eventName = eventName;
        this.openMode = openMode;
        this.issueMode = issueMode;
        this.triggerType = triggerType;
        this.eventStatus = CouponEventStatus.READY;
        this.claimWindowSeconds = claimWindowSeconds;
        this.scheduledOpenAt = scheduledOpenAt;
    }

    public static CouponEvent create(
            Long esportsMatchId,
            String eventName,
            CouponEventOpenMode openMode,
            CouponIssueMode issueMode,
            String triggerType,
            int claimWindowSeconds,
            LocalDateTime scheduledOpenAt
    ) {
        validateConfiguration(
                esportsMatchId,
                eventName,
                openMode,
                issueMode,
                triggerType,
                claimWindowSeconds,
                scheduledOpenAt
        );
        return new CouponEvent(
                esportsMatchId,
                eventName,
                openMode,
                issueMode,
                triggerType,
                claimWindowSeconds,
                scheduledOpenAt
        );
    }

    private static void validateConfiguration(
            Long esportsMatchId,
            String eventName,
            CouponEventOpenMode openMode,
            CouponIssueMode issueMode,
            String triggerType,
            int claimWindowSeconds,
            LocalDateTime scheduledOpenAt
    ) {
        if (eventName == null || eventName.isBlank() || eventName.length() > 200) {
            throw new IllegalArgumentException("이벤트 이름은 1자 이상 200자 이하여야 합니다.");
        }
        if (openMode == null || issueMode == null) {
            throw new IllegalArgumentException("오픈 방식과 발급 방식은 필수입니다.");
        }
        if (claimWindowSeconds <= 0) {
            throw new IllegalArgumentException("신청 가능 시간은 1초 이상이어야 합니다.");
        }
        if (openMode == CouponEventOpenMode.SCHEDULED) {
            if (scheduledOpenAt == null) {
                throw new IllegalArgumentException("예약 선착순 이벤트에는 오픈 시각이 필요합니다.");
            }
            if (issueMode != CouponIssueMode.SINGLE_FIRST_COME) {
                throw new IllegalArgumentException("예약 이벤트는 일반 선착순 방식만 지원합니다.");
            }
            return;
        }
        if (esportsMatchId == null || esportsMatchId <= 0) {
            throw new IllegalArgumentException("경기 트리거 이벤트에는 경기 ID가 필요합니다.");
        }
        if (triggerType == null || triggerType.isBlank()) {
            throw new IllegalArgumentException("경기 트리거 이벤트에는 트리거 종류가 필요합니다.");
        }
        if (scheduledOpenAt != null) {
            throw new IllegalArgumentException("경기 트리거 이벤트에는 예약 시각을 설정할 수 없습니다.");
        }
        if (issueMode != CouponIssueMode.PHASED_FIRST_COME) {
            throw new IllegalArgumentException("경기 트리거 이벤트는 단계별 선착순 방식이어야 합니다.");
        }
    }

}
