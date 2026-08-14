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
public class BetPlacementService {

    private final BettingEventRepository bettingEventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final LiveBettingCache liveBettingCache;
    private final Clock clock;

    @Autowired
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
                event.getExternalGameId()
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
