package com.clutch.user.repository;

import com.clutch.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 저장소.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update User user
            set user.point = user.point - :amount
            where user.id = :userId
              and user.point >= :amount
            """)
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
    int increasePoint(
            @Param("userId") Long userId,
            @Param("amount") long amount
    );
}
