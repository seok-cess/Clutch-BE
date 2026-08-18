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

import java.util.List;

/** 취소 이벤트의 등록 배팅을 잠그고 포인트 환불과 원장 기록을 원자적으로 처리한다. */
@Service
@RequiredArgsConstructor
public class BetRefundService {

    private final BettingEventRepository eventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /**
     * 취소 이벤트의 미처리 배팅을 환불하며 처리할 배팅이 없으면 멱등하게 종료한다.
     *
     * @param bettingEventId 환불할 배팅 이벤트 ID
     * @throws BettingException 이벤트가 없거나 취소 상태가 아니거나 사용자를 찾을 수 없을 때
     */
    @Transactional
    public void refund(Long bettingEventId) {
        BettingEvent event = eventRepository.findByIdForUpdate(bettingEventId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        validateCancelled(event);
        List<UserBet> placedBets = userBetRepository
                .findAllByBettingEventIdAndStatusForUpdate(
                        bettingEventId,
                        UserBetStatus.PLACED
                );
        for (UserBet userBet : placedBets) {
            refundBet(userBet);
        }
    }

    /**
     * 환불 대상 이벤트가 취소 상태인지 검증한다.
     *
     * @param event 환불할 배팅 이벤트
     * @throws BettingException 취소 상태가 아닌 경우
     */
    private void validateCancelled(BettingEvent event) {
        if (event.getStatus() != BettingEventStatus.CANCELLED) {
            throw new BettingException(BettingErrorCode.EVENT_NOT_CANCELLED);
        }
    }

    /**
     * 한 사용자 배팅의 포인트를 반환하고 환불 원장을 기록한다.
     *
     * @param userBet 환불할 사용자 배팅
     * @throws BettingException 사용자를 찾을 수 없는 경우
     */
    private void refundBet(UserBet userBet) {
        if (userRepository.increasePoint(userBet.getUserId(), userBet.getAmount()) != 1) {
            throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
        }
        userBet.refund();
        transactionRepository.save(
                BetPointTransaction.refund(userBet.getId(), userBet.getAmount())
        );
    }
}
