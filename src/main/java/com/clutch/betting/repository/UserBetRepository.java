package com.clutch.betting.repository;

import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 사용자 배팅 조회와 정산 시 행 잠금을 제공한다. */
public interface UserBetRepository extends JpaRepository<UserBet, Long> {

    /**
     * 이벤트에 등록한 특정 사용자의 배팅을 조회한다.
     *
     * @param bettingEventId 배팅 이벤트 ID
     * @param userId 사용자 ID
     * @return 사용자가 등록한 배팅
     */
    Optional<UserBet> findByBettingEventIdAndUserId(Long bettingEventId, Long userId);

    /**
     * 동일 이벤트에 사용자가 이미 배팅했는지 확인한다.
     *
     * @param bettingEventId 배팅 이벤트 ID
     * @param userId 사용자 ID
     * @return 기존 배팅이 존재하면 true
     */
    boolean existsByBettingEventIdAndUserId(Long bettingEventId, Long userId);

    /** 사용자가 등록한 전체 승부예측 횟수를 조회한다. */
    long countByUserId(Long userId);

    /** 사용자가 적중한 승부예측 횟수를 조회한다. */
    long countByUserIdAndStatus(Long userId, UserBetStatus status);

    /**
     * 사용자의 전체 배팅을 최신 등록 순서로 조회한다.
     *
     * @param userId 사용자 ID
     * @return 최신 배팅이 먼저 정렬된 사용자 배팅 목록
     */
    List<UserBet> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /**
     * 여러 이벤트에 참여한 전체 배팅을 조회한다.
     *
     * <p>진행 중인 내 배팅의 예상 풀 배당을 계산할 때 사용한다.</p>
     *
     * @param bettingEventIds 조회할 배팅 이벤트 식별자 목록
     * @return 이벤트별 전체 사용자 배팅 목록
     */
    List<UserBet> findAllByBettingEventIdIn(Collection<Long> bettingEventIds);

    /**
     * 정산·환불 교착을 줄이도록 사용자와 ID 순서로 배팅 행을 잠가 조회한다.
     *
     * @param bettingEventId 배팅 이벤트 ID
     * @param status 사용자 배팅 상태
     * @return 쓰기 잠금이 적용된 사용자 배팅 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bet
            from UserBet bet
            where bet.bettingEventId = :bettingEventId
              and bet.status = :status
            order by bet.userId, bet.id
            """)
    List<UserBet> findAllByBettingEventIdAndStatusForUpdate(
            Long bettingEventId,
            UserBetStatus status
    );
}
