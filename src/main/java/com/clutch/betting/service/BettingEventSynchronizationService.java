package com.clutch.betting.service;

import com.clutch.betting.integration.lolesports.LiveBettingCache;
import com.clutch.betting.integration.lolesports.LiveBettingCache.LiveMatchSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 캐시의 라이브 매치를 순회하며 매치별 동기화 실패를 격리한다. */
@Service
public class BettingEventSynchronizationService {

    private static final Logger log = LoggerFactory.getLogger(BettingEventSynchronizationService.class);

    private final LiveBettingCache liveBettingCache;
    private final BettingEventSynchronizationProcessor processor;

    /**
     * 라이브 캐시 포트와 매치 단위 처리기를 주입받는다.
     *
     * @param liveBettingCache 라이브 배팅 캐시 포트
     * @param processor 매치 단위 동기화 처리기
     */
    public BettingEventSynchronizationService(
            LiveBettingCache liveBettingCache,
            BettingEventSynchronizationProcessor processor
    ) {
        this.liveBettingCache = liveBettingCache;
        this.processor = processor;
    }

    /** 모든 라이브 매치를 동기화하되 중복 생성과 개별 오류를 다음 매치와 격리한다. */
    public void synchronize() {
        for (LiveMatchSnapshot liveMatch : liveBettingCache.findLiveMatches()) {
            try {
                processor.synchronizeMatch(liveMatch);
            } catch (DataIntegrityViolationException exception) {
                log.debug("중복 캐시 감지로 이미 생성된 배팅 이벤트를 유지합니다: {}", liveMatch.externalMatchId());
            } catch (RuntimeException exception) {
                log.warn("배팅 이벤트 캐시 동기화 실패 (matchId={}): {}",
                        liveMatch.externalMatchId(), exception.toString());
            }
        }
    }
}
