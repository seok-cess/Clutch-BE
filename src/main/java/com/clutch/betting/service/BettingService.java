package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.dto.BetPlacementResult;
import com.clutch.betting.dto.BettingEventView;
import com.clutch.betting.dto.MyBetView;
import com.clutch.betting.dto.UserBetView;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.live.LiveBettingDataProvider;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 사용자 배팅 등록과 현재 이벤트·내 배팅 조회 유스케이스를 제공한다. */
@Service
@RequiredArgsConstructor
public class BettingService {

    private final BettingEventRepository bettingEventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final LiveBettingDataProvider liveBettingDataProvider;
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
        BettingEvent event = bettingEventRepository.findByIdForUpdate(bettingEventId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        validatePlaceRequest(event, bettingEventId, userId, selectedExternalTeamId);

        UserBet userBet = UserBet.place(
                bettingEventId,
                userId,
                selectedExternalTeamId,
                amount
        );
        decreasePointOrThrow(userId, amount);
        saveBetAndStake(userBet);
        return toPlacementResult(userBet, currentPoint(userId));
    }

    /**
     * 이벤트 시간·라이브 상태·팀·중복 조건을 순서대로 검증한다.
     *
     * @param event 배팅 대상 이벤트
     * @param bettingEventId 배팅 이벤트 ID
     * @param userId 배팅 사용자 ID
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

    /**
     * 저장된 배팅과 최신 포인트를 등록 응답 모델로 변환한다.
     *
     * @param userBet 저장된 사용자 배팅
     * @param currentPoint 배팅 차감 후 사용자 포인트
     * @return 배팅 등록 결과
     */
    private BetPlacementResult toPlacementResult(UserBet userBet, long currentPoint) {
        return new BetPlacementResult(
                userBet.getId(),
                userBet.getUserId(),
                userBet.getBettingEventId(),
                userBet.getSelectedExternalTeamId(),
                userBet.getAmount(),
                userBet.getStatus(),
                currentPoint
        );
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
        BettingEvent event = bettingEventRepository
                .findFirstByExternalMatchIdOrderBySetNumberDesc(externalMatchId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        LocalDateTime now = now();
        UserBet userBet = userBetRepository
                .findByBettingEventIdAndUserId(event.getId(), userId)
                .orElse(null);
        boolean liveAvailable = isBettingAvailable(event, now);
        return new BettingEventView(
                event.getId(),
                event.getExternalMatchId(),
                event.getExternalGameId(),
                event.getSetNumber(),
                event.getFirstExternalTeamId(),
                event.getSecondExternalTeamId(),
                event.getStatus(),
                userBet == null && liveAvailable,
                toSummary(userBet)
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
        UserBet userBet = userBetRepository
                .findByBettingEventIdAndUserId(bettingEventId, userId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.BET_NOT_FOUND));
        return new UserBetView(
                userBet.getId(),
                userBet.getUserId(),
                bettingEventId,
                userBet.getSelectedExternalTeamId(),
                userBet.getAmount(),
                userBet.getStatus(),
                currentPoint(userId)
        );
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
        Map<Long, BettingEvent> eventsById = loadEventsById(userBets);

        return userBets.stream()
                .map(userBet -> toMyBetView(userBet, eventsById))
                .toList();
    }

    private boolean isBettingAvailable(BettingEvent event, LocalDateTime now) {
        return event.isOpenAt(now) && !isLiveAvailabilityRequiredAndUnavailable(event);
    }

    /**
     * 첫 세트는 실제 livestats 첫 프레임을 동기화 서비스가 받는 순간 이벤트 자체를 닫는다.
     * 시작 전에는 예정 경기 캐시가 잠깐 비어도 OPEN 이벤트가 배팅을 막으면 안 된다.
     */
    private boolean isLiveAvailabilityRequiredAndUnavailable(BettingEvent event) {
        if (event.getSetNumber() == 1) {
            return false;
        }
        return !liveBettingDataProvider.isAcceptingBets(
                event.getExternalMatchId(),
                event.getExternalGameId(),
                event.getSetNumber()
        );
    }

    private void validateUser(Long userId) {
        if (userRepository.findPointById(userId).isEmpty()) {
            throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
        }
    }

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

    /**
     * 사용자 배팅이 있을 때만 이벤트 응답용 요약을 생성한다.
     *
     * @param userBet 사용자 배팅 또는 미등록이면 null
     * @return 사용자 배팅 요약 또는 미등록이면 null
     */
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

    /** 사용자 배팅과 연결 이벤트를 내 배팅 목록 항목으로 변환한다. */
    private MyBetView toMyBetView(UserBet userBet, Map<Long, BettingEvent> eventsById) {
        BettingEvent event = eventsById.get(userBet.getBettingEventId());
        if (event == null) {
            throw new BettingException(BettingErrorCode.EVENT_NOT_FOUND);
        }
        return new MyBetView(
                userBet.getId(),
                userBet.getBettingEventId(),
                event.getExternalMatchId(),
                event.getExternalGameId(),
                event.getSetNumber(),
                event.getFirstExternalTeamId(),
                event.getSecondExternalTeamId(),
                userBet.getSelectedExternalTeamId(),
                userBet.getAmount(),
                userBet.getStatus(),
                event.getStatus(),
                userBet.getCreatedAt()
        );
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
