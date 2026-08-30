package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BetPointTransactionType;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.dto.BetPlacementResult;
import com.clutch.betting.dto.BettingCandidateView;
import com.clutch.betting.dto.BettingEventView;
import com.clutch.betting.dto.MyBetView;
import com.clutch.betting.dto.UserBetView;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.live.BettingLiveStateReader;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.entity.MatchTeam;
import com.clutch.lolesports.repository.MatchTeamRepository;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.lolesports.service.SetWinnerTracker;
import com.clutch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 사용자 배팅의 등록·조회·정산·환불과 운영자 승자 복구 유스케이스를 처리한다.
 *
 * <p>포인트 잔액 변경과 배팅·원장 상태 변경은 같은 트랜잭션에서 수행해 중복 정산과
 * 잔액 불일치를 방지한다.</p>
 */
@Service
@RequiredArgsConstructor
public class BettingService {

    private static final long OPERATING_FEE_PERCENT = 10L;
    private static final long PERCENTAGE_BASE = 100L;

    private final BettingEventRepository bettingEventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MatchTeamRepository matchTeamRepository;
    private final BettingLiveStateReader liveStateReader;
    private final DataCacheService dataCacheService;
    private final SetWinnerTracker setWinnerTracker;
    private final PollingScheduler pollingScheduler;
    private final Clock clock;

    /**
     * 이벤트 행을 잠근 뒤 중복·라이브 상태·포인트를 검증하고 배팅을 등록한다.
     *
     * @param userId 사용자 ID
     * @param bettingEventId 배팅 이벤트 ID
     * @param selectedExternalTeamId 선택한 외부 팀 ID
     * @param amount 배팅 포인트
     * @return 등록된 배팅과 잔여 포인트
     * @throws BettingException 이벤트·팀·사용자·포인트·중복 조건을 만족하지 못할 때
     */
    @Transactional
    public BetPlacementResult place(
            Long userId,
            Long bettingEventId,
            String selectedExternalTeamId,
            long amount
    ) {
        BettingEvent event = findEventForUpdate(bettingEventId);
        validatePlaceRequest(event, bettingEventId, userId, selectedExternalTeamId);

        UserBet userBet = UserBet.place(bettingEventId, userId, selectedExternalTeamId, amount);
        decreasePointOrThrow(userId, amount);
        saveBetAndStake(userBet);
        return BetPlacementResult.from(userBet, currentPoint(userId));
    }

    /**
     * 승자가 확정된 이벤트의 사용자 배팅을 정산하고 총 풀의 수수료를 제외해 적중자에게 배분한다.
     *
     * @param bettingEventId 정산할 배팅 이벤트 ID
     * @throws BettingException 이벤트가 없거나 결과가 아직 준비되지 않았거나 사용자를 찾을 수 없을 때
     * @throws ArithmeticException 지급액 계산 중 long 범위를 넘을 때
     */
    @Transactional
    public void settle(Long bettingEventId) {
        BettingEvent event = findEventForUpdate(bettingEventId);
        if (event.getStatus() == BettingEventStatus.SETTLED) {
            return;
        }
        settleEvent(event);
    }

    /**
     * 취소된 이벤트의 미처리 배팅을 환불하고 환불 원장을 기록한다.
     *
     * @param bettingEventId 환불할 배팅 이벤트 ID
     * @throws BettingException 이벤트가 없거나 취소 상태가 아니거나 사용자를 찾을 수 없을 때
     */
    @Transactional
    public void refund(Long bettingEventId) {
        BettingEvent event = findEventForUpdate(bettingEventId);
        validateCancelled(event);

        refundPlacedBets(findPlacedBetsForUpdate(bettingEventId));
    }

