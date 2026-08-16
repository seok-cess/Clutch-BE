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

/** 승자가 확정된 이벤트의 사용자 배팅과 포인트 지급을 한 트랜잭션으로 정산한다. */
@Service
@RequiredArgsConstructor
public class BetSettlementService {

    private static final long PAYOUT_MULTIPLIER = 2L;

    private final BettingEventRepository eventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /**
     * 이벤트와 배팅을 잠근 뒤 적중에는 2배 지급, 실패에는 몰수 결과를 기록한다.
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
        for (UserBet userBet : placedBets) {
            settleBet(userBet, event.getWinnerExternalTeamId());
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

    /**
     * 한 사용자 배팅을 승패에 따라 지급 또는 몰수 처리한다.
     *
     * @param userBet 처리할 사용자 배팅
     * @param winnerExternalTeamId 확정된 승리 팀 ID
     */
    private void settleBet(UserBet userBet, String winnerExternalTeamId) {
        if (!userBet.getSelectedExternalTeamId().equals(winnerExternalTeamId)) {
            userBet.lose();
            return;
        }

        long payoutPoint = Math.multiplyExact(userBet.getAmount(), PAYOUT_MULTIPLIER);
        increasePoint(userBet.getUserId(), payoutPoint);
        userBet.win();
        transactionRepository.save(
                BetPointTransaction.payout(userBet.getId(), payoutPoint)
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
