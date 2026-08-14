package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.integration.lolesports.LiveBettingCache;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 배팅 가능 상태를 검증하고 포인트 차감과 사용자 배팅 등록을 한 트랜잭션으로 처리한다. */
@Service
public class BetPlacementService {

    private final BettingEventRepository bettingEventRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final LiveBettingCache liveBettingCache;
    private final Clock clock;

    /**
     * 운영 환경에서 UTC 시스템 시계를 사용하는 등록 서비스를 구성한다.
     *
     * @param bettingEventRepository 배팅 이벤트 저장소
     * @param userBetRepository 사용자 배팅 저장소
     * @param transactionRepository 배팅 포인트 거래 저장소
     * @param userRepository 사용자 저장소
     * @param liveBettingCache 라이브 배팅 캐시 포트
     */
    @Autowired
    public BetPlacementService(
            BettingEventRepository bettingEventRepository,
            UserBetRepository userBetRepository,
            BetPointTransactionRepository transactionRepository,
            UserRepository userRepository,
            LiveBettingCache liveBettingCache
    ) {
        this(
                bettingEventRepository,
                userBetRepository,
                transactionRepository,
                userRepository,
                liveBettingCache,
                Clock.systemUTC()
        );
    }

    /**
     * 테스트에서 결정적인 현재 시각을 주입할 수 있도록 서비스를 구성한다.
     *
     * @param bettingEventRepository 배팅 이벤트 저장소
     * @param userBetRepository 사용자 배팅 저장소
     * @param transactionRepository 배팅 포인트 거래 저장소
     * @param userRepository 사용자 저장소
     * @param liveBettingCache 라이브 배팅 캐시 포트
     * @param clock 현재 시각을 제공할 시계
     */
    BetPlacementService(
            BettingEventRepository bettingEventRepository,
            UserBetRepository userBetRepository,
            BetPointTransactionRepository transactionRepository,
            UserRepository userRepository,
            LiveBettingCache liveBettingCache,
            Clock clock
    ) {
        this.bettingEventRepository = bettingEventRepository;
        this.userBetRepository = userBetRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.liveBettingCache = liveBettingCache;
        this.clock = clock;
    }

    /**
     * 이벤트 행을 잠근 뒤 중복·라이브 상태·포인트를 검증하고 배팅을 등록한다.
     *
     * @param userId 사용자 ID
     * @param bettingEventId 배팅 이벤트 ID
     * @param selectedExternalTeamId 선택한 외부 팀 ID
     * @param amount 배팅 포인트
     * @return 등록된 배팅과 잔여 포인트
     * @throws BettingException 이벤트·팀·사용자·포인트·중복 조건을 만족하지 못할 때
     * @throws IllegalArgumentException 배팅 금액 또는 필수 값이 도메인 조건을 만족하지 못할 때
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
        if (!liveBettingCache.isAcceptingBets(
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
                bettingEventId,
                selectedExternalTeamId,
                amount,
                userBet.getStatus(),
                userRepository.findPointById(userId)
                        .orElseThrow(() -> new BettingException(BettingErrorCode.USER_NOT_FOUND))
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
