package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.dto.BetPlacementResult;
import com.clutch.betting.dto.BettingEventView;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 사용자 배팅 등록과 현재 이벤트·내 배팅 조회 유스케이스를 제공한다. */
@Service
@RequiredArgsConstructor
public class BettingService {

    private static final List<BettingEventStatus> CURRENT_STATUSES = List.of(
            BettingEventStatus.OPEN,
            BettingEventStatus.CLOSED
    );

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
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!event.isOpenAt(now)) {
            throw new BettingException(BettingErrorCode.EVENT_NOT_OPEN);
        }
        if (!liveBettingDataProvider.isAcceptingBets(
                event.getExternalMatchId(),
                event.getExternalGameId(),
                event.getSetNumber()
        )) {
            throw new BettingException(BettingErrorCode.LIVE_DATA_UNAVAILABLE);
        }
        if (!event.hasParticipant(selectedExternalTeamId)) {
            throw new BettingException(BettingErrorCode.INVALID_TEAM);
        }
        if (userBetRepository.existsByBettingEventIdAndUserId(bettingEventId, userId)) {
            throw new BettingException(BettingErrorCode.DUPLICATE_BET);
        }

        UserBet userBet = UserBet.place(
                bettingEventId,
                userId,
                selectedExternalTeamId,
                amount
        );
        decreasePoint(userId, amount);
        try {
            userBetRepository.saveAndFlush(userBet);
            transactionRepository.saveAndFlush(BetPointTransaction.stake(userBet.getId(), amount));
        } catch (DataIntegrityViolationException exception) {
            throw new BettingException(BettingErrorCode.DUPLICATE_BET);
        }

        return new BetPlacementResult(
                userBet.getId(),
                userBet.getUserId(),
                bettingEventId,
                selectedExternalTeamId,
                amount,
                userBet.getStatus(),
                userRepository.findPointById(userId)
                        .orElseThrow(() -> new BettingException(BettingErrorCode.USER_NOT_FOUND))
        );
    }

    /**
     * 매치의 최신 진행 이벤트와 현재 사용자의 참여 여부를 함께 조회한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param userId 사용자 ID
     * @return 현재 배팅 이벤트 조회 모델
     * @throws BettingException 현재 배팅 이벤트를 찾을 수 없을 때
     */
    @Transactional(readOnly = true)
    public BettingEventView getCurrentEvent(String externalMatchId, Long userId) {
        BettingEvent event = bettingEventRepository
                .findFirstByExternalMatchIdAndStatusInOrderBySetNumberDesc(
                        externalMatchId,
                        CURRENT_STATUSES
                )
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        LocalDateTime now = now();
        UserBet userBet = userBetRepository
                .findByBettingEventIdAndUserId(event.getId(), userId)
                .orElse(null);
        boolean liveAvailable = liveBettingDataProvider.isAcceptingBets(
                event.getExternalMatchId(),
                event.getExternalGameId(),
                event.getSetNumber()
        );
        return new BettingEventView(
                event.getId(),
                event.getExternalMatchId(),
                event.getExternalGameId(),
                event.getSetNumber(),
                event.getFirstExternalTeamId(),
                event.getSecondExternalTeamId(),
                event.getStatus(),
                event.getClosesAt(),
                remainingSeconds(event.getClosesAt(), now),
                userBet == null && event.isOpenAt(now) && liveAvailable,
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
        long currentPoint = userRepository.findPointById(userId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.USER_NOT_FOUND));
        return new UserBetView(
                userBet.getId(),
                userBet.getUserId(),
                bettingEventId,
                userBet.getSelectedExternalTeamId(),
                userBet.getAmount(),
                userBet.getStatus(),
                currentPoint
        );
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
     * 미정 마감은 -1, 마감 이후는 0으로 표현해 남은 초를 계산한다.
     *
     * @param closesAt 배팅 마감 시각
     * @param now 판단 기준 시각
     * @return 마감까지 남은 초, 마감 미정이면 -1
     */
    private long remainingSeconds(LocalDateTime closesAt, LocalDateTime now) {
        if (closesAt == null) {
            return -1L;
        }
        return Math.max(0L, Duration.between(now, closesAt).toSeconds());
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

    /**
     * 조건부 갱신으로 포인트를 원자적으로 차감하고 실패 원인을 구분한다.
     *
     * @param userId 사용자 ID
     * @param amount 차감할 포인트
     * @throws BettingException 사용자가 없거나 보유 포인트가 부족할 때
     */
    private void decreasePoint(Long userId, long amount) {
        int updated = userRepository.decreasePointIfEnough(userId, amount);
        if (updated == 1) {
            return;
        }
        if (!userRepository.existsById(userId)) {
            throw new BettingException(BettingErrorCode.USER_NOT_FOUND);
        }
        throw new BettingException(BettingErrorCode.INSUFFICIENT_POINT);
    }
}
