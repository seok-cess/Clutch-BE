package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 쿠폰 이벤트의 저장과 관리자 목록 조회를 담당하는 저장소.
 */
public interface CouponEventRepository
        extends JpaRepository<CouponEvent, Long> {

    /**
     * 같은 경기와 트리거로 등록된 이벤트가 있는지 확인한다.
     *
     * @param esportsMatchId 경기 ID
     * @param triggerType 경기 트리거 종류
     * @return 중복 이벤트가 존재하면 {@code true}
     */
    boolean existsByEsportsMatchIdAndTriggerType(
            Long esportsMatchId,
            String triggerType
    );

    /**
     * 수정 대상 자신을 제외하고 같은 경기와 트리거의 이벤트가 있는지 확인한다.
     *
     * @param esportsMatchId 경기 ID
     * @param triggerType 경기 트리거 종류
     * @param couponEventId 중복 검사에서 제외할 이벤트 ID
     * @return 다른 중복 이벤트가 존재하면 {@code true}
     */
    boolean existsByEsportsMatchIdAndTriggerTypeAndIdNot(
            Long esportsMatchId,
            String triggerType,
            Long couponEventId
    );

    /**
     * 전체 이벤트를 ID 내림차순으로 조회한다.
     *
     * @param pageable 조회 크기 정보
     * @return 현재 커서 페이지의 이벤트 목록
     */
    Slice<CouponEvent> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 커서 ID보다 작은 이벤트를 ID 내림차순으로 조회한다.
     *
     * @param cursor 이전 페이지의 마지막 이벤트 ID
     * @param pageable 조회 크기 정보
     * @return 다음 커서 페이지의 이벤트 목록
     */
    Slice<CouponEvent> findByIdLessThanOrderByIdDesc(
            Long cursor,
            Pageable pageable
    );

    /**
     * 특정 상태의 이벤트를 ID 내림차순으로 조회한다.
     *
     * @param eventStatus 조회할 이벤트 상태
     * @param pageable 조회 크기 정보
     * @return 현재 커서 페이지의 이벤트 목록
     */
    Slice<CouponEvent> findByEventStatusOrderByIdDesc(
            CouponEventStatus eventStatus,
            Pageable pageable
    );

    /**
     * 특정 상태이면서 커서 ID보다 작은 이벤트를 조회한다.
     *
     * @param eventStatus 조회할 이벤트 상태
     * @param cursor 이전 페이지의 마지막 이벤트 ID
     * @param pageable 조회 크기 정보
     * @return 다음 커서 페이지의 이벤트 목록
     */
    Slice<CouponEvent> findByEventStatusAndIdLessThanOrderByIdDesc(
            CouponEventStatus eventStatus,
            Long cursor,
            Pageable pageable
    );
}
