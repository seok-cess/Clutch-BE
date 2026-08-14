package com.clutch.betting.service;

import com.clutch.betting.integration.lolesports.LiveBettingCache;
import com.clutch.betting.integration.lolesports.LiveBettingCache.LiveMatchSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class BettingEventSynchronizationService {

    private static final Logger log = LoggerFactory.getLogger(BettingEventSynchronizationService.class);

    private final LiveBettingCache liveBettingCache;
    private final BettingEventSynchronizationProcessor processor;

    public BettingEventSynchronizationService(
            LiveBettingCache liveBettingCache,
            BettingEventSynchronizationProcessor processor
    ) {
        this.liveBettingCache = liveBettingCache;
        this.processor = processor;
    }

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