    /**
     * 자동 판정이 불가능했던 종료 이벤트에 운영자가 확인한 승자를 기록하고 즉시 정산한다.
     *
     * @param bettingEventId 복구할 배팅 이벤트 ID
     * @param winnerExternalTeamId 운영자가 외부 결과로 확인한 승리 팀 ID
     * @throws BettingException 이벤트가 없거나 아직 종료되지 않았거나 다른 승자가 이미 기록됐을 때
     */
    @Transactional
    public void recoverWinnerAndSettle(Long bettingEventId, String winnerExternalTeamId) {
        BettingEvent event = findEventForUpdate(bettingEventId);
        validateWinnerRecovery(event, winnerExternalTeamId);
        if (event.getStatus() == BettingEventStatus.SETTLED) {
            return;
        }

        event.recordWinner(winnerExternalTeamId);
        settleEvent(event);
    }

    /**
     * 매치의 최신 세트 이벤트와 현재 사용자의 참여 여부를 함께 조회한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param userId 사용자 ID
     * @return 현재 배팅 이벤트 조회 모델
     * @throws BettingException 현재 배팅 이벤트를 찾을 수 없을 때
     */
    @Transactional(readOnly = true)
    public BettingEventView getCurrentEvent(String externalMatchId, Long userId) {
        BettingEvent event = findCurrentEvent(externalMatchId);
        LocalDateTime now = now();
        UserBet userBet = findUserBet(event.getId(), userId).orElse(null);
        return BettingEventView.from(
                event,
                userBet,
                userBet == null && isBettingAvailable(event, now)
        );
    }

