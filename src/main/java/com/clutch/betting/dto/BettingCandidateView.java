package com.clutch.betting.dto;

import java.util.List;

/**
 * 현재 배팅 가능한 매치와 화면 표시에 필요한 세트 정보를 전달하는 조회 모델이다.
 *
 * @param matchId 외부 매치 ID
 * @param leagueName 리그 표시 이름
 * @param blockName 대회 블록 또는 라운드 이름
 * @param startTime 외부 피드의 공식 시작 시각 문자열
 * @param bestOf 다전제 최대 세트 수
 * @param matchFinished 매치 종료 여부
 * @param matchWinnerTeamId 매치 승리 팀 ID 또는 미확정이면 null
 * @param teams 참가 팀 표시 정보
 * @param games 세트별 진행·결과 정보
 * @param activeGameId 현재 진행 중인 외부 게임 ID 또는 없으면 null
 */
public record BettingCandidateView(
        String matchId,
        String leagueName,
        String blockName,
        String startTime,
        Integer bestOf,
        boolean matchFinished,
        String matchWinnerTeamId,
        List<Team> teams,
        List<Game> games,
        String activeGameId
) {

    /**
     * 배팅 후보 매치의 참가 팀 정보다.
     *
     * @param id 외부 팀 ID
     * @param name 팀 이름
     * @param code 팀 약칭
     * @param image 팀 이미지 URL
     * @param outcome 외부 피드의 경기 결과 표기
     * @param gameWins 현재 매치 세트 승수
     * @param wins 시즌 승수
     * @param losses 시즌 패수
     */
    public record Team(
            String id,
            String name,
            String code,
            String image,
            String outcome,
            Integer gameWins,
            Integer wins,
            Integer losses
    ) {
    }

    /**
     * 배팅 후보 매치에 포함된 세트 정보다.
     *
     * @param gameId 외부 게임 ID
     * @param number 매치 내 세트 번호
     * @param state 외부 피드의 세트 상태
     * @param feedFinished livestats 종료 프레임 수신 여부
     * @param winnerTeamId 추적된 세트 승리 팀 ID 또는 미확정이면 null
     * @param statsUnavailable 라이브 통계 피드 이용 불가 여부
     */
    public record Game(
            String gameId,
            Integer number,
            String state,
            boolean feedFinished,
            String winnerTeamId,
            boolean statsUnavailable
    ) {
    }
}
