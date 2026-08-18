package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.repository.BettingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 자동 판정이 불가능한 종료 이벤트에 검증된 승자를 기록하고 즉시 정산한다. */
@Service
@RequiredArgsConstructor
public class BettingResultRecoveryService {

    private final BettingEventRepository eventRepository;
    private final BetSettlementService settlementService;

    /**
     * 종료 이벤트의 승자를 복구하고 같은 트랜잭션에서 사용자 배팅까지 정산한다.
     *
     * @param bettingEventId 복구할 배팅 이벤트 ID
     * @param winnerExternalTeamId 운영자가 외부 결과로 확인한 승리 팀 ID
     */
    @Transactional
    public void recoverAndSettle(Long bettingEventId, String winnerExternalTeamId) {
        BettingEvent event = eventRepository.findByIdForUpdate(bettingEventId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        validateRecovery(event, winnerExternalTeamId);
        if (event.getStatus() == BettingEventStatus.SETTLED) {
            return;
        }

        event.recordWinner(winnerExternalTeamId);
        settlementService.settle(bettingEventId);
    }

    /** 이미 확정된 결과는 같은 승자만 멱등하게 허용한다. */
    private void validateRecovery(BettingEvent event, String winnerExternalTeamId) {
        if (event.getWinnerExternalTeamId() != null
                && !event.getWinnerExternalTeamId().equals(winnerExternalTeamId)) {
            throw new BettingException(BettingErrorCode.WINNER_ALREADY_DECIDED);
        }
        if (event.getStatus() != BettingEventStatus.CLOSED
                && event.getStatus() != BettingEventStatus.SETTLED) {
            throw new BettingException(BettingErrorCode.RESULT_NOT_READY);
        }
    }
}
