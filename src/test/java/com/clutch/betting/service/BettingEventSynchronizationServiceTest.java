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

    private static final LocalDateTime SCHEDULED_START =
            LocalDateTime.of(2026, 8, 14, 10, 0);

    private final BettingEventRepository repository = mock(BettingEventRepository.class);

    /** 첫 세트 공식 시작 20분 전 경계에서 이벤트가 열리는지 검증한다. */
    @Test
    void opensFirstSetTwentyMinutesBeforeOfficialStart() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T09:40:00Z");
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.empty());
        given(repository.save(any(BettingEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeMatch(snapshot(List.of()));

        BettingEvent saved = captureSavedEvent();
        assertThat(saved.getOpenedAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 9, 40));
        assertThat(saved.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 1));
        assertThat(saved.getStatus()).isEqualTo(BettingEventStatus.OPEN);
    }

    /** 첫 세트 공식 시작 20분 전보다 이르면 이벤트가 열리지 않는지 검증한다. */
    @Test
    void doesNotOpenFirstSetBeforeBettingWindow() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T09:39:59Z");

        service.synchronizeMatch(snapshot(List.of()));

        verify(repository, never()).save(any(BettingEvent.class));
    }

    /** 공식 시작 시각을 복구할 수 없는 진행 이벤트가 취소되는지 검증한다. */
    @Test
    void cancelsExistingEventWhenOfficialStartCannotBeRecovered() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T09:40:00Z");
        BettingEvent existing = firstEvent();
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.of(existing));
        LiveMatchSnapshot missingSchedule = new LiveMatchSnapshot(
                "match-1",
                null,
                List.of("team-a", "team-b"),
                List.of(),
                false
        );

        service.synchronizeMatch(missingSchedule);

        assertThat(existing.getStatus()).isEqualTo(BettingEventStatus.CANCELLED);
    }

    /** 스케줄러가 늦게 복구돼도 첫 세트 마감이 공식 시작 1분 후로 유지되는지 검증한다. */
    @Test
    void closesRecoveredFirstSetAtOneMinuteAfterOfficialStart() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.empty());
        given(repository.save(any(BettingEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeMatch(snapshot(List.of(activeSet(1))));

        BettingEvent saved = captureSavedEvent();
        assertThat(saved.getExternalGameId()).isEqualTo("game-1");
        assertThat(saved.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
        assertThat(saved.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 1));
    }

    /** 종료 피드 시각부터 20분 동안 다음 세트 이벤트가 열리는지 검증한다. */
    @Test
    void opensNextSetForTwentyMinutesFromFeedFinish() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        BettingEvent current = firstEvent();
        LocalDateTime finishedAt = LocalDateTime.of(2026, 8, 14, 10, 2);
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.of(current));
        given(repository.findByExternalMatchIdAndSetNumber("match-1", 2))
                .willReturn(Optional.empty());
        given(repository.save(any(BettingEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeMatch(snapshot(List.of(
                new SetSnapshot("game-1", 1, null, false, true, finishedAt, "team-a")
        )));

        BettingEvent next = captureSavedEvent();
        assertThat(current.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
        assertThat(current.getWinnerExternalTeamId()).isEqualTo("team-a");
        assertThat(next.getSetNumber()).isEqualTo(2);
        assertThat(next.getOpenedAt()).isEqualTo(finishedAt);
        assertThat(next.getClosesAt()).isEqualTo(finishedAt.plusMinutes(20));
    }

    /** 종료 피드 시각이 없으면 다음 세트 이벤트를 만들지 않는지 검증한다. */
    @Test
    void doesNotOpenNextSetWithoutFeedFinishTime() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        BettingEvent current = firstEvent();
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.of(current));

        service.synchronizeMatch(snapshot(List.of(
                new SetSnapshot("game-1", 1, null, false, true, null, "team-a")
        )));

        verify(repository, never()).findByExternalMatchIdAndSetNumber("match-1", 2);
        verify(repository, never()).save(any(BettingEvent.class));
    }

    /**
     * 기간 기준이 누락돼도 이미 종료되고 승자가 확인된 이벤트는 취소하지 않는지 검증한다.
     */
    @Test
    void preservesFinishedResultWhenPeriodCannotBeRecovered() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        BettingEvent existing = firstEvent();
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.of(existing));
        given(repository.findByExternalMatchIdAndSetNumber("match-1", 2))
                .willReturn(Optional.of(BettingEvent.open(
                        "match-1",
                        2,
                        "team-a",
                        "team-b",
                        LocalDateTime.of(2026, 8, 14, 10, 2),
                        LocalDateTime.of(2026, 8, 14, 10, 22)
                )));
        LiveMatchSnapshot missingSchedule = new LiveMatchSnapshot(
                "match-1",
                null,
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot(
                        "game-1",
                        1,
                        null,
                        false,
                        true,
                        LocalDateTime.of(2026, 8, 14, 10, 2),
                        "team-a"
                )),
                false
        );

        service.synchronizeMatch(missingSchedule);

        assertThat(existing.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
        assertThat(existing.getExternalGameId()).isEqualTo("game-1");
        assertThat(existing.getWinnerExternalTeamId()).isEqualTo("team-a");
    }

    /** 매치 종료 시 실제 존재하지 않는 미래 세트 이벤트가 취소되는지 검증한다. */
    @Test
    void cancelsSpeculativeFutureSetWhenMatchFinishes() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        BettingEvent current = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 20)
        );
        BettingEvent future = BettingEvent.open(
                "match-1",
                3,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 2),
                LocalDateTime.of(2026, 8, 14, 10, 22)
        );
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 2))
                .willReturn(Optional.of(current));
        given(repository.findAllFutureEventsForUpdate("match-1", 2))
                .willReturn(List.of(future));

        service.synchronizeMatch(new LiveMatchSnapshot(
                "match-1",
                SCHEDULED_START,
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot(
                        "game-2",
                        2,
                        null,
                        false,
                        true,
                        LocalDateTime.of(2026, 8, 14, 10, 2),
                        "team-a"
                )),
                true
        ));

        assertThat(future.getStatus()).isEqualTo(BettingEventStatus.CANCELLED);
        verify(repository, never()).findByExternalMatchIdAndSetNumber("match-1", 3);
    }

    /**
     * 지정 UTC 시각을 사용하는 동기화 서비스를 생성한다.
     *
     * @param instant ISO-8601 UTC 기준 시각
     * @return 고정 시계를 사용하는 동기화 서비스
     */
    private BettingEventSynchronizationService serviceAt(String instant) {
        return new BettingEventSynchronizationService(
                repository,
                new BettingProperties(
                        Duration.ofMinutes(20),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(20),
                        Duration.ofSeconds(1)
                ),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
        );
    }

    /**
     * 지정 세트 목록을 가진 기본 매치 스냅샷을 생성한다.
     *
     * @param sets 세트 상태 목록
     * @return 테스트용 매치 스냅샷
     */
    private LiveMatchSnapshot snapshot(List<SetSnapshot> sets) {
        return new LiveMatchSnapshot(
                "match-1",
                SCHEDULED_START,
                List.of("team-a", "team-b"),
                sets,
                false
        );
    }

    /**
     * 지정 번호의 진행 중 세트 스냅샷을 생성한다.
     *
     * @param setNumber 세트 번호
     * @return 진행 중 세트 스냅샷
     */
    private SetSnapshot activeSet(int setNumber) {
        return new SetSnapshot(
                "game-" + setNumber,
                setNumber,
                SCHEDULED_START,
                true,
                false,
                null,
                null
        );
    }

    /**
     * 공식 첫 세트 기간을 가진 기존 이벤트를 생성한다.
     *
     * @return 테스트용 첫 세트 이벤트
     */
    private BettingEvent firstEvent() {
        return BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 9, 40),
                LocalDateTime.of(2026, 8, 14, 10, 1)
        );
    }

    /**
     * 저장소에 전달된 단일 이벤트를 캡처한다.
     *
     * @return 저장 호출에서 캡처한 배팅 이벤트
     */
    private BettingEvent captureSavedEvent() {
        org.mockito.ArgumentCaptor<BettingEvent> captor = org.mockito.ArgumentCaptor
                .forClass(BettingEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
