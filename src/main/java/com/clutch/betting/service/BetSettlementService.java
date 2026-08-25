package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.List;

/** 승자가 확정된 이벤트의 사용자 배팅과 포인트 지급을 한 트랜잭션으로 정산한다. */
@Service
@RequiredArgsConstructor
public class BetSettlementService {

    private static final long OPERATING_FEE_PERCENT = 10L;
    private static final long PERCENTAGE_BASE = 100L;

    private final BettingEventRepository eventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /**
     * 이벤트와 배팅을 잠근 뒤 총 풀의 10% 수수료를 제외한 금액을 적중 배팅금 비율로 지급한다.
     *
     * @param bettingEventId 정산할 배팅 이벤트 ID
     * @throws BettingException 이벤트가 없거나 결과가 준비되지 않았거나 사용자를 찾을 수 없을 때
     * @throws ArithmeticException 지급액 계산 중 long 범위를 넘을 때
     */
    @Transactional
    public void settle(Long bettingEventId) {
        BettingEvent event = eventRepository.findByIdForUpdate(bettingEventId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        if (event.getStatus() == BettingEventStatus.SETTLED) {
            return;
        }
        validateResultReady(event);

        List<UserBet> placedBets = userBetRepository
                .findAllByBettingEventIdAndStatusForUpdate(
                        bettingEventId,
                        UserBetStatus.PLACED
                );
        long totalPool = sumAmounts(placedBets);
        List<UserBet> winningBets = placedBets.stream()
                .filter(userBet -> userBet.getSelectedExternalTeamId()
                        .equals(event.getWinnerExternalTeamId()))
                .toList();

        placedBets.stream()
                .filter(userBet -> !userBet.getSelectedExternalTeamId()
                        .equals(event.getWinnerExternalTeamId()))
                .forEach(UserBet::lose);

        if (!winningBets.isEmpty()) {
            long distributablePool = totalPool - operatingFee(totalPool);
            long totalWinningStake = sumAmounts(winningBets);
            for (UserBet winningBet : winningBets) {
                payout(winningBet, proportionalPayout(
                        distributablePool,
                        winningBet.getAmount(),
                        totalWinningStake
                ));
            }
        }
        event.settle();
    }

    /**
     * 종료 상태와 승자 정보를 확인해 정산 가능한 이벤트인지 검증한다.
     *
     * @param event 정산할 배팅 이벤트
     * @throws BettingException 이벤트가 아직 종료되지 않았거나 승자가 없는 경우
     */
    private void validateResultReady(BettingEvent event) {
        if (event.getStatus() != BettingEventStatus.CLOSED
                || event.getWinnerExternalTeamId() == null) {
            throw new BettingException(BettingErrorCode.RESULT_NOT_READY);
        }
    }

    /** 모든 배팅금을 더해 해당 이벤트의 총 풀을 계산한다. */
    private long sumAmounts(List<UserBet> userBets) {
        long total = 0L;
        for (UserBet userBet : userBets) {
            total = Math.addExact(total, userBet.getAmount());
        }
        return total;
    }

    /** 총 풀의 10%를 소수점 버림해 운영 수수료를 계산한다. */
    private long operatingFee(long totalPool) {
        return totalPool / PERCENTAGE_BASE * OPERATING_FEE_PERCENT
                + totalPool % PERCENTAGE_BASE * OPERATING_FEE_PERCENT / PERCENTAGE_BASE;
    }

    /**
     * 적중 배팅금 비율로 배당 풀을 나누고 소수점 버림으로 생긴 잔여는 운영 수수료에 포함한다.
     * BigInteger를 사용해 총 풀과 배팅금의 곱셈 중 long 범위를 넘지 않게 한다.
     */
    private long proportionalPayout(long distributablePool, long amount, long totalWinningStake) {
        return BigInteger.valueOf(distributablePool)
                .multiply(BigInteger.valueOf(amount))
                .divide(BigInteger.valueOf(totalWinningStake))
                .longValueExact();
    }

    /** 적중 배팅 한 건에 계산된 포인트를 지급하고 원장을 기록한다. */
    private void payout(UserBet userBet, long payoutPoint) {
        increasePoint(userBet.getUserId(), payoutPoint);
        userBet.win();
        transactionRepository.save(BetPointTransaction.payout(userBet.getId(), payoutPoint));
    }

    /**
     * 사용자 포인트 지급 결과가 한 건이 아니면 사용자 없음 오류로 처리한다.
     *
     * @param userId 사용자 ID
     * @param payoutPoint 지급할 포인트
     * @throws BettingException 사용자를 찾을 수 없을 때
     */
    private void increasePoint(Long userId, long payoutPoint) {
        if (userRepository.increasePoint(userId, payoutPoint) != 1) {
            throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
        }
    }
}
