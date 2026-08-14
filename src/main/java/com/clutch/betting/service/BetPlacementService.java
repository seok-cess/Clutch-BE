package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.integration.lolesports.LiveBettingCache;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
/** 배팅 가능 상태를 검증하고 포인트 차감과 사용자 배팅 등록을 한 트랜잭션으로 처리한다. */
public class BetPlacementService {

    private final BettingEventRepository bettingEventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final LiveBettingCache liveBettingCache;
    private final Clock clock;

    @Autowired
    /** 운영 환경에서 UTC 시스템 시계를 사용하는 등록 서비스를 구성한다. */
    public BetPlacementService(
            BettingEventRepository bettingEventRepository,
            UserBetRepository userBetRepository,
            BetPointTransactionRepository transactionRepository,
            UserRepository userRepository,
            LiveBettingCache liveBettingCache
    ) {
        this(
                bettingEventRepository,
                userBetRepository,
                transactionRepository,
                userRepository,
                liveBettingCache,
                Clock.systemUTC()
        );
    }

    /** 테스트에서 결정적인 현재 시각을 주입할 수 있도록 서비스를 구성한다. */
    BetPlacementService(
            BettingEventRepository bettingEventRepository,
            UserBetRepository userBetRepository,
            BetPointTransactionRepository transactionRepository,
            UserRepository userRepository,
            LiveBettingCache liveBettingCache,
            Clock clock
    ) {
        this.bettingEventRepository = bettingEventRepository;
        this.userBetRepository = userBetRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.liveBettingCache = liveBettingCache;
        this.clock = clock;
    }

    @Transactional
    /** 이벤트 행을 잠근 뒤 중복·라이브 상태·포인트를 검증하고 배팅을 등록한다. */
    public BetPlacementResult place(
            Long userId,
            Long bettingEventId,
            String selectedExternalTeamId,
            long amount
    ) {
        BettingEvent event = bettingEventRepository.findByIdForUpdate(bettingEventId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!event.isOpenAt(now)) {
            throw new BettingException(BettingErrorCode.EVENT_NOT_OPEN);
        }
        if (!liveBettingCache.isAcceptingBets(
                event.getExternalMatchId(),
                event.getExternalGameId(),
                event.getSetNumber()
        )) {
            throw new BettingException(BettingErrorCode.LIVE_DATA_UNAVAILABLE);
        }
        if (!event.hasParticipant(selectedExternalTeamId)) {
            throw new BettingException(BettingErrorCode.INVALID_TEAM);
        }
        if (userBetRepository.existsByBettingEventIdAndUserId(bettingEventId, userId)) {
            throw new BettingException(BettingErrorCode.DUPLICATE_BET);
        }

        UserBet userBet = UserBet.place(
                bettingEventId,
                userId,
                selectedExternalTeamId,
                amount
        );
        decreasePoint(userId, amount);
        try {
            userBetRepository.saveAndFlush(userBet);
            transactionRepository.saveAndFlush(BetPointTransaction.stake(userBet.getId(), amount));
        } catch (DataIntegrityViolationException exception) {
            throw new BettingException(BettingErrorCode.DUPLICATE_BET);
        }

        return new BetPlacementResult(
                userBet.getId(),
                bettingEventId,
                selectedExternalTeamId,
                amount,
                userBet.getStatus(),
                userRepository.findPointById(userId)
                        .orElseThrow(() -> new BettingException(BettingErrorCode.USER_NOT_FOUND))
        );
    }

    /** 조건부 갱신으로 포인트를 원자적으로 차감하고 실패 원인을 구분한다. */
    private void decreasePoint(Long userId, long amount) {
        int updated = userRepository.decreasePointIfEnough(userId, amount);
        if (updated == 1) {
            return;
        }
        if (!userRepository.existsById(userId)) {
            throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
        }
        throw new BettingException(BettingErrorCode.INSUFFICIENT_POINT);
    }
}
