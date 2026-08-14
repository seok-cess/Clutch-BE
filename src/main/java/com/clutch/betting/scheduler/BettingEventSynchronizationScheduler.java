package com.clutch.betting.scheduler;

import com.clutch.betting.live.LiveBettingDataProvider;
import com.clutch.betting.live.LiveBettingDataProvider.LiveMatchSnapshot;
import com.clutch.betting.service.BettingEventSynchronizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 설정된 주기마다 라이브 캐시와 배팅 이벤트 동기화를 실행한다. */
@Component
public class BettingEventSynchronizationScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            BettingEventSynchronizationScheduler.class
    );

    private final LiveBettingDataProvider liveBettingDataProvider;
    private final BettingEventSynchronizationService synchronizationService;

    /**
     * 라이브 매치 제공자와 매치 단위 동기화 서비스를 주입받는다.
     *
     * @param liveBettingDataProvider 라이브 배팅 데이터 제공자
     * @param synchronizationService 매치 단위 동기화 서비스
     */
    public BettingEventSynchronizationScheduler(
            LiveBettingDataProvider liveBettingDataProvider,
            BettingEventSynchronizationService synchronizationService
    ) {
        this.liveBettingDataProvider = liveBettingDataProvider;
        this.synchronizationService = synchronizationService;
    }

    /** 이전 실행 종료 후 설정된 간격으로 라이브 상태 동기화를 요청한다. */
    @Scheduled(fixedDelayString = "${betting.synchronization-interval:1s}")
    public void synchronize() {
        for (LiveMatchSnapshot liveMatch : liveBettingDataProvider.findLiveMatches()) {
            try {
                synchronizationService.synchronizeMatch(liveMatch);
            } catch (DataIntegrityViolationException exception) {
                log.debug("중복 캐시 감지로 이미 생성된 배팅 이벤트를 유지합니다: {}",
                        liveMatch.externalMatchId());
            } catch (RuntimeException exception) {
                log.warn("배팅 이벤트 캐시 동기화 실패 (matchId={}): {}",
                        liveMatch.externalMatchId(), exception.toString());
            }
        }
    }
}
