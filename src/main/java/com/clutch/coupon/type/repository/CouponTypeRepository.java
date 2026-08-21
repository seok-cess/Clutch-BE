package com.clutch.coupon.type.repository;

import com.clutch.coupon.type.domain.CouponType;
import com.clutch.coupon.type.domain.CouponTypeStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 쿠폰 종류의 저장과 관리자 목록 조회를 담당하는 저장소.
 */
public interface CouponTypeRepository extends JpaRepository<CouponType, Long> {

    /**
     * 모든 쿠폰 종류를 최신순으로 조회한다.
     *
     * @return 쿠폰 종류 목록
     */
    Slice<CouponType> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 특정 상태의 쿠폰 종류를 최신순으로 조회한다.
     *
     * @param status 조회할 쿠폰 종류 상태
     * @return 상태가 일치하는 쿠폰 종류 목록
     */
    Slice<CouponType> findByStatusOrderByIdDesc(
            CouponTypeStatus status,
            Pageable pageable
    );

    /**
     * ID 커서보다 작은 쿠폰 종류를 최신순으로 조회한다.
     */
    Slice<CouponType> findByIdLessThanOrderByIdDesc(
            Long cursor,
            Pageable pageable
    );

    /**
     * 상태와 ID 커서를 적용해 쿠폰 종류를 최신순으로 조회한다.
     */
    Slice<CouponType> findByStatusAndIdLessThanOrderByIdDesc(
            CouponTypeStatus status,
            Long cursor,
            Pageable pageable
    );

    /**
     * 활성 상태이면서 이름에 검색어가 포함된 쿠폰 종류를 조회한다.
     */
    Slice<CouponType> findByStatusAndCouponNameContainingIgnoreCaseOrderByIdDesc(
            CouponTypeStatus status,
            String keyword,
            Pageable pageable
    );

    /**
     * 활성 상태, 이름 검색어와 ID 커서를 모두 적용해 조회한다.
     */
    Slice<CouponType>
            findByStatusAndCouponNameContainingIgnoreCaseAndIdLessThanOrderByIdDesc(
                    CouponTypeStatus status,
                    String keyword,
                    Long cursor,
                    Pageable pageable
            );
}