    /**
     * 사용자 배팅 상세와 최신 포인트 값을 조회한다.
     *
     * @param bettingEventId 배팅 이벤트 ID
     * @param userId 사용자 ID
     * @return 사용자 배팅 상세 조회 모델
     * @throws BettingException 사용자 배팅 또는 사용자를 찾을 수 없을 때
     */
    @Transactional(readOnly = true)
    public UserBetView getMyBet(Long bettingEventId, Long userId) {
        UserBet userBet = findUserBet(bettingEventId, userId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.BET_NOT_FOUND));
        return UserBetView.from(userBet, currentPoint(userId));
    }

    /**
     * 현재 사용자의 전체 배팅을 최신 등록 순서로 조회한다.
     *
     * @param userId 사용자 ID
     * @return 경기와 세트 정보가 포함된 사용자 배팅 목록
     * @throws BettingException 사용자 또는 연결된 배팅 이벤트를 찾을 수 없을 때
     */
    @Transactional(readOnly = true)
    public List<MyBetView> getMyBets(Long userId) {
        validateUser(userId);
        List<UserBet> userBets = userBetRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
        return userBets.isEmpty() ? List.of() : loadMyBetViews(userBets);
    }

    /**
     * 실제로 OPEN 상태인 배팅 이벤트와 연결된 예정·라이브 매치를 배팅 카드용으로 조회한다.
     *
     * <p>라이브 화면용 캐시와 배팅 후보 캐시는 의도적으로 분리돼 있다. 이 조회는 시작 전 20분처럼
     * 아직 {@code /api/live}에 없는 경기라도, 실제 배팅 이벤트가 생성된 뒤에만 사용자 화면에 노출한다.</p>
     *
     * @return 현재 배팅 가능한 매치의 화면 표시용 조회 모델
     */
    @Transactional(readOnly = true)
    public List<BettingCandidateView> findBettingCandidates() {
        return bettingCandidatesFor(openMatchIdsAt(now()));
    }

    // 변경 유스케이스 보조 로직

    /** 배팅 등록·정산·환불이 같은 이벤트 행을 잠근 상태에서 시작하게 한다. */
    private BettingEvent findEventForUpdate(Long bettingEventId) {
        return bettingEventRepository.findByIdForUpdate(bettingEventId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
    }

    /** 현재 미처리 상태인 사용자 배팅을 정산·환불용 잠금으로 조회한다. */
    private List<UserBet> findPlacedBetsForUpdate(Long bettingEventId) {
        return userBetRepository.findAllByBettingEventIdAndStatusForUpdate(
                bettingEventId,
                UserBetStatus.PLACED
        );
    }

    /**
     * 이벤트 시간·라이브 상태·팀·중복 조건을 순서대로 검증한다.
     *
     * @param event 배팅 대상 이벤트
     * @param bettingEventId 배팅 이벤트 ID
     * @param userId 사용자 ID
     * @param selectedExternalTeamId 선택한 외부 팀 ID
     * @throws BettingException 배팅 등록 조건을 만족하지 못하는 경우
     */
    private void validatePlaceRequest(
            BettingEvent event,
            Long bettingEventId,
            Long userId,
            String selectedExternalTeamId
    ) {
        if (!event.isOpenAt(now())) {
            throw new BettingException(BettingErrorCode.EVENT_NOT_OPEN);
        }
        if (isLiveAvailabilityRequiredAndUnavailable(event)) {
            throw new BettingException(BettingErrorCode.LIVE_DATA_UNAVAILABLE);
        }
        if (!event.hasParticipant(selectedExternalTeamId)) {
            throw new BettingException(BettingErrorCode.INVALID_TEAM);
        }
        if (userBetRepository.existsByBettingEventIdAndUserId(bettingEventId, userId)) {
            throw new BettingException(BettingErrorCode.DUPLICATE_BET);
        }
    }

    /**
     * 사용자 배팅을 저장하면서 DB 중복을 도메인 오류로 변환하고 차감 원장을 기록한다.
     *
     * @param userBet 저장할 사용자 배팅
     * @throws BettingException 동일 이벤트에 사용자 배팅이 이미 존재하는 경우
     */
    private void saveBetAndStake(UserBet userBet) {
        try {
            userBetRepository.saveAndFlush(userBet);
        } catch (DataIntegrityViolationException exception) {
            throw new BettingException(BettingErrorCode.DUPLICATE_BET);
        }
        transactionRepository.saveAndFlush(
                BetPointTransaction.stake(userBet.getId(), userBet.getAmount())
        );
    }

    /** 잠긴 종료 이벤트의 결과를 검증한 뒤 등록 배팅을 정산하고 최종 상태로 전환한다. */
    private void settleEvent(BettingEvent event) {
        validateResultReady(event);
        settlePlacedBets(event, findPlacedBetsForUpdate(event.getId()));
        event.settle();
    }

    /** 적중·실패 상태 전환과 적중자 풀 배분을 한 이벤트 단위로 처리한다. */
    private void settlePlacedBets(BettingEvent event, List<UserBet> placedBets) {
        List<UserBet> winningBets = winningBetsOf(event, placedBets);
        markLosingBets(event, placedBets);
        payoutWinningBets(placedBets, winningBets);
    }

    /** 이벤트의 확정 승리 팀을 선택한 배팅만 적중 목록으로 분리한다. */
    private List<UserBet> winningBetsOf(BettingEvent event, List<UserBet> placedBets) {
        return placedBets.stream()
                .filter(userBet -> userBet.getSelectedExternalTeamId()
                        .equals(event.getWinnerExternalTeamId()))
                .toList();
    }

    /** 적중하지 않은 등록 배팅을 실패 상태로 전환한다. */
    private void markLosingBets(BettingEvent event, List<UserBet> placedBets) {
        placedBets.stream()
                .filter(userBet -> !userBet.getSelectedExternalTeamId()
                        .equals(event.getWinnerExternalTeamId()))
                .forEach(UserBet::lose);
    }

    /** 총 풀에서 수수료를 뺀 금액을 적중자의 배팅 비율대로 지급한다. */
    private void payoutWinningBets(List<UserBet> placedBets, List<UserBet> winningBets) {
        if (winningBets.isEmpty()) {
            return;
        }
        long totalPool = sumAmounts(placedBets);
        long distributablePool = totalPool - operatingFee(totalPool);
        long totalWinningStake = sumAmounts(winningBets);
        for (UserBet winningBet : winningBets) {
            payout(winningBet, proportionalPayout(
                    distributablePool,
                    winningBet.getAmount(),
                    totalWinningStake
            ));
        }
    }

    /** 취소 이벤트에 남은 등록 배팅을 각각 원금 환불 처리한다. */
    private void refundPlacedBets(List<UserBet> placedBets) {
        placedBets.forEach(this::refundBet);
    }

    // 조회 유스케이스 보조 로직

    /** 내 배팅·이벤트·원장·팀 스냅샷을 한 번씩만 조회한 뒤 이력 조회 모델로 조합한다. */
    private List<MyBetView> loadMyBetViews(List<UserBet> userBets) {
        Map<Long, BettingEvent> eventsById = loadEventsById(userBets);
        Map<Long, List<UserBet>> eventBetsByEventId = eventBetsByEventId(eventsById.keySet());
        Map<Long, BetPointTransaction> settlementTransactionsByBetId = settlementTransactionsByBetId(
                userBets
        );
        Map<String, String> teamCodesByExternalId = teamCodesByExternalId(eventsById.values());
        return toMyBetViews(
                userBets,
                eventsById,
                eventBetsByEventId,
                settlementTransactionsByBetId,
                teamCodesByExternalId
        );
    }

    /** 배팅이 열려 있는 매치만 캐시에서 골라 후보 카드 조회 모델로 변환한다. */
    private List<BettingCandidateView> bettingCandidatesFor(Set<String> openMatchIds) {
        if (openMatchIds.isEmpty()) {
            return List.of();
        }
        return dataCacheService.getBettingMatches().stream()
                .filter(match -> openMatchIds.contains(match.matchId()))
                .filter(match -> !match.isFinished())
                .map(this::toBettingCandidateView)
                .toList();
    }

    /** 외부 매치에서 마지막으로 생성된 세트 이벤트를 현재 노출 이벤트로 조회한다. */
    private BettingEvent findCurrentEvent(String externalMatchId) {
        return bettingEventRepository.findFirstByExternalMatchIdOrderBySetNumberDesc(externalMatchId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
    }

    /** 이벤트와 사용자가 정해진 사용자 배팅을 조회한다. */
    private Optional<UserBet> findUserBet(Long bettingEventId, Long userId) {
        return userBetRepository.findByBettingEventIdAndUserId(bettingEventId, userId);
    }

    /** 사용자 배팅 목록과 미리 조회한 이벤트 맵을 API 내부 조회 모델 목록으로 조합한다. */
    private List<MyBetView> toMyBetViews(
            List<UserBet> userBets,
            Map<Long, BettingEvent> eventsById,
            Map<Long, List<UserBet>> eventBetsByEventId,
            Map<Long, BetPointTransaction> settlementTransactionsByBetId,
            Map<String, String> teamCodesByExternalId
    ) {
        return userBets.stream()
                .map(userBet -> toMyBetView(
                        userBet,
                        eventOf(userBet, eventsById),
                        eventBetsByEventId.getOrDefault(userBet.getBettingEventId(), List.of()),
                        settlementTransactionsByBetId.get(userBet.getId()),
                        teamCodesByExternalId
                ))
                .toList();
    }

    /** 배팅 이력의 팀 ID를 화면용 팀 코드로 한 번에 변환한다. */
    private Map<String, String> teamCodesByExternalId(Collection<BettingEvent> events) {
        Set<String> externalTeamIds = events.stream()
                .flatMap(event -> java.util.stream.Stream.of(
                        event.getFirstExternalTeamId(),
                        event.getSecondExternalTeamId()
                ))
                .filter(teamId -> teamId != null && !teamId.isBlank())
                .collect(Collectors.toSet());
        if (externalTeamIds.isEmpty()) {
            return Map.of();
        }
        return matchTeamRepository.findByExternalTeamIdIn(externalTeamIds).stream()
                .filter(team -> team.getTeamCode() != null && !team.getTeamCode().isBlank())
                .collect(Collectors.toMap(
                        MatchTeam::getExternalTeamId,
                        MatchTeam::getTeamCode,
                        (first, ignored) -> first
                ));
    }

    /** 이벤트별 전체 배팅을 묶어 진행 중 배팅의 예상 풀 배당 계산에 사용한다. */
    private Map<Long, List<UserBet>> eventBetsByEventId(Set<Long> eventIds) {
        return userBetRepository.findAllByBettingEventIdIn(eventIds).stream()
                .collect(Collectors.groupingBy(UserBet::getBettingEventId));
    }

    /** 내 배팅의 지급·환불 원장만 배팅 식별자로 연결한다. */
    private Map<Long, BetPointTransaction> settlementTransactionsByBetId(List<UserBet> userBets) {
        List<Long> userBetIds = userBets.stream().map(UserBet::getId).toList();
        return transactionRepository.findAllByUserBetIdIn(userBetIds).stream()
                .filter(this::isSettlementTransaction)
                .collect(Collectors.toMap(
                        BetPointTransaction::getUserBetId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    /** 지급 또는 환불 원장만 내 배팅 정산 표시에 사용한다. */
    private boolean isSettlementTransaction(BetPointTransaction transaction) {
        return transaction.getTransactionType() == BetPointTransactionType.PAYOUT
                || transaction.getTransactionType() == BetPointTransactionType.REFUND;
    }

    /** 배팅 상태·원장·현재 풀을 조합해 정산 금액, 순손익, 배당률을 만든다. */
    private MyBetView toMyBetView(
            UserBet userBet,
            BettingEvent event,
            List<UserBet> eventBets,
            BetPointTransaction settlementTransaction,
            Map<String, String> teamCodesByExternalId
    ) {
        String firstTeamCode = teamCodesByExternalId.get(event.getFirstExternalTeamId());
        String secondTeamCode = teamCodesByExternalId.get(event.getSecondExternalTeamId());
        if (userBet.getStatus() == UserBetStatus.PLACED) {
            return MyBetView.from(
                    userBet,
                    event,
                    firstTeamCode,
                    secondTeamCode,
                    null,
                    null,
                    expectedPayoutMultiplier(userBet, eventBets),
                    false
            );
        }
        if (userBet.getStatus() == UserBetStatus.LOST) {
            return MyBetView.from(
                    userBet,
                    event,
                    firstTeamCode,
                    secondTeamCode,
                    0L,
                    -userBet.getAmount(),
                    BigDecimal.ZERO,
                    true
            );
        }
        if (settlementTransaction == null) {
            return MyBetView.from(
                    userBet,
                    event,
                    firstTeamCode,
                    secondTeamCode,
                    null,
                    null,
                    null,
                    false
            );
        }

        long settlementPoint = settlementTransaction.getPointDelta();
        return MyBetView.from(
                userBet,
                event,
                firstTeamCode,
                secondTeamCode,
                settlementPoint,
                Math.subtractExact(settlementPoint, userBet.getAmount()),
                userBet.getStatus() == UserBetStatus.WON
                        ? payoutMultiplier(settlementPoint, userBet.getAmount())
                        : null,
                userBet.getStatus() == UserBetStatus.WON
        );
    }

    /** 현재 풀에서 선택 팀이 이긴다고 가정한 개별 배팅의 예상 배당률을 계산한다. */
    private BigDecimal expectedPayoutMultiplier(UserBet userBet, List<UserBet> eventBets) {
        long totalPool = sumAmounts(eventBets);
        long selectedTeamPool = sumAmounts(eventBets.stream()
                .filter(candidate -> candidate.getSelectedExternalTeamId()
                        .equals(userBet.getSelectedExternalTeamId()))
                .toList());
        long expectedPayout = proportionalPayout(
                totalPool - operatingFee(totalPool),
                userBet.getAmount(),
                selectedTeamPool
        );
        return payoutMultiplier(expectedPayout, userBet.getAmount());
    }

    /** 실제 또는 예상 지급액을 배팅 원금 기준 두 자리 배당률로 변환한다. */
    private BigDecimal payoutMultiplier(long payoutPoint, long amount) {
        return BigDecimal.valueOf(payoutPoint)
                .divide(BigDecimal.valueOf(amount), 2, RoundingMode.HALF_UP);
    }

    /** 현재 시각에 실제로 배팅을 받고 있는 이벤트의 외부 매치 ID만 모은다. */
    private Set<String> openMatchIdsAt(LocalDateTime now) {
        return bettingEventRepository.findAllByStatus(BettingEventStatus.OPEN).stream()
                .filter(event -> event.isOpenAt(now))
                .map(BettingEvent::getExternalMatchId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 이벤트의 시간 조건과 후속 세트의 최신 라이브 상태를 모두 만족하는지 확인한다. */
    private boolean isBettingAvailable(BettingEvent event, LocalDateTime now) {
        return event.isOpenAt(now) && !isLiveAvailabilityRequiredAndUnavailable(event);
    }

    /** 정산하려면 이벤트가 종료되고 승자가 확정돼야 한다. */
    private void validateResultReady(BettingEvent event) {
        if (event.getStatus() != BettingEventStatus.CLOSED
                || event.getWinnerExternalTeamId() == null) {
            throw new BettingException(BettingErrorCode.RESULT_NOT_READY);
        }
    }

    /** 이미 확정된 결과는 같은 승자만 멱등하게 허용한다. */
    private void validateWinnerRecovery(BettingEvent event, String winnerExternalTeamId) {
        if (event.getWinnerExternalTeamId() != null
                && !event.getWinnerExternalTeamId().equals(winnerExternalTeamId)) {
            throw new BettingException(BettingErrorCode.WINNER_ALREADY_DECIDED);
        }
        if (event.getStatus() != BettingEventStatus.CLOSED
                && event.getStatus() != BettingEventStatus.SETTLED) {
            throw new BettingException(BettingErrorCode.RESULT_NOT_READY);
        }
    }

    /** 모든 배팅금을 더해 해당 이벤트의 총 풀을 계산한다. */
    private long sumAmounts(List<UserBet> userBets) {
        long total = 0L;
        for (UserBet userBet : userBets) {
            total = Math.addExact(total, userBet.getAmount());
        }
        return total;
    }

    /** 총 풀의 10%를 소수점 버림해 운영 수수료를 계산한다. */
    private long operatingFee(long totalPool) {
        return totalPool / PERCENTAGE_BASE * OPERATING_FEE_PERCENT
                + totalPool % PERCENTAGE_BASE * OPERATING_FEE_PERCENT / PERCENTAGE_BASE;
    }

    /** 적중 배팅금 비율로 배당 풀을 나누고 소수점 버림으로 생긴 잔여는 운영 수수료에 포함한다. */
    private long proportionalPayout(long distributablePool, long amount, long totalWinningStake) {
        return BigInteger.valueOf(distributablePool)
                .multiply(BigInteger.valueOf(amount))
                .divide(BigInteger.valueOf(totalWinningStake))
                .longValueExact();
    }

    /** 적중 배팅 한 건에 계산된 포인트를 지급하고 원장을 기록한다. */
    private void payout(UserBet userBet, long payoutPoint) {
        increasePoint(userBet.getUserId(), payoutPoint);
        userBet.win();
        transactionRepository.save(
                BetPointTransaction.payout(userBet.getId(), payoutPoint)
        );
    }

    /** 취소 이벤트의 사용자 배팅을 원금만큼 환불한다. */
    private void refundBet(UserBet userBet) {
        increasePoint(userBet.getUserId(), userBet.getAmount());
        userBet.refund();
        transactionRepository.save(
                BetPointTransaction.refund(userBet.getId(), userBet.getAmount())
        );
    }

    /** 사용자 포인트 지급 또는 환불 결과가 한 건이 아니면 사용자 없음 오류로 처리한다. */
    private void increasePoint(Long userId, long amount) {
        if (userRepository.increasePoint(userId, amount) != 1) {
            throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
        }
    }

    /** 환불하려면 이벤트가 취소 상태여야 한다. */
    private void validateCancelled(BettingEvent event) {
        if (event.getStatus() != BettingEventStatus.CANCELLED) {
            throw new BettingException(BettingErrorCode.EVENT_NOT_CANCELLED);
        }
    }

    /** lolesports 캐시를 배팅 후보 API가 소유한 조회 모델로 변환한다. */
    private BettingCandidateView toBettingCandidateView(DataCacheService.LiveMatch match) {
        return new BettingCandidateView(
                match.matchId(),
                match.leagueName(),
                match.blockName(),
                match.startTime(),
                match.bestOf(),
                match.isFinished(),
                match.winnerTeamId(),
                teamsOf(match.teams()),
                gamesOf(match),
                match.activeGameId()
        );
    }

    /** lolesports 팀 목록에서 카드 응답에 필요한 팀 필드만 추출한다. */
    private List<BettingCandidateView.Team> teamsOf(List<ScheduleResponse.Team> teams) {
        if (teams == null) {
            return List.of();
        }
        return teams.stream()
                .map(team -> new BettingCandidateView.Team(
                        team.id(),
                        team.name(),
                        team.code(),
                        team.image(),
                        team.result() != null ? team.result().outcome() : null,
                        team.result() != null ? team.result().gameWins() : null,
                        team.record() != null ? team.record().wins() : null,
                        team.record() != null ? team.record().losses() : null
                ))
                .toList();
    }

    /** 캐시·승자 추적기·폴링 상태를 결합해 세트별 카드 정보를 만든다. */
    private List<BettingCandidateView.Game> gamesOf(DataCacheService.LiveMatch match) {
        List<EventDetailsResponse.Game> games = match.games();
        if (games == null) {
            return List.of();
        }
        return games.stream()
                .map(game -> new BettingCandidateView.Game(
                        game.id(),
                        game.number(),
                        game.state(),
                        dataCacheService.isFeedFinished(game.id()),
                        setWinnerTracker.winnerOf(match.matchId(), game.id()),
                        pollingScheduler.isStatsUnavailable(game.id())
                ))
                .toList();
    }

    /**
     * 첫 세트는 실제 livestats 첫 프레임을 동기화 서비스가 받는 순간 이벤트 자체를 닫는다.
     * 시작 전에는 예정 경기 캐시가 잠깐 비어도 OPEN 이벤트가 배팅을 막으면 안 된다.
     */
    private boolean isLiveAvailabilityRequiredAndUnavailable(BettingEvent event) {
        if (event.getSetNumber() == 1) {
            return false;
        }
        return !liveStateReader.isAcceptingBets(
                event.getExternalMatchId(),
                event.getExternalGameId(),
                event.getSetNumber()
        );
    }

    /** 포인트 조회가 가능한 사용자만 내 배팅 이력을 조회하게 한다. */
    private void validateUser(Long userId) {
        if (userRepository.findPointById(userId).isEmpty()) {
            throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
        }
    }

    /** 목록에 포함된 배팅 이벤트를 한 번에 조회해 사용자 배팅과 연결한다. */
    private Map<Long, BettingEvent> loadEventsById(List<UserBet> userBets) {
        List<Long> eventIds = userBets.stream()
                .map(UserBet::getBettingEventId)
                .distinct()
                .toList();
        return bettingEventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(BettingEvent::getId, Function.identity()));
    }

    /**
     * 주입된 시계를 UTC 로컬 시각으로 변환한다.
     *
     * @return 현재 UTC 로컬 시각
     */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** 사용자 배팅이 참조하는 이벤트가 실제로 함께 조회됐는지 확인한다. */
    private BettingEvent eventOf(UserBet userBet, Map<Long, BettingEvent> eventsById) {
        BettingEvent event = eventsById.get(userBet.getBettingEventId());
        if (event == null) {
            throw new BettingException(BettingErrorCode.EVENT_NOT_FOUND);
        }
        return event;
    }

    /**
     * 조건부 갱신으로 포인트를 원자적으로 차감하고 실패 원인을 구분한다.
     *
     * @param userId 사용자 ID
     * @param amount 차감할 포인트
     * @throws BettingException 사용자가 없거나 보유 포인트가 부족할 때
     */
    private void decreasePointOrThrow(Long userId, long amount) {
        int updated = userRepository.decreasePointIfEnough(userId, amount);
        if (updated == 1) {
            return;
        }
        if (!userRepository.existsById(userId)) {
            throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
        }
        throw new BettingException(BettingErrorCode.INSUFFICIENT_POINT);
    }

    /**
     * 사용자 엔티티를 적재하지 않고 최신 포인트만 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자의 현재 포인트
     * @throws BettingException 사용자를 찾을 수 없는 경우
     */
    private long currentPoint(Long userId) {
        return userRepository.findPointById(userId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.USER_NOT_FOUND));
    }
}
