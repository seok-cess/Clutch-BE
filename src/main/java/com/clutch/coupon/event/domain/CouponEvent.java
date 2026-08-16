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
 * 관리자가 설정한 경기 트리거 기반 쿠폰 이벤트.
 *
 * <p>어떤 경기의 어떤 트리거에서 이벤트를 열지와 발급 방식,
 * 신청 가능 시간을 관리하는 애그리거트 루트이다.</p>
 */
@Getter
@Entity
@Table(name = "coupon_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_event_id", nullable = false)
    private Long id;

    @Column(name = "esports_match_id", nullable = false)
    private Long esportsMatchId;

    @Column(name = "event_name", nullable = false, length = 200)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_mode", nullable = false, length = 30)
    private CouponIssueMode issueMode;

    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    private CouponEventStatus eventStatus;

    @Column(name = "claim_window_seconds", nullable = false)
    private int claimWindowSeconds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private CouponEvent(
            Long esportsMatchId,
            String eventName,
            CouponIssueMode issueMode,
            String triggerType,
            int claimWindowSeconds
    ) {
        this.esportsMatchId = esportsMatchId;
        this.eventName = eventName;
        this.issueMode = issueMode;
        this.triggerType = triggerType;
        this.eventStatus = CouponEventStatus.READY;
        this.claimWindowSeconds = claimWindowSeconds;
    }

    /**
     * 유효한 설정으로 대기 상태의 쿠폰 이벤트를 생성한다.
     *
     * @param esportsMatchId 이벤트를 적용할 경기 ID
     * @param eventName 이벤트 이름
     * @param issueMode 쿠폰 발급 방식
     * @param triggerType 이벤트 시작 조건인 경기 트리거 종류
     * @param claimWindowSeconds 쿠폰 신청 가능 시간(초)
     * @return 생성된 쿠폰 이벤트
     * @throws IllegalArgumentException 필수 설정이 없거나 값의 범위가 잘못된 경우
     */
    public static CouponEvent create(
            Long esportsMatchId,
            String eventName,
            CouponIssueMode issueMode,
            String triggerType,
            int claimWindowSeconds
    ) {
        validateConfiguration(
                esportsMatchId,
                eventName,
                issueMode,
                triggerType,
                claimWindowSeconds
        );
        return new CouponEvent(
                esportsMatchId,
                eventName,
                issueMode,
                triggerType,
                claimWindowSeconds
        );
    }

    /**
     * 이벤트의 경기, 트리거, 발급 방식 및 신청 시간을 변경한다.
     *
     * <p>이 메서드는 설정 값만 변경한다. 이벤트 상태에 따른 수정 가능 여부는
     * 애플리케이션 서비스에서 확인한다.</p>
     *
     * @param esportsMatchId 변경할 경기 ID
     * @param eventName 변경할 이벤트 이름
     * @param issueMode 변경할 쿠폰 발급 방식
     * @param triggerType 변경할 경기 트리거 종류
     * @param claimWindowSeconds 변경할 쿠폰 신청 가능 시간(초)
     * @throws IllegalArgumentException 필수 설정이 없거나 값의 범위가 잘못된 경우
     */
    public void updateConfiguration(
            Long esportsMatchId,
            String eventName,
            CouponIssueMode issueMode,
            String triggerType,
            int claimWindowSeconds
    ) {
        validateConfiguration(
                esportsMatchId,
                eventName,
                issueMode,
                triggerType,
                claimWindowSeconds
        );
        this.esportsMatchId = esportsMatchId;
        this.eventName = eventName;
        this.issueMode = issueMode;
        this.triggerType = triggerType;
        this.claimWindowSeconds = claimWindowSeconds;
    }

    private static void validateConfiguration(
            Long esportsMatchId,
            String eventName,
            CouponIssueMode issueMode,
            String triggerType,
            int claimWindowSeconds
    ) {
        if (esportsMatchId == null || esportsMatchId <= 0) {
            throw new IllegalArgumentException("경기 ID는 필수입니다.");
        }
        if (eventName == null || eventName.isBlank()
                || eventName.length() > 200) {
            throw new IllegalArgumentException(
                    "이벤트 이름은 1자 이상 200자 이하여야 합니다."
            );
        }
        if (issueMode == null) {
            throw new IllegalArgumentException("발급 방식은 필수입니다.");
        }
        if (triggerType == null || triggerType.isBlank()) {
            throw new IllegalArgumentException("트리거 종류는 필수입니다.");
        }
        if (claimWindowSeconds <= 0) {
            throw new IllegalArgumentException(
                    "신청 가능 시간은 1초 이상이어야 합니다."
            );
        }
    }
}
