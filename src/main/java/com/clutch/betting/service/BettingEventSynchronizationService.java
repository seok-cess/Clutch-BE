package com.clutch.betting.service;

import com.clutch.betting.config.BettingProperties;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.live.LiveBettingDataProvider.LiveMatchSnapshot;
import com.clutch.betting.live.LiveBettingDataProvider.SetSnapshot;
import com.clutch.betting.repository.BettingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** 한 라이브 매치의 세트 스냅샷을 배팅 이벤트 생명주기에 반영한다. */
@Service
@RequiredArgsConstructor
public class BettingEventSynchronizationService {

    private final BettingEventRepository bettingEventRepository;
    private final BettingProperties bettingProperties;
    private final Clock clock;

    /**
     * 세트 시작·마감·종료·승자를 동기화하고 경기 종료 시 후속 이벤트를 취소한다.
     *
     * @param liveMatch 동기화할 라이브 매치 스냅샷
     */
    @Transactional
    public void synchronizeMatch(LiveMatchSnapshot liveMatch) {
        if (!hasRequiredMatchData(liveMatch)) {
            return;
        }
        List<SetSnapshot> sets = liveMatch.sets();
        if (sets == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (sets.isEmpty()) {
            openScheduledFirstSetIfAvailable(liveMatch, now);
            return;
        }
        for (SetSnapshot set : sets) {
            synchronizeSet(liveMatch, set, now);
        }
        cancelUnusedFutureEvents(liveMatch);
    }

    /**
     * 한 세트의 기간 복구, 이벤트 생성, 마감과 종료 결과를 순서대로 반영한다.
     *
     * @param liveMatch 세트가 속한 라이브 매치
     * @param set 동기화할 세트
     * @param now 현재 UTC 시각
     */
    private void synchronizeSet(
            LiveMatchSnapshot liveMatch,
            SetSnapshot set,
            LocalDateTime now
    ) {
        BettingEvent event = bettingEventRepository
                .findByExternalMatchIdAndSetNumberForUpdate(
                        liveMatch.externalMatchId(),
                        set.setNumber()
                )
                .orElse(null);
        BettingPeriod period = periodOf(liveMatch, set.setNumber());
        if (period == null) {
            synchronizeWithoutPeriod(liveMatch, set, event);
            return;
        }
        if (now.isBefore(period.openedAt())) {
            updateOpenPeriod(event, period);
            return;
        }

        if (event == null) {
            event = openEvent(liveMatch, set.setNumber(), period);
        }
        updateOpenPeriod(event, period);
        attachGameIfPresent(event, set.externalGameId());
        event.closeIfExpired(now);
        if (set.finished()) {
            finishEvent(event, set);
            openNextEventAfterFinishedSet(liveMatch, set);
        }
    }

    /**
     * 기간을 복구할 수 없으면 진행 이벤트는 유지하고 확인된 종료 결과만 반영한다.
     * 라이브 피드의 일시적인 시각 누락은 경기 또는 배팅 이벤트 취소를 의미하지 않는다.
     *
     * @param liveMatch 세트가 속한 라이브 매치
     * @param set 동기화할 세트
     * @param existingEvent 기존 이벤트 또는 없으면 null
     */
    private void synchronizeWithoutPeriod(
            LiveMatchSnapshot liveMatch,
            SetSnapshot set,
            BettingEvent existingEvent
    ) {
        if (!set.finished()) {
            return;
        }
        if (existingEvent != null) {
            finishEvent(existingEvent, set);
        }
        openNextEventAfterFinishedSet(liveMatch, set);
    }

    /**
     * 열린 이벤트에만 최신 오픈·마감 기간을 반영한다.
     *
     * @param event 갱신할 이벤트 또는 없으면 null
     * @param period 최신 배팅 기간
     */
    private void updateOpenPeriod(BettingEvent event, BettingPeriod period) {
        if (event != null && event.getStatus() == BettingEventStatus.OPEN) {
            event.definePeriod(period.openedAt(), period.closesAt());
        }
    }

    /**
     * 외부 게임 ID가 준비된 경우에만 이벤트에 연결한다.
     *
     * @param event 게임을 연결할 이벤트
     * @param externalGameId 외부 게임 ID 또는 미확정이면 null
     */
    private void attachGameIfPresent(BettingEvent event, String externalGameId) {
        if (externalGameId != null && !externalGameId.isBlank()) {
            event.attachGame(externalGameId);
        }
    }

    /**
     * 매치 종료 후 실제로 진행되지 않을 미래 세트 이벤트를 취소한다.
     *
     * @param liveMatch 종료 여부와 세트 목록을 가진 라이브 매치
     */
    private void cancelUnusedFutureEvents(LiveMatchSnapshot liveMatch) {
        if (!liveMatch.matchFinished()) {
            return;
        }
        liveMatch.sets().stream()
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

    /**
     * 매치 ID와 정확히 두 참가 팀을 가진 스냅샷만 처리 대상으로 인정한다.
     *
     * @param liveMatch 검증할 라이브 매치 스냅샷
     * @return 동기화에 필요한 최소 정보가 있으면 true
     */
    private boolean hasRequiredMatchData(LiveMatchSnapshot liveMatch) {
        return liveMatch != null
                && liveMatch.externalMatchId() != null
                && !liveMatch.externalMatchId().isBlank()
                && liveMatch.externalTeamIds() != null
                && liveMatch.externalTeamIds().size() == 2;
    }

    /**
     * 주어진 매치·세트에 대한 신규 배팅 이벤트를 저장한다.
     *
     * @param liveMatch 라이브 매치 스냅샷
     * @param setNumber 생성할 세트 번호
     * @param period 이벤트 오픈·마감 기간
     * @return 저장된 배팅 이벤트
     */
    private BettingEvent openEvent(
            LiveMatchSnapshot liveMatch,
            int setNumber,
            BettingPeriod period
    ) {
        BettingEvent event = BettingEvent.open(
                liveMatch.externalMatchId(),
                setNumber,
                liveMatch.externalTeamIds().get(0),
                liveMatch.externalTeamIds().get(1),
                period.openedAt(),
                period.closesAt()
        );
        return bettingEventRepository.save(event);
    }

    /**
     * 이전 세트 종료 직후 다음 세트 이벤트를 중복 없이 선개설한다.
     *
     * @param liveMatch 라이브 매치 스냅샷
     * @param nextSetNumber 선개설할 다음 세트 번호
     * @param period 다음 세트 배팅 기간
     */
    private void openNextEventIfMissing(
            LiveMatchSnapshot liveMatch,
            int nextSetNumber,
            BettingPeriod period
    ) {
        if (bettingEventRepository
                .findByExternalMatchIdAndSetNumber(liveMatch.externalMatchId(), nextSetNumber)
                .isEmpty()) {
            openEvent(liveMatch, nextSetNumber, period);
        }
    }

    /**
     * 세트 종료 시각이 있으면 직후부터 다음 세트 배팅 이벤트를 선개설한다.
     *
     * @param liveMatch 종료 세트가 속한 매치 스냅샷
     * @param finishedSet 종료된 세트 스냅샷
     */
    private void openNextEventAfterFinishedSet(
            LiveMatchSnapshot liveMatch,
            SetSnapshot finishedSet
    ) {
        if (liveMatch.matchFinished() || finishedSet.finishedAt() == null) {
            return;
        }
        openNextEventIfMissing(
                liveMatch,
                finishedSet.setNumber() + 1,
                new BettingPeriod(
                        finishedSet.finishedAt(),
                        finishedSet.finishedAt().plus(bettingProperties.nextSetBettingDuration())
                )
        );
    }

    /**
     * 기간 정보를 복구할 수 없는 경우에도 종료된 기존 이벤트의 게임과 승자 정보는 보존한다.
     *
     * @param event 종료 처리할 기존 배팅 이벤트
     * @param finishedSet 종료된 세트 스냅샷
     */
    private void finishEvent(BettingEvent event, SetSnapshot finishedSet) {
        attachGameIfPresent(event, finishedSet.externalGameId());
        event.close();
        if (finishedSet.winnerExternalTeamId() != null) {
            event.recordWinner(finishedSet.winnerExternalTeamId());
        }
    }

    /**
     * 외부 게임 목록이 아직 없어도 공식 일정과 팀이 준비되면 첫 세트 이벤트를 선개설한다.
     *
     * @param liveMatch 예정 매치 스냅샷
     * @param now 현재 UTC 시각
     */
    private void openScheduledFirstSetIfAvailable(
            LiveMatchSnapshot liveMatch,
            LocalDateTime now
    ) {
        BettingPeriod period = periodOf(liveMatch, 1);
        if (period == null) {
            return;
        }
        BettingEvent event = bettingEventRepository
                .findByExternalMatchIdAndSetNumberForUpdate(liveMatch.externalMatchId(), 1)
                .orElse(null);
        if (now.isBefore(period.openedAt())) {
            updateOpenPeriod(event, period);
            return;
        }
        if (event == null) {
            event = openEvent(liveMatch, 1, period);
        }
        updateOpenPeriod(event, period);
        event.closeIfExpired(now);
    }

    /**
     * 첫 세트는 공식 일정, 이후 세트는 직전 세트의 피드 종료 시각으로 기간을 계산한다.
     *
     * @param liveMatch 라이브 매치 스냅샷
     * @param setNumber 배팅 기간을 계산할 세트 번호
     * @return 확정된 배팅 기간 또는 필수 시각이 없으면 null
     */
    private BettingPeriod periodOf(LiveMatchSnapshot liveMatch, int setNumber) {
        if (setNumber == 1) {
            if (liveMatch.scheduledStartAt() == null) {
                return null;
            }
            return new BettingPeriod(
                    liveMatch.scheduledStartAt()
                            .minus(bettingProperties.firstSetOpenBeforeStart()),
                    liveMatch.scheduledStartAt()
                            .plus(bettingProperties.firstSetCloseAfterStart())
            );
        }
        return liveMatch.sets().stream()
                .filter(set -> set.setNumber() == setNumber - 1)
                .map(SetSnapshot::finishedAt)
                .filter(Objects::nonNull)
                .findFirst()
                .map(finishedAt -> new BettingPeriod(
                        finishedAt,
                        finishedAt.plus(bettingProperties.nextSetBettingDuration())
                ))
                .orElse(null);
    }

    /**
     * 이벤트 생성과 복구에 공통으로 적용할 배팅 기간이다.
     *
     * @param openedAt 이벤트 오픈 시각
     * @param closesAt 이벤트 마감 시각
     */
    private record BettingPeriod(LocalDateTime openedAt, LocalDateTime closesAt) {
    }
}
