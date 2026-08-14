package com.clutch.betting.live;

import java.time.LocalDateTime;
import java.util.List;

/** 배팅 도메인에 최신 매치·세트 상태를 제공하는 라이브 데이터 조회 계약이다. */
public interface LiveBettingDataProvider {

    /**
     * 현재 라이브 중인 매치를 배팅 동기화용 스냅샷으로 조회한다.
     *
     * @return 현재 라이브 매치 스냅샷 목록
     */
    List<LiveMatchSnapshot> findLiveMatches();

    /**
     * 매치·세트의 최신 상태를 기준으로 신규 배팅 허용 여부를 확인한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param externalGameId 연결된 외부 게임 ID 또는 선개설 이벤트이면 null
     * @param setNumber 세트 번호
     * @return 최신 라이브 상태에서 배팅 가능하면 true
     */
    boolean isAcceptingBets(String externalMatchId, String externalGameId, int setNumber);

    /**
     * 팀·세트·종료 여부를 묶은 매치 단위 불변 스냅샷이다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param externalTeamIds 참가 팀 외부 ID 목록
     * @param sets 세트 상태 목록
     * @param matchFinished 매치 승부 확정 여부
     */
    record LiveMatchSnapshot(
            String externalMatchId,
            List<String> externalTeamIds,
            List<SetSnapshot> sets,
            boolean matchFinished
    ) {
    }

    /**
     * 세트 시작·종료·승자 상태를 묶은 불변 스냅샷이다.
     *
     * @param externalGameId 외부 게임 ID
     * @param setNumber 세트 번호
     * @param startedAt 세트 시작 시각
     * @param active 현재 진행 세트 여부
     * @param finished 세트 종료 여부
     * @param winnerExternalTeamId 승리 팀 외부 ID 또는 미확정이면 null
     */
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
