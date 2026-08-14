package com.clutch.betting.service;

import com.clutch.betting.config.BettingProperties;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.integration.lolesports.LiveBettingCache.LiveMatchSnapshot;
import com.clutch.betting.integration.lolesports.LiveBettingCache.SetSnapshot;
import com.clutch.betting.repository.BettingEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class BettingEventSynchronizationProcessor {

    private final BettingEventRepository bettingEventRepository;
    private final BettingProperties bettingProperties;
    private final Clock clock;

    @Autowired
    public BettingEventSynchronizationProcessor(
            BettingEventRepository bettingEventRepository,
            BettingProperties bettingProperties
    ) {
        this(bettingEventRepository, bettingProperties, Clock.systemUTC());
    }

    BettingEventSynchronizationProcessor(
            BettingEventRepository bettingEventRepository,
            BettingProperties bettingProperties,
            Clock clock
    ) {
        this.bettingEventRepository = bettingEventRepository;
        this.bettingProperties = bettingProperties;
        this.clock = clock;
    }

    @Transactional
    public void synchronizeMatch(LiveMatchSnapshot liveMatch) {
        if (!isUsable(liveMatch)) {
            return;
        }
        List<SetSnapshot> sets = liveMatch.sets();
        if (sets == null || sets.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (SetSnapshot set : sets) {
            var existingEvent = bettingEventRepository
                    .findByExternalMatchIdAndSetNumberForUpdate(
                            liveMatch.externalMatchId(),
                            set.setNumber()
                    );
            if (existingEvent.isEmpty() && !set.active() && !set.finished()) {
                continue;
            }
            BettingEvent event = existingEvent
                    .orElseGet(() -> openEvent(liveMatch, set.setNumber(), now));
            event.attachGame(
                    set.externalGameId(),
                    set.startedAt(),
                    bettingProperties.closeAfterSetStart()
            );
            event.closeIfExpired(now);
            if (set.finished()) {
                event.close();
                if (!liveMatch.matchFinished()) {
                    openNextEventIfMissing(liveMatch, set.setNumber() + 1, now);
                }
            }
            if (set.winnerExternalTeamId() != null) {
                event.recordWinner(set.winnerExternalTeamId());
            }
        }
        if (liveMatch.matchFinished()) {
            sets.stream()
                    .filter(SetSnapshot::finished)
                    .mapToInt(SetSnapshot::setNumber)
                    .max()
                    .ifPresent(lastFinishedSetNumber -> bettingEventRepository
                            .findAllFutureEventsForUpdate(
                                    liveMatch.externalMatchId(),
                                    lastFinishedSetNumber
                            )
                            .forEach(BettingEvent::cancel));
        }
    }

    private boolean isUsable(LiveMatchSnapshot liveMatch) {
        return liveMatch != null
                && liveMatch.externalMatchId() != null
                && !liveMatch.externalMatchId().isBlank()
                && liveMatch.externalTeamIds() != null
                && liveMatch.externalTeamIds().size() == 2;
    }

    private BettingEvent openEvent(
            LiveMatchSnapshot liveMatch,
            int setNumber,
            LocalDateTime openedAt
    ) {
        BettingEvent event = BettingEvent.open(
                liveMatch.externalMatchId(),
                setNumber,
                liveMatch.externalTeamIds().get(0),
                liveMatch.externalTeamIds().get(1),
                openedAt
        );
        return bettingEventRepository.save(event);
    }

    private void openNextEventIfMissing(
            LiveMatchSnapshot liveMatch,
            int nextSetNumber,
            LocalDateTime openedAt
    ) {
        if (bettingEventRepository
                .findByExternalMatchIdAndSetNumber(liveMatch.externalMatchId(), nextSetNumber)
                .isEmpty()) {
            openEvent(liveMatch, nextSetNumber, openedAt);
        }
    }
}
