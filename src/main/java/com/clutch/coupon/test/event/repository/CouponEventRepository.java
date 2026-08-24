package com.clutch.coupon.test.event.repository;

import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.test.event.domain.CouponEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 수동 발급 테스트용 쿠폰 이벤트 저장소. */
@Repository("couponTestEventRepository")
public interface CouponEventRepository
        extends JpaRepository<CouponEvent, Long> {

    /** 동시 수동 오픈 요청을 직렬화하여 이벤트를 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from CouponTestEvent event where event.id = :id")
    Optional<CouponEvent> findByIdForUpdate(@Param("id") Long id);

    /**
     * 이 경기에서 이 트리거를 기다리는 READY 상태 이벤트.
     *
     * 경기 조건이 반드시 함께 걸려야 한다. 트리거만 보면 어느 경기의 펜타킬이든
     * 가장 오래된 PENTAKILL 이벤트를 열어버려, 전혀 다른 경기(혹은 샘플 재생)의
     * 사건으로 실제 이벤트가 열린다.
     *
     * 한 경기에 같은 트리거 이벤트는 uk_coupon_event_match_trigger 로 하나뿐이지만,
     * 방어적으로 가장 먼저 만든 것을 쓴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from CouponTestEvent event
            where event.esportsMatchId = :esportsMatchId
              and event.triggerType = :triggerType
              and event.eventStatus = :status
            order by event.id asc
            limit 1
            """)
    Optional<CouponEvent> findReadyByMatchAndTriggerForUpdate(
            @Param("esportsMatchId") Long esportsMatchId,
            @Param("triggerType") String triggerType,
            @Param("status") CouponEventStatus status
    );
}
