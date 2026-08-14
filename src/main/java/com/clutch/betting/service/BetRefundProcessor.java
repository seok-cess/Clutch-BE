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
/** 취소 이벤트의 등록 배팅을 잠그고 포인트 환불과 원장 기록을 원자적으로 처리한다. */
public class BetRefundProcessor {

    private final BettingEventRepository eventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /** 환불에 필요한 이벤트·배팅·원장·사용자 저장소를 주입받는다. */
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
    /** 취소 이벤트의 미처리 배팅을 환불하며 반복 호출은 처리 완료 결과로 응답한다. */
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
