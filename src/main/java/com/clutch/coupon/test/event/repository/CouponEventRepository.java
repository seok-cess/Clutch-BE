package com.clutch.coupon.test.event.repository;

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
}
