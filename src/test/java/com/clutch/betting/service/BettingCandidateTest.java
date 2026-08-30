package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.dto.BettingCandidateView;
import com.clutch.betting.live.BettingLiveStateReader;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.lolesports.service.SetWinnerTracker;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BettingCandidateTest {

    private final BettingEventRepository repository = mock(BettingEventRepository.class);
    private final DataCacheService cache = new DataCacheService();
    private final SetWinnerTracker setWinnerTracker = mock(SetWinnerTracker.class);
    private final PollingScheduler pollingScheduler = mock(PollingScheduler.class);
    private final BettingService service = new BettingService(
            repository,
            mock(UserBetRepository.class),
            mock(BetPointTransactionRepository.class),
            mock(UserRepository.class),
            mock(com.clutch.lolesports.repository.MatchTeamRepository.class),
            mock(BettingLiveStateReader.class),
            cache,
            setWinnerTracker,
            pollingScheduler,
            Clock.fixed(Instant.parse("2026-08-14T09:45:00Z"), ZoneOffset.UTC)
    );

    @Test
    void findsOnlyCachedMatchesWithAnOpenBettingEvent() {
        cache.putBettingMatches(List.of(match("match-open"), match("match-without-event")));
        given(repository.findAllByStatus(BettingEventStatus.OPEN)).willReturn(List.of(
                event("match-open", LocalDateTime.of(2026, 8, 14, 9, 40),
                        LocalDateTime.of(2026, 8, 14, 10, 1))
        ));

        List<BettingCandidateView> candidates = service.findBettingCandidates();

        assertThat(candidates).extracting(BettingCandidateView::matchId)
                .containsExactly("match-open");
    }

    @Test
    void excludesAnExpiredOpenEvent() {
        cache.putBettingMatches(List.of(match("match-expired")));
        given(repository.findAllByStatus(BettingEventStatus.OPEN)).willReturn(List.of(
                event("match-expired", LocalDateTime.of(2026, 8, 14, 9, 20),
                        LocalDateTime.of(2026, 8, 14, 9, 44))
        ));

        assertThat(service.findBettingCandidates()).isEmpty();
    }

    private BettingEvent event(String matchId, LocalDateTime openedAt, LocalDateTime closesAt) {
        return BettingEvent.open(matchId, 1, "team-a", "team-b", openedAt, closesAt);
    }

    private DataCacheService.LiveMatch match(String matchId) {
        return new DataCacheService.LiveMatch(
                matchId,
                "week 1",
                "LCK",
                "2026-08-14T10:00:00Z",
                3,
                List.of(
                        new ScheduleResponse.Team("team-a", "A", "A", null, null, null),
                        new ScheduleResponse.Team("team-b", "B", "B", null, null, null)
                ),
                List.<EventDetailsResponse.Game>of(),
                null
        );
    }
}
