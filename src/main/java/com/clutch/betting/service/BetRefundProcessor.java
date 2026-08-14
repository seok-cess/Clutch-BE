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
public class BetRefundProcessor {

    private final BettingEventRepository eventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public BetRefundProcessor(
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
    public BetRefundResult refund(Long bettingEventId) {
        BettingEvent event = eventRepository.findByIdForUpdate(bettingEventId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        if (event.getStatus() != BettingEventStatus.CANCELLED) {
            throw new BettingException(BettingErrorCode.EVENT_NOT_CANCELLED);
        }
        List<UserBet> placedBets = userBetRepository
                .findAllByBettingEventIdAndStatusForUpdate(
                        bettingEventId,
                        UserBetStatus.PLACED
                );
        if (placedBets.isEmpty()) {
            return BetRefundResult.alreadyProcessed(bettingEventId);
        }

        long totalRefundPoint = 0L;
        for (UserBet userBet : placedBets) {
            if (userRepository.increasePoint(userBet.getUserId(), userBet.getAmount()) != 1) {
                throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
            }
            userBet.refund();
            transactionRepository.save(
                    BetPointTransaction.refund(userBet.getId(), userBet.getAmount())
            );
            totalRefundPoint = Math.addExact(totalRefundPoint, userBet.getAmount());
        }
        return new BetRefundResult(
                bettingEventId,
                placedBets.size(),
                totalRefundPoint,
                false
        );
    }
}
