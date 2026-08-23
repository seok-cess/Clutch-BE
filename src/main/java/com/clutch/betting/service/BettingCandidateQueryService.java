package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.lolesports.service.DataCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/** 실제로 열려 있는 배팅 이벤트와 연결된 예정·라이브 매치를 조회한다. */
@Service
@RequiredArgsConstructor
public class BettingCandidateQueryService {

    private final BettingEventRepository bettingEventRepository;
    private final DataCacheService dataCacheService;
    private final Clock clock;

    /**
     * 배팅 이벤트가 OPEN이고 현재 시각에도 유효한 매치만 반환한다.
     *
     * <p>라이브 화면용 캐시와 배팅 후보 캐시는 의도적으로 분리돼 있다. 이 조회는
     * 시작 전 20분처럼 아직 {@code /api/live}에 없는 경기라도, 실제 배팅 이벤트가
     * 생성된 뒤에만 사용자 화면에 노출하게 한다.</p>
     *
     * @return 현재 배팅 가능한 매치의 화면 표시용 캐시 값
     */
    @Transactional(readOnly = true)
    public List<DataCacheService.LiveMatch> findOpenMatchCandidates() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Set<String> openMatchIds = bettingEventRepository.findAllByStatus(BettingEventStatus.OPEN).stream()
                .filter(event -> event.isOpenAt(now))
                .map(event -> event.getExternalMatchId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        if (openMatchIds.isEmpty()) {
            return List.of();
        }
        return dataCacheService.getBettingMatches().stream()
                .filter(match -> openMatchIds.contains(match.matchId()))
                .filter(match -> !match.isFinished())
                .toList();
    }
}
