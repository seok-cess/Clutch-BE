package com.clutch.betting.integration.lolesports;

import java.time.LocalDateTime;
import java.util.List;

/** 배팅 도메인이 lolesports 캐시 구현에 직접 의존하지 않도록 제공하는 라이브 데이터 포트다. */
public interface LiveBettingCache {

    /** 현재 라이브 중인 매치를 배팅 동기화용 스냅샷으로 조회한다. */
    List<LiveMatchSnapshot> findLiveMatches();

    /** 매치·세트의 최신 상태를 기준으로 신규 배팅 허용 여부를 확인한다. */
    boolean isAcceptingBets(String externalMatchId, String externalGameId, int setNumber);

    /** 팀·세트·종료 여부를 묶은 매치 단위 불변 스냅샷이다. */
    record LiveMatchSnapshot(
            String externalMatchId,
            List<String> externalTeamIds,
            List<SetSnapshot> sets,
            boolean matchFinished
    ) {
    }

    /** 세트 시작·종료·승자 상태를 묶은 불변 스냅샷이다. */
    record SetSnapshot(
            String externalGameId,
            int setNumber,
            LocalDateTime startedAt,
            boolean active,
            boolean finished,
            String winnerExternalTeamId
    ) {
    }
}
