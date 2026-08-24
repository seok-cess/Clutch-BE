package com.clutch.betting.service;

import com.clutch.betting.config.BettingProperties;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.live.LiveBettingDataProvider.LiveMatchSnapshot;
import com.clutch.betting.live.LiveBettingDataProvider.SetSnapshot;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
    private final EsportsMatchRepository esportsMatchRepository = mock(EsportsMatchRepository.class);

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
        assertThat(saved.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 20));
        assertThat(saved.getStatus()).isEqualTo(BettingEventStatus.OPEN);
    }

    /** 첫 세트 공식 시작 20분 전보다 이르면 이벤트가 열리지 않는지 검증한다. */
    @Test
    void doesNotOpenFirstSetBeforeBettingWindow() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T09:39:59Z");

        service.synchronizeMatch(snapshot(List.of()));

        verify(repository, never()).save(any(BettingEvent.class));
    }

    /** 공식 시작 시각이 일시적으로 누락돼도 진행 이벤트를 유지하는지 검증한다. */
    @Test
    void preservesExistingEventWhenOfficialStartCannotBeRecovered() {
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

        assertThat(existing.getStatus()).isEqualTo(BettingEventStatus.OPEN);
    }

    /** 이전 세트 종료 시각이 누락돼도 선개설된 다음 세트 이벤트에 실제 게임을 연결하는지 검증한다. */
    @Test
    void preservesNextSetEventWhenPreviousFinishTimeIsMissing() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        BettingEvent existing = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 2),
                LocalDateTime.of(2026, 8, 14, 10, 22)
        );
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 2))
                .willReturn(Optional.of(existing));

        service.synchronizeMatch(snapshot(List.of(activeSetAt(
                2,
                LocalDateTime.of(2026, 8, 14, 10, 3)
        ))));

        assertThat(existing.getStatus()).isEqualTo(BettingEventStatus.OPEN);
        assertThat(existing.getExternalGameId()).isEqualTo("game-2");
    }

    /** 실제 첫 프레임 후 1분이 지나면 첫 세트 배팅을 마감하는지 검증한다. */
    @Test
    void closesFirstSetOneMinuteAfterActualStatsStart() {
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

    /** 실제 첫 프레임 뒤 1분 전에는 첫 세트 배팅이 열려 있어야 한다. */
    @Test
    void keepsFirstSetOpenDuringOneMinuteAfterActualStatsStart() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:00:30Z");
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.empty());
        given(repository.save(any(BettingEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeMatch(snapshot(List.of(activeSet(1))));

        BettingEvent saved = captureSavedEvent();
        assertThat(saved.getStatus()).isEqualTo(BettingEventStatus.OPEN);
        assertThat(saved.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 1));
    }

    /** replay는 배속과 무관하게 게임 시계 1분을 넘는 즉시 첫 세트 배팅을 마감한다. */
    @Test
    void closesFirstSetWhenReplayGameClockReachesOneMinute() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:00:03Z");
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.empty());
        given(repository.save(any(BettingEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeMatch(snapshot(List.of(new SetSnapshot(
                "game-1", 1, LocalDateTime.of(2026, 8, 14, 10, 0), 60L,
                true, false, null, null
        ))));

        assertThat(captureSavedEvent().getStatus()).isEqualTo(BettingEventStatus.CLOSED);
    }

    /** 일정이 먼저 inProgress가 되어도 첫 livestats 프레임 전에는 배팅을 유지한다. */
    @Test
    void keepsFirstSetOpenUntilActualStatsStart() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.empty());
        given(repository.save(any(BettingEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeMatch(snapshot(List.of(new SetSnapshot(
                "game-1", 1, null, true, false, null, null
        ))));

        BettingEvent saved = captureSavedEvent();
        assertThat(saved.getStatus()).isEqualTo(BettingEventStatus.OPEN);
        assertThat(saved.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 20));
    }

    /** 실제 다음 세트 시작 시각을 아직 모르면 안전 마감 시각까지 다음 세트를 열어 둔다. */
    @Test
    void opensNextSetWithSafetyDeadlineUntilNextSetStarts() {
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

    /** 다음 세트가 실제로 시작되면 이전 세트 종료 기준의 안전 마감 대신 시작 1분 후에 닫는다. */
    @Test
    void closesNextSetOneMinuteAfterActualGameStart() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:11:00Z");
        BettingEvent previous = firstEvent();
        BettingEvent next = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 2),
                LocalDateTime.of(2026, 8, 14, 10, 22)
        );
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.of(previous));
        given(repository.findByExternalMatchIdAndSetNumber("match-1", 2))
                .willReturn(Optional.of(next));
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 2))
                .willReturn(Optional.of(next));

        service.synchronizeMatch(snapshot(List.of(
                new SetSnapshot(
                        "game-1",
                        1,
                        null,
                        false,
                        true,
                        LocalDateTime.of(2026, 8, 14, 10, 2),
                        "team-a"
                ),
                activeSetAt(2, LocalDateTime.of(2026, 8, 14, 10, 10))
        )));

        assertThat(next.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 11));
        assertThat(next.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
    }

    /** 실제 시작 시각을 한 번 확인한 다음에는 일시적 캐시 누락이 안전 마감을 다시 늦추지 않는다. */
    @Test
    void doesNotExtendNextSetDeadlineWhenGameStartTemporarilyDisappearsFromCache() {
        BettingEvent previous = firstEvent();
        BettingEvent next = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 2),
                LocalDateTime.of(2026, 8, 14, 10, 22)
        );
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.of(previous));
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 2))
                .willReturn(Optional.of(next));
        given(repository.findByExternalMatchIdAndSetNumber("match-1", 2))
                .willReturn(Optional.of(next));

        List<SetSnapshot> startedSets = List.of(
                new SetSnapshot(
                        "game-1",
                        1,
                        null,
                        false,
                        true,
                        LocalDateTime.of(2026, 8, 14, 10, 2),
                        "team-a"
                ),
                activeSetAt(2, LocalDateTime.of(2026, 8, 14, 10, 10))
        );
        serviceAt("2026-08-14T10:10:30Z").synchronizeMatch(snapshot(startedSets));

        assertThat(next.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 11));

        List<SetSnapshot> missingStartSets = List.of(
                startedSets.getFirst(),
                new SetSnapshot("game-2", 2, null, true, false, null, null)
        );
        serviceAt("2026-08-14T10:10:45Z").synchronizeMatch(snapshot(missingStartSets));

        assertThat(next.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 11));

        serviceAt("2026-08-14T10:12:00Z").synchronizeMatch(snapshot(missingStartSets));

        assertThat(next.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
    }

    /** 종료 피드 시각이 이미 사라졌어도 공식 완료 관측 시각부터 다음 세트 이벤트를 여는지 검증한다. */
    @Test
    void opensNextSetAtOfficialCompletionObservationWhenFeedFinishTimeIsMissing() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        BettingEvent current = firstEvent();
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 1))
                .willReturn(Optional.of(current));
        given(repository.findByExternalMatchIdAndSetNumber("match-1", 2))
                .willReturn(Optional.empty());
        given(repository.save(any(BettingEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeMatch(snapshot(List.of(
                new SetSnapshot("game-1", 1, null, false, true, null, "team-a")
        )));

        BettingEvent next = captureSavedEvent();
        assertThat(next.getSetNumber()).isEqualTo(2);
        assertThat(next.getOpenedAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 3));
        assertThat(next.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 23));
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

    /** 라이브 목록 밖에서 공식 결과가 늦게 확인돼도 종료 이벤트에만 승자를 반영한다. */
    @Test
    void recordsReconciledWinnerForClosedEvent() {
        BettingEvent event = firstEvent();
        event.attachGame("game-1");
        event.close();
        given(repository.findAllClosedWithoutWinnerForUpdate("match-1"))
                .willReturn(List.of(event));

        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        service.synchronizeConfirmedWinners("match-1", Map.of("game-1", "team-a"));

        assertThat(event.getWinnerExternalTeamId()).isEqualTo("team-a");
    }

    /** 종료 프레임이 적재된 열린 이벤트를 결과 재조회 전에 닫는지 검증한다. */
    @Test
    void closesOpenEventWhenItsGameHasAlreadyFinishedInPersistence() {
        BettingEvent event = firstEvent();
        event.attachGame("game-1");
        given(repository.findAllUnsettledFinishedGameEventsForUpdate("match-1"))
                .willReturn(List.of(event));

        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:03:00Z");
        service.closeFinishedEventsForReconciliation("match-1");

        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
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

    /** BO3의 세 번째 세트가 끝나면 공식 매치 완료 응답 전에도 4세트를 만들지 않는다. */
    @Test
    void doesNotOpenSetAfterLastPossibleSetBeforeOfficialMatchCompletion() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:35:00Z");
        BettingEvent thirdSet = BettingEvent.open(
                "match-1",
                3,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 10),
                LocalDateTime.of(2026, 8, 14, 10, 30)
        );
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 3))
                .willReturn(Optional.of(thirdSet));

        service.synchronizeMatch(new LiveMatchSnapshot(
                "match-1",
                SCHEDULED_START,
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot(
                        "game-3",
                        3,
                        null,
                        false,
                        true,
                        LocalDateTime.of(2026, 8, 14, 10, 34),
                        "team-a"
                )),
                false,
                3
        ));

        assertThat(thirdSet.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
        verify(repository, never()).findByExternalMatchIdAndSetNumber("match-1", 4);
        verify(repository, never()).save(any(BettingEvent.class));
    }

    /** 캐시의 bestOf가 비어 있어도 DB에 적재한 BO3 정보로 4세트 생성을 막는다. */
    @Test
    void doesNotOpenSetAfterLastPossibleSetWhenOnlyPersistedBestOfIsAvailable() {
        BettingEventSynchronizationService service = serviceAt("2026-08-14T10:35:00Z");
        BettingEvent thirdSet = BettingEvent.open(
                "match-1",
                3,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 10),
                LocalDateTime.of(2026, 8, 14, 10, 30)
        );
        EsportsMatch persistedMatch = mock(EsportsMatch.class);
        given(persistedMatch.getBestOf()).willReturn(3);
        given(esportsMatchRepository.findByExternalMatchId("match-1"))
                .willReturn(Optional.of(persistedMatch));
        given(repository.findByExternalMatchIdAndSetNumberForUpdate("match-1", 3))
                .willReturn(Optional.of(thirdSet));

        service.synchronizeMatch(new LiveMatchSnapshot(
                "match-1",
                SCHEDULED_START,
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot(
                        "game-3",
                        3,
                        null,
                        false,
                        true,
                        LocalDateTime.of(2026, 8, 14, 10, 34),
                        "team-a"
                )),
                false
        ));

        verify(repository, never()).findByExternalMatchIdAndSetNumber("match-1", 4);
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
                esportsMatchRepository,
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
        return activeSetAt(setNumber, SCHEDULED_START);
    }

    /** 지정 시각에 시작한 진행 중 세트를 만든다. */
    private SetSnapshot activeSetAt(int setNumber, LocalDateTime startedAt) {
        return new SetSnapshot(
                "game-" + setNumber,
                setNumber,
                startedAt,
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
