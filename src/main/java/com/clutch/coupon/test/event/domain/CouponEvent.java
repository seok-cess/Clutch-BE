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

    /**
     * 이 이벤트가 걸린 경기.
     *
     * 트리거로 이벤트를 찾을 때 반드시 함께 본다. 경기를 보지 않으면
     * 아무 경기의 펜타킬이나 샘플 재생이 다른 경기 이벤트를 열어버린다.
     */
    @Column(name = "esports_match_id", nullable = false)
    private Long esportsMatchId;

    @Column(name = "event_name", nullable = false, length = 200)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    private CouponEventStatus eventStatus;

    /**
     * 이 이벤트를 여는 경기 트리거.
     *
     * 기존 데이터에 자유 문자열이 들어 있어 enum 매핑 대신 문자열로 읽고
     * {@link CouponEventTrigger#from} 으로 해석한다. 알 수 없는 값 때문에
     * 조회 자체가 실패하면 운영이 막힌다.
     */
    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

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
