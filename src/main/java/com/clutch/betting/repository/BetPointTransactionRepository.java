package com.clutch.betting.repository;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BetPointTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 배팅 포인트 거래의 멱등성 확인과 원장 조회를 담당한다. */
public interface BetPointTransactionRepository extends JpaRepository<BetPointTransaction, Long> {

    /**
     * 사용자 배팅에 동일 유형 거래가 이미 기록됐는지 확인한다.
     *
     * @param userBetId 사용자 배팅 ID
     * @param transactionType 포인트 거래 유형
     * @return 동일 거래가 존재하면 true
     */
    boolean existsByUserBetIdAndTransactionType(
            Long userBetId,
            BetPointTransactionType transactionType
    );

    /**
     * 사용자 배팅과 거래 유형으로 포인트 원장 한 건을 조회한다.
     *
     * @param userBetId 사용자 배팅 ID
     * @param transactionType 포인트 거래 유형
     * @return 조건에 맞는 포인트 거래
     */
    Optional<BetPointTransaction> findByUserBetIdAndTransactionType(
            Long userBetId,
            BetPointTransactionType transactionType
    );

    /**
     * 여러 사용자 배팅의 포인트 원장을 한 번에 조회한다.
     *
     * @param userBetIds 조회할 사용자 배팅 식별자 목록
     * @return 해당 배팅의 포인트 원장 목록
     */
    List<BetPointTransaction> findAllByUserBetIdIn(Collection<Long> userBetIds);

}
