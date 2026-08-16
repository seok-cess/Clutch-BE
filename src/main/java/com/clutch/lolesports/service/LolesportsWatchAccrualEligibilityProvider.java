package com.clutch.lolesports.service;

import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.watch.service.WatchAccrualEligibilityProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 라이브 폴링 캐시를 기준으로 시청 시간 적립 가능 구간을 판정한다.
 */
@Component
@RequiredArgsConstructor
public class LolesportsWatchAccrualEligibilityProvider implements WatchAccrualEligibilityProvider {

    private final EsportsMatchRepository esportsMatchRepository;
    private final DataCacheService dataCacheService;

    @Override
    public boolean canAccumulate(long matchId) {
        String externalMatchId = esportsMatchRepository.findById(matchId)
                .map(EsportsMatch::getExternalMatchId)
                .orElse(null);
        if (externalMatchId == null) {
            return false;
        }

        return dataCacheService.getLiveMatches().stream()
                .filter(match -> externalMatchId.equals(match.matchId()))
                .map(DataCacheService.LiveMatch::activeGameId)
                .filter(gameId -> gameId != null && !gameId.isBlank())
                .anyMatch(dataCacheService::isGameInProgress);
    }
}
