package com.clutch.betting.service;

import com.clutch.betting.config.BettingProperties;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.live.LiveBettingDataProvider.LiveMatchSnapshot;
import com.clutch.betting.live.LiveBettingDataProvider.SetSnapshot;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 한 라이브 매치의 세트 스냅샷을 배팅 이벤트 생명주기에 반영한다. */
@Service
@RequiredArgsConstructor
public class BettingEventSynchronizationService {

    private final BettingEventRepository bettingEventRepository;
    private final EsportsMatchRepository esportsMatchRepository;
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
     * 라이브 목록 밖에서 재조회한 공식 결과를 종료 이벤트에 반영한다.
     *
     * <p>이 메서드는 승자를 추정하지 않는다. 호출자는 공식 {@code gameWins} 증가분으로
     * 확정한 {@code gameId → teamId} 결과만 전달해야 한다. 정산은 기존 스케줄러가
     * {@code CLOSED + winner} 이벤트를 발견해 수행한다.</p>
     *
     * @param externalMatchId 결과를 재조회한 매치의 외부 ID
     * @param winnerByGameId 확정된 외부 게임 ID별 승리 팀 외부 ID
     */
    @Transactional
    public void synchronizeConfirmedWinners(
            String externalMatchId,
            Map<String, String> winnerByGameId
    ) {
        if (externalMatchId == null || externalMatchId.isBlank()
                || winnerByGameId == null || winnerByGameId.isEmpty()) {
            return;
        }
        for (BettingEvent event : bettingEventRepository
                .findAllClosedWithoutWinnerForUpdate(externalMatchId)) {
            String gameId = event.getExternalGameId();
            String winnerTeamId = gameId == null ? null : winnerByGameId.get(gameId);
            if (winnerTeamId != null) {
                event.recordWinner(winnerTeamId);
            }
        }
    }

    /**
     * 종료 프레임이 적재된 세트의 이벤트를 결과 재조회 전에 닫는다.
     *
     * <p>라이브 캐시가 먼저 정리되면 일반 동기화가 {@code gameState=finished}를
     * 관측하지 못할 수 있다. 이 경우에도 DB의 {@code ended_at}은 종료 근거이므로,
     * 연결된 이벤트만 닫아 결과 조정·정산 대상으로 넘긴다.</p>
     *
     * @param externalMatchId 종료 세트를 포함한 외부 매치 ID
     */
    @Transactional
    public void closeFinishedEventsForReconciliation(String externalMatchId) {
        if (externalMatchId == null || externalMatchId.isBlank()) {
            return;
        }
        bettingEventRepository.findAllUnsettledFinishedGameEventsForUpdate(externalMatchId)
                .forEach(BettingEvent::close);
    }

    /**
     * 공식 최종 스코어가 확인된 뒤, 실제로 열리지 않은 이후 세트 이벤트를 취소한다.
     *
     * @param externalMatchId 종료가 확인된 매치의 외부 ID
     * @param lastFinishedSetNumber 실제로 끝난 마지막 세트 번호
     */
    @Transactional
    public void cancelFutureEventsAfterConfirmedMatch(
            String externalMatchId,
            int lastFinishedSetNumber
    ) {
        if (externalMatchId == null || externalMatchId.isBlank() || lastFinishedSetNumber < 1) {
            return;
        }
        bettingEventRepository.findAllFutureEventsForUpdate(externalMatchId, lastFinishedSetNumber)
                .forEach(BettingEvent::cancel);
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
        BettingPeriod period = periodOf(liveMatch, set);
        if (period == null) {
            synchronizeWithoutPeriod(liveMatch, set, event, now);
            return;
        }
        if (now.isBefore(period.openedAt())) {
            updateOpenPeriod(event, period, set.setNumber() > 1);
            return;
        }

        if (event == null) {
            event = openEvent(liveMatch, set.setNumber(), period);
        }
        updateOpenPeriod(event, period, set.setNumber() > 1);
        attachGameIfPresent(event, set.externalGameId());
        closeNextSetAfterStartGracePeriod(event, set, now);
        if (set.finished()) {
            finishEvent(event, set);
            openNextEventAfterFinishedSet(liveMatch, set, now);
        }
    }

