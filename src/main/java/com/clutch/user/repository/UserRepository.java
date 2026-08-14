package com.clutch.user.repository;

import com.clutch.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 사용자 조회와 배팅 포인트의 원자적 증감을 제공한다. */
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update User user
            set user.point = user.point - :amount
            where user.id = :userId
              and user.point >= :amount
            """)
    /** 잔액이 충분할 때만 포인트를 차감해 동시 배팅의 초과 사용을 막는다. */
    int decreasePointIfEnough(
            @Param("userId") Long userId,
            @Param("amount") long amount
    );

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update User user
            set user.point = user.point + :amount
            where user.id = :userId
            """)
    /** 정산 또는 환불 포인트를 데이터베이스에서 직접 증가시킨다. */
    int increasePoint(
            @Param("userId") Long userId,
            @Param("amount") long amount
    );

    @Query("select user.point from User user where user.id = :userId")
    /** 영속성 컨텍스트의 오래된 엔티티 대신 최신 포인트 값만 조회한다. */
    Optional<Long> findPointById(@Param("userId") Long userId);
}
