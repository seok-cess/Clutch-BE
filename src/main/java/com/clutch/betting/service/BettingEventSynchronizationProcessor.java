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
/** 한 라이브 매치의 세트 스냅샷을 배팅 이벤트 생명주기에 반영한다. */
public class BettingEventSynchronizationProcessor {

    private final BettingEventRepository bettingEventRepository;
    private final BettingProperties bettingProperties;
    private final Clock clock;

    @Autowired
    /** 운영 환경에서 UTC 시스템 시계를 사용하는 동기화 처리기를 구성한다. */
    public BettingEventSynchronizationProcessor(
            BettingEventRepository bettingEventRepository,
            BettingProperties bettingProperties
    ) {
        this(bettingEventRepository, bettingProperties, Clock.systemUTC());
    }

    /** 테스트에서 현재 시각을 고정할 수 있도록 동기화 처리기를 구성한다. */
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
    /** 세트 시작·마감·종료·승자를 동기화하고 경기 종료 시 후속 이벤트를 취소한다. */
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

    /** 매치 ID와 정확히 두 참가 팀을 가진 스냅샷만 처리 대상으로 인정한다. */
    private boolean isUsable(LiveMatchSnapshot liveMatch) {
        return liveMatch != null
                && liveMatch.externalMatchId() != null
                && !liveMatch.externalMatchId().isBlank()
                && liveMatch.externalTeamIds() != null
                && liveMatch.externalTeamIds().size() == 2;
    }

    /** 주어진 매치·세트에 대한 신규 배팅 이벤트를 저장한다. */
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

    /** 이전 세트 종료 직후 다음 세트 이벤트를 중복 없이 선개설한다. */
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
