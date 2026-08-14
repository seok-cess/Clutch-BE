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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BetSettlementProcessor {

    private static final long PAYOUT_MULTIPLIER = 2L;

    private final BettingEventRepository eventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;

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

    private void increasePoint(Long userId, long payoutPoint) {
        if (userRepository.increasePoint(userId, payoutPoint) != 1) {
            throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
        }
    }
}
