package com.clutch.betting.service;

import com.clutch.betting.config.BettingProperties;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.live.LiveBettingDataProvider.LiveMatchSnapshot;
import com.clutch.betting.live.LiveBettingDataProvider.SetSnapshot;
import com.clutch.betting.repository.BettingEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BettingEventSynchronizationServiceTest {

    private final BettingEventRepository repository = mock(BettingEventRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-14T10:03:00Z"), ZoneOffset.UTC);
    private final BettingEventSynchronizationService service = new BettingEventSynchronizationService(
            repository,
            new BettingProperties(Duration.ofMinutes(2), Duration.ofSeconds(1)),
            clock
    );

    @Test
    void createsAndClosesEventFromLiveCache() {
        LiveMatchSnapshot match = new LiveMatchSnapshot(
                "match-1",
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot(
                        "game-1",
                        1,
                        LocalDateTime.of(2026, 8, 14, 10, 0),
                        true,
                        false,
                        null
                )),
                false
        );
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.empty());
        given(repository.save(any(BettingEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeMatch(match);

        verify(repository).save(any(BettingEvent.class));
        BettingEvent saved = captureSavedEvent();
        assertThat(saved.getExternalGameId()).isEqualTo("game-1");
        assertThat(saved.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 2));
        assertThat(saved.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
    }

    @Test
    void finishesCurrentEventAndOpensNextSetIdempotently() {
        BettingEvent current = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 9, 59)
        );
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.of(current));
        given(repository.findByExternalMatchIdAndSetNumber("match-1", 2))
                .willReturn(Optional.empty());
        given(repository.save(any(BettingEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeMatch(new LiveMatchSnapshot(
                "match-1",
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot("game-1", 1, null, false, true, "team-a")),
                false
        ));

        assertThat(current.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
        assertThat(current.getWinnerExternalTeamId()).isEqualTo("team-a");
        verify(repository).save(any(BettingEvent.class));
    }

    @Test
    void doesNotOpenFutureSetBeforePreviousSetFinishes() {
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 2))
                .willReturn(Optional.empty());

        service.synchronizeMatch(new LiveMatchSnapshot(
                "match-1",
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot("game-2", 2, null, false, false, null)),
                false
        ));

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .save(any(BettingEvent.class));
    }

    @Test
    void cancelsSpeculativeNextSetWhenMatchFinishes() {
        BettingEvent current = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 9, 59)
        );
        BettingEvent speculativeNext = BettingEvent.open(
                "match-1",
                3,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 1)
        );
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 2))
                .willReturn(Optional.of(current));
        given(repository.findAllFutureEventsForUpdate("match-1", 2))
                .willReturn(List.of(speculativeNext));

        service.synchronizeMatch(new LiveMatchSnapshot(
                "match-1",
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot("game-2", 2, null, false, true, "team-a")),
                true
        ));

        assertThat(speculativeNext.getStatus()).isEqualTo(BettingEventStatus.CANCELLED);
    }

    @Test
    void doesNotCreateSpeculativeNextSetWhenMatchIsAlreadyFinished() {
        BettingEvent current = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 9, 59)
        );
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 2))
                .willReturn(Optional.of(current));
        given(repository.findAllFutureEventsForUpdate("match-1", 2))
                .willReturn(List.of());

        service.synchronizeMatch(new LiveMatchSnapshot(
                "match-1",
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot("game-2", 2, null, false, true, "team-a")),
                true
        ));

        verify(repository, never())
                .findByExternalMatchIdAndSetNumber("match-1", 3);
        verify(repository, never()).save(any(BettingEvent.class));
    }

    private BettingEvent captureSavedEvent() {
        org.mockito.ArgumentCaptor<BettingEvent> captor = org.mockito.ArgumentCaptor
                .forClass(BettingEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