    /**
     * 기간을 복구할 수 없으면 진행 이벤트의 게임 연결은 유지하고 확인된 종료 결과만 반영한다.
     * 종료 피드 시각까지 사라진 경우에는 공식 완료 상태를 관측한 현재 시각을 다음 세트 오픈 시각으로 쓴다.
     *
     * @param liveMatch 세트가 속한 라이브 매치
     * @param set 동기화할 세트
     * @param existingEvent 기존 이벤트 또는 없으면 null
     * @param now 종료 상태를 관측한 현재 시각
     */
    private void synchronizeWithoutPeriod(
            LiveMatchSnapshot liveMatch,
            SetSnapshot set,
            BettingEvent existingEvent,
            LocalDateTime now
    ) {
        if (existingEvent != null) {
            attachGameIfPresent(existingEvent, set.externalGameId());
            closeNextSetAfterStartGracePeriod(existingEvent, set, now);
        }
        if (!set.finished()) {
            return;
        }
        if (existingEvent != null) {
            finishEvent(existingEvent, set);
        }
        openNextEventAfterFinishedSet(liveMatch, set, now);
    }

    /**
     * 열린 이벤트에만 최신 오픈·마감 기간을 반영한다.
     *
     * <p>다음 세트는 실제 시작 프레임을 한 번 관측해 안전 마감보다 이른 시각으로
     * 조정한 뒤, 일시적인 캐시 공백 때문에 20분 안전 마감으로 다시 연장돼서는 안 된다.</p>
     *
     * @param event 갱신할 이벤트 또는 없으면 null
     * @param period 최신 배팅 기간
     * @param preserveEarlierDeadline 이미 더 이른 마감이 있으면 유지할지 여부
     */
    private void updateOpenPeriod(
            BettingEvent event,
            BettingPeriod period,
            boolean preserveEarlierDeadline
    ) {
        if (event != null && event.getStatus() == BettingEventStatus.OPEN) {
            LocalDateTime closesAt = preserveEarlierDeadline
                    && event.getClosesAt().isBefore(period.closesAt())
                    ? event.getClosesAt()
                    : period.closesAt();
            event.definePeriod(period.openedAt(), closesAt);
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
     * 두 번째 세트부터는 실제 게임이 시작된 시각을 기준으로 마감 시각을 다시 잡는다.
     *
     * <p>세트 사이에는 종료 프레임 시각부터 열어 두되, 실제 게임의 첫 프레임이 들어오면
     * 1분의 공통 유예 시간 뒤에는 반드시 마감한다. 라이브 캐시가 늦게 복구돼도 이미
     * 유예 시간이 지났다면 즉시 닫는다.</p>
     */
    private void closeNextSetAfterStartGracePeriod(
            BettingEvent event,
            SetSnapshot set,
            LocalDateTime now
    ) {
        if (event == null || event.getStatus() != BettingEventStatus.OPEN) {
            return;
        }
        if (set.setNumber() == 1) {
            event.closeIfExpired(now);
            return;
        }
        if (set.startedAt() == null) {
            event.closeIfExpired(now);
            return;
        }

        LocalDateTime closesAt = set.startedAt()
                .plus(bettingProperties.firstSetCloseAfterStart());
        if (!closesAt.isAfter(event.getOpenedAt())) {
            event.close();
            return;
        }
        event.definePeriod(event.getOpenedAt(), closesAt);
        event.closeIfExpired(now);
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
     * 세트 종료 직후부터 다음 세트 배팅 이벤트를 선개설한다.
     * 종료 피드 시각이 없으면 공식 완료 상태를 관측한 시각을 보수적인 대체 기준으로 사용한다.
     *
     * @param liveMatch 종료 세트가 속한 매치 스냅샷
     * @param finishedSet 종료된 세트 스냅샷
     * @param observedAt 종료 상태를 관측한 현재 시각
     */
    private void openNextEventAfterFinishedSet(
            LiveMatchSnapshot liveMatch,
            SetSnapshot finishedSet,
            LocalDateTime observedAt
    ) {
        if (liveMatch.matchFinished() || isLastPossibleSet(liveMatch, finishedSet)) {
            return;
        }
        LocalDateTime openedAt = finishedSet.finishedAt() != null
                ? finishedSet.finishedAt()
                : observedAt;
        openNextEventIfMissing(
                liveMatch,
                finishedSet.setNumber() + 1,
                new BettingPeriod(
                        openedAt,
                        openedAt.plus(bettingProperties.nextSetBettingDuration())
                )
        );
    }

    /** 다전제의 최종 가능 세트가 끝나면 지연된 공식 최종 응답과 무관하게 후속 세트를 열지 않는다. */
    private boolean isLastPossibleSet(LiveMatchSnapshot liveMatch, SetSnapshot finishedSet) {
        int bestOf = maximumKnownBestOf(liveMatch);
        return bestOf > 0 && finishedSet.setNumber() >= bestOf;
    }

    /**
     * 최종 세트 생성 직전에는 캐시의 다전제 수와 DB의 확정 값을 함께 확인한다.
     *
     * <p>라이브 캐시는 60초 주기라 고배속 재생이나 일시적인 상세 API 실패 때 오래된 값이
     * 남을 수 있다. 이미 적재한 {@code esports_match.best_of}까지 대조해 더 큰 유효 값을
     * 사용하면 BO3의 4세트·BO5의 6세트 같은 잘못된 이벤트 생성을 막을 수 있다.</p>
     */
    private int maximumKnownBestOf(LiveMatchSnapshot liveMatch) {
        int cachedBestOf = liveMatch.bestOf() != null && liveMatch.bestOf() > 0
                ? liveMatch.bestOf()
                : 0;
        int persistedBestOf = esportsMatchRepository
                .findByExternalMatchId(liveMatch.externalMatchId())
                .map(match -> match.getBestOf())
                .filter(bestOf -> bestOf != null && bestOf > 0)
                .orElse(0);
        return Math.max(cachedBestOf, persistedBestOf);
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
        BettingPeriod period = firstSetPeriodOf(liveMatch);
        if (period == null) {
            return;
        }
        BettingEvent event = bettingEventRepository
                .findByExternalMatchIdAndSetNumberForUpdate(liveMatch.externalMatchId(), 1)
                .orElse(null);
        if (now.isBefore(period.openedAt())) {
            updateOpenPeriod(event, period, false);
            return;
        }
        if (event == null) {
            event = openEvent(liveMatch, 1, period);
        }
        updateOpenPeriod(event, period, false);
        event.closeIfExpired(now);
    }

    /**
     * 첫 세트는 공식 일정, 이후 세트는 직전 세트 종료 시각부터 실제 시작 1분 후까지의 기간을 계산한다.
     * 실제 시작 시각을 아직 알 수 없을 때만 기존 20분 값을 안전 마감 시각으로 사용한다.
     *
     * @param liveMatch 라이브 매치 스냅샷
     * @param set 배팅 기간을 계산할 세트
     * @return 확정된 배팅 기간 또는 필수 시각이 없으면 null
     */
    private BettingPeriod periodOf(LiveMatchSnapshot liveMatch, SetSnapshot set) {
        int setNumber = set.setNumber();
        if (setNumber == 1) {
            return firstSetPeriodOf(liveMatch);
        }
        return liveMatch.sets().stream()
                .filter(previousSet -> previousSet.setNumber() == setNumber - 1)
                .map(SetSnapshot::finishedAt)
                .filter(Objects::nonNull)
                .findFirst()
                .map(finishedAt -> new BettingPeriod(
                        finishedAt,
                        set.startedAt() == null
                                ? finishedAt.plus(bettingProperties.nextSetBettingDuration())
                                : set.startedAt().plus(bettingProperties.firstSetCloseAfterStart())
                ))
                .orElse(null);
    }

    /** 공식 일정으로 첫 세트의 오픈·마감 기간을 계산한다. */
    private BettingPeriod firstSetPeriodOf(LiveMatchSnapshot liveMatch) {
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

    /**
     * 이벤트 생성과 복구에 공통으로 적용할 배팅 기간이다.
     *
     * @param openedAt 이벤트 오픈 시각
     * @param closesAt 이벤트 마감 시각
     */
    private record BettingPeriod(LocalDateTime openedAt, LocalDateTime closesAt) {
    }
}
