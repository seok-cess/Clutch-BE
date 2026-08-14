package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.integration.lolesports.LiveBettingCache;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 배팅 이벤트와 사용자 배팅을 응답 전용 뷰로 조회한다. */
@Service
@Transactional(readOnly = true)
public class BetQueryService {

    private static final List<BettingEventStatus> CURRENT_STATUSES = List.of(
            BettingEventStatus.OPEN,
            BettingEventStatus.CLOSED
    );

    private final BettingEventRepository eventRepository;
    private final UserBetRepository userBetRepository;
    private final UserRepository userRepository;
    private final LiveBettingCache liveBettingCache;
    private final Clock clock;

    /**
     * 운영 환경에서 UTC 시스템 시계를 사용하는 조회 서비스를 구성한다.
     *
     * @param eventRepository 배팅 이벤트 저장소
     * @param userBetRepository 사용자 배팅 저장소
     * @param userRepository 사용자 저장소
     * @param liveBettingCache 라이브 배팅 캐시 포트
     */
    @Autowired
    public BetQueryService(
            BettingEventRepository eventRepository,
            UserBetRepository userBetRepository,
            UserRepository userRepository,
            LiveBettingCache liveBettingCache
    ) {
        this(eventRepository, userBetRepository, userRepository, liveBettingCache, Clock.systemUTC());
    }

    /**
     * 테스트에서 현재 시각을 고정할 수 있도록 조회 서비스를 구성한다.
     *
     * @param eventRepository 배팅 이벤트 저장소
     * @param userBetRepository 사용자 배팅 저장소
     * @param userRepository 사용자 저장소
     * @param liveBettingCache 라이브 배팅 캐시 포트
     * @param clock 현재 시각을 제공할 시계
     */
    BetQueryService(
            BettingEventRepository eventRepository,
            UserBetRepository userBetRepository,
            UserRepository userRepository,
            LiveBettingCache liveBettingCache,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.userBetRepository = userBetRepository;
        this.userRepository = userRepository;
        this.liveBettingCache = liveBettingCache;
        this.clock = clock;
    }

    /**
     * 매치의 최신 진행 이벤트와 현재 사용자의 참여 여부를 함께 조회한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param userId 사용자 ID
     * @return 현재 배팅 이벤트 조회 모델
     * @throws BettingException 현재 배팅 이벤트를 찾을 수 없을 때
     */
    public BettingEventView getCurrentEvent(String externalMatchId, Long userId) {
        BettingEvent event = eventRepository
                .findFirstByExternalMatchIdAndStatusInOrderBySetNumberDesc(
                        externalMatchId,
                        CURRENT_STATUSES
                )
                .orElseThrow(() -> new BettingException(BettingErrorCode.EVENT_NOT_FOUND));
        LocalDateTime now = now();
        UserBet userBet = userBetRepository
                .findByBettingEventIdAndUserId(event.getId(), userId)
                .orElse(null);
        boolean liveAvailable = liveBettingCache.isAcceptingBets(
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
    public UserBetView getMyBet(Long bettingEventId, Long userId) {
        UserBet userBet = userBetRepository
                .findByBettingEventIdAndUserId(bettingEventId, userId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.BET_NOT_FOUND));
        long currentPoint = userRepository.findPointById(userId)
                .orElseThrow(() -> new BettingException(BettingErrorCode.USER_NOT_FOUND));
        return new UserBetView(
                userBet.getId(),
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
}
