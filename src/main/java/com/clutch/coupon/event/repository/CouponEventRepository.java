package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * CLUTCH-216: 지정한 수동 테스트 트리거 접두사의 마지막 순번을 조회한다.
     *
     * @param triggerPrefix 날짜까지 포함한 수동 테스트 트리거 접두사
     * @return 현재 저장된 가장 큰 수동 테스트 순번, 없으면 0
     */
    @Query(
            value = """
                    SELECT COALESCE(
                        MAX(CAST(SUBSTRING_INDEX(trigger_type, '_', -1)
                            AS UNSIGNED)),
                        0
                    )
                    FROM coupon_event
                    WHERE trigger_type LIKE CONCAT(:triggerPrefix, '%')
                      AND trigger_type REGEXP CONCAT(
                          '^', :triggerPrefix, '[0-9]+$'
                      )
                    """,
            nativeQuery = true
    )
    int findMaxManualTestSequence(
            @Param("triggerPrefix") String triggerPrefix
    );

    /**
     * 전체 이벤트를 ID 내림차순으로 조회한다.
     *
     * @param pageable 조회 크기 정보
     * @return 전체 건수와 페이지 정보를 포함한 이벤트 목록
     */
    Page<CouponEvent> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 특정 상태의 이벤트를 ID 내림차순으로 조회한다.
     *
     * @param eventStatus 조회할 이벤트 상태
     * @param pageable 조회 크기 정보
     * @return 전체 건수와 페이지 정보를 포함한 상태별 이벤트 목록
     */
    Page<CouponEvent> findByEventStatusOrderByIdDesc(
            CouponEventStatus eventStatus,
            Pageable pageable
    );
}
