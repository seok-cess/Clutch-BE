package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEventPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 시간 차등 쿠폰 발급 단계의 저장과 활성 단계 조회를 담당하는 저장소.
 */
public interface CouponEventPhaseRepository
        extends JpaRepository<CouponEventPhase, Long> {

    /**
     * 이벤트의 단계를 시작 시간 오름차순으로 조회한다.
     *
     * @param couponEventId 쿠폰 이벤트 ID
     * @return 시간순으로 정렬된 발급 단계 목록
     */
    List<CouponEventPhase> findAllByCouponEventIdOrderByOpenOffsetSecondsAsc(
            Long couponEventId
    );

    /**
     * 이벤트 오픈 후 경과 시간에 해당하는 가장 최근 단계를 조회한다.
     *
     * <p>다음 단계가 열리면 이전 단계 대신 이 쿼리로 조회된 한 단계만
     * 발급 대상이 된다.</p>
     *
     * @param couponEventId 쿠폰 이벤트 ID
     * @param elapsedSeconds 이벤트 오픈 후 경과 시간(초)
     * @return 현재 활성 단계, 아직 첫 단계가 열리지 않았다면 빈 값
     */
    Optional<CouponEventPhase>
    findFirstByCouponEventIdAndOpenOffsetSecondsLessThanEqualOrderByOpenOffsetSecondsDesc(
            Long couponEventId,
            int elapsedSeconds
    );

    /**
     * 이벤트에 속한 모든 발급 단계를 물리 삭제한다.
     *
     * @param couponEventId 삭제할 쿠폰 이벤트 ID
     */
    void deleteAllByCouponEventId(Long couponEventId);
}
