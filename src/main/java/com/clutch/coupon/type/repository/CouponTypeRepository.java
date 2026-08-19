package com.clutch.coupon.type.repository;

import com.clutch.coupon.type.domain.CouponType;
import com.clutch.coupon.type.domain.CouponTypeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 쿠폰 종류의 저장과 관리자 목록 조회를 담당하는 저장소.
 */
public interface CouponTypeRepository extends JpaRepository<CouponType, Long> {

    /**
     * 모든 쿠폰 종류를 최신순으로 조회한다.
     *
     * @return 쿠폰 종류 목록
     */
    List<CouponType> findAllByOrderByIdDesc();

    /**
     * 특정 상태의 쿠폰 종류를 최신순으로 조회한다.
     *
     * @param status 조회할 쿠폰 종류 상태
     * @return 상태가 일치하는 쿠폰 종류 목록
     */
    List<CouponType> findAllByStatusOrderByIdDesc(CouponTypeStatus status);
}
