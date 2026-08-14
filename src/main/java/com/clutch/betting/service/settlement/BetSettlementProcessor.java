package com.clutch.betting.service.settlement;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 승자가 확정된 이벤트의 사용자 배팅과 포인트 지급을 한 트랜잭션으로 정산한다. */
@Service
public class BetSettlementProcessor {

    private static final long PAYOUT_MULTIPLIER = 2L;

    private final BettingEventRepository eventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /**
     * 정산에 필요한 이벤트·배팅·원장·사용자 저장소를 주입받는다.
     *
     * @param eventRepository 배팅 이벤트 저장소
     * @param userBetRepository 사용자 배팅 저장소
     * @param transactionRepository 배팅 포인트 거래 저장소
     * @param userRepository 사용자 저장소
     */
    public BetSettlementProcessor(
            BettingEventRepository eventRepository,
            UserBetRepository userBetRepository,
            BetPointTransactionRepository transactionRepository,
            UserRepository userRepository
    ) {
        this.eventRepository = eventRepository;
        this.userBetRepository = userBetRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    /**
     * 이벤트와 배팅을 잠근 뒤 적중에는 2배 지급, 실패에는 몰수 결과를 기록한다.
     *
     * @param bettingEventId 정산할 배팅 이벤트 ID
     * @return 적중·실패 건수와 총 지급 포인트
     * @throws BettingException 이벤트가 없거나 결과가 준비되지 않았거나 사용자를 찾을 수 없을 때
     * @throws ArithmeticException 지급액 계산 또는 합산 중 long 범위를 넘을 때
     */
    @Transactional
    public BetSettlementResult settle(Long bettingEventId) {
        BettingEvent event = eventRepository.findByIdForUpdate(bettingEventId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        if (event.getStatus() == BettingEventStatus.SETTLED) {
            return BetSettlementResult.alreadyProcessed(bettingEventId);
        }
        if (event.getStatus() != BettingEventStatus.CLOSED
                || event.getWinnerExternalTeamId() == null) {
            throw new BettingException(BettingErrorCode.RESULT_NOT_READY);
        }

        List<UserBet> placedBets = userBetRepository
                .findAllByBettingEventIdAndStatusForUpdate(
                        bettingEventId,
                        UserBetStatus.PLACED
                );
        int wonCount = 0;
        int lostCount = 0;
        long totalPayoutPoint = 0L;
        for (UserBet userBet : placedBets) {
            if (userBet.getSelectedExternalTeamId().equals(event.getWinnerExternalTeamId())) {
                long payoutPoint = Math.multiplyExact(userBet.getAmount(), PAYOUT_MULTIPLIER);
                increasePoint(userBet.getUserId(), payoutPoint);
                userBet.win();
                transactionRepository.save(
                        BetPointTransaction.payout(userBet.getId(), payoutPoint)
                );
                wonCount++;
                totalPayoutPoint = Math.addExact(totalPayoutPoint, payoutPoint);
            } else {
                userBet.lose();
                lostCount++;
            }
        }
        event.settle();
        return new BetSettlementResult(
                bettingEventId,
                wonCount,
                lostCount,
                totalPayoutPoint,
                false
        );
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
