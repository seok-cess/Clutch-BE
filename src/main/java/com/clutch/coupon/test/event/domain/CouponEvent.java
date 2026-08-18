package com.clutch.coupon.test.event.domain;

import com.clutch.coupon.event.domain.CouponEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수동 발급 테스트에서 쿠폰 이벤트 상태만 다루는 전용 엔티티.
 */
@Getter
@Entity(name = "CouponTestEvent")
@Table(name = "coupon_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEvent {

    @Id
    @Column(name = "coupon_event_id", nullable = false)
    private Long id;

    @Column(name = "event_name", nullable = false, length = 200)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    private CouponEventStatus eventStatus;

    @Column(name = "claim_window_seconds", nullable = false)
    private int claimWindowSeconds;

    /** 대기 중인 이벤트를 테스트 발급 가능 상태로 전환한다. */
    public void open() {
        if (eventStatus != CouponEventStatus.READY) {
            throw new IllegalStateException(
                    "대기 상태의 쿠폰 이벤트만 오픈할 수 있습니다."
            );
        }
        eventStatus = CouponEventStatus.OPEN;
    }

    /** 테스트 발급 중인 이벤트를 종료한다. */
    public void close() {
        if (eventStatus == CouponEventStatus.OPEN) {
            eventStatus = CouponEventStatus.CLOSED;
        }
    }
}
