package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.integration.lolesports.LiveBettingCache;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.user.domain.User;
import com.clutch.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BetQueryService {

    private static final List<BettingEventStatus> CURRENT_STATUSES = List.of(
            BettingEventStatus.OPEN,
            BettingEventStatus.CLOSED
    );

    private final BettingEventRepository eventRepository;
    private final UserBetRepository userBetRepository;
    private final UserRepository userRepository;
    private final LiveBettingCache liveBettingCache;
    private final Clock clock;

    @Autowired
    public BetQueryService(
            BettingEventRepository eventRepository,
            UserBetRepository userBetRepository,
            UserRepository userRepository,
            LiveBettingCache liveBettingCache
    ) {
        this(eventRepository, userBetRepository, userRepository, liveBettingCache, Clock.systemUTC());
    }

    BetQueryService(
            BettingEventRepository eventRepository,
            UserBetRepository userBetRepository,
            UserRepository userRepository,
            LiveBettingCache liveBettingCache,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.userBetRepository = userBetRepository;
        this.userRepository = userRepository;
        this.liveBettingCache = liveBettingCache;
        this.clock = clock;
    }

    public BettingEventView getCurrentEvent(String externalMatchId, Long userId) {
        BettingEvent event = eventRepository
                .findFirstByExternalMatchIdAndStatusInOrderBySetNumberDesc(
                        externalMatchId,
                        CURRENT_STATUSES
                )
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        LocalDateTime now = now();
        UserBet userBet = userBetRepository
                .findByBettingEventIdAndUserId(event.getId(), userId)
                .orElse(null);
        boolean liveAvailable = liveBettingCache.isAcceptingBets(
                event.getExternalMatchId(),
                event.getExternalGameId()
        );
        return new BettingEventView(
                event.getId(),
                event.getExternalMatchId(),
                event.getExternalGameId(),
                event.getSetNumber(),
                event.getFirstExternalTeamId(),
                event.getSecondExternalTeamId(),
                event.getStatus(),
                event.getClosesAt(),
                remainingSeconds(event.getClosesAt(), now),
                userBet == null && event.isOpenAt(now) && liveAvailable,
                toSummary(userBet)
        );
    }

    public UserBetView getMyBet(Long bettingEventId, Long userId) {
        UserBet userBet = userBetRepository
                .findByBettingEventIdAndUserId(bettingEventId, userId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.BET_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.USER_NOT_FOUND));
        return new UserBetView(
                userBet.getId(),
                bettingEventId,
                userBet.getSelectedExternalTeamId(),
                userBet.getAmount(),
                userBet.getStatus(),
                user.getPoint()
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private long remainingSeconds(LocalDateTime closesAt, LocalDateTime now) {
        if (closesAt == null) {
            return -1L;
        }
        return Math.max(0L, Duration.between(now, closesAt).toSeconds());
    }

    private BettingEventView.UserBetSummary toSummary(UserBet userBet) {
        if (userBet == null) {
            return null;
        }
        return new BettingEventView.UserBetSummary(
                userBet.getId(),
                userBet.getSelectedExternalTeamId(),
                userBet.getAmount(),
                userBet.getStatus()
        );
    }
}
