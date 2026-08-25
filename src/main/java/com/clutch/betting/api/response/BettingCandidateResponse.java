package com.clutch.betting.api.response;

import com.clutch.betting.dto.BettingCandidateView;

import java.util.List;

/**
 * 실제로 열린 배팅 이벤트에 연결된 예정·라이브 매치의 응답이다.
 *
 * @param matchId 외부 매치 ID
 * @param leagueName 리그 표시 이름
 * @param blockName 대회 블록 또는 라운드 이름
 * @param startTime 공식 시작 시각 문자열
 * @param bestOf 다전제 최대 세트 수
 * @param matchFinished 매치 종료 여부
 * @param matchWinnerTeamId 매치 승리 팀 ID 또는 미확정이면 null
 * @param teams 참가 팀 정보
 * @param games 세트별 진행·결과 정보
 * @param activeGameId 현재 진행 중인 외부 게임 ID 또는 없으면 null
 */
public record BettingCandidateResponse(
        String matchId,
        String leagueName,
        String blockName,
        String startTime,
        Integer bestOf,
        boolean matchFinished,
        String matchWinnerTeamId,
        List<TeamResponse> teams,
        List<GameResponse> games,
        String activeGameId
) {

    /**
     * 배팅 후보 조회 모델을 API 응답으로 변환한다.
     *
     * @param view 배팅 후보 조회 모델
     * @return API 배팅 후보 응답
     */
    public static BettingCandidateResponse from(BettingCandidateView view) {
        return new BettingCandidateResponse(
                view.matchId(),
                view.leagueName(),
                view.blockName(),
                view.startTime(),
                view.bestOf(),
                view.matchFinished(),
                view.matchWinnerTeamId(),
                view.teams().stream().map(TeamResponse::from).toList(),
                view.games().stream().map(GameResponse::from).toList(),
                view.activeGameId()
        );
    }

    /**
     * 배팅 후보 매치의 참가 팀 응답이다.
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
    public record TeamResponse(
            String id,
            String name,
            String code,
            String image,
            String outcome,
            Integer gameWins,
            Integer wins,
            Integer losses
    ) {

        /** 내부 팀 조회 모델을 API 팀 응답으로 변환한다. */
        private static TeamResponse from(BettingCandidateView.Team team) {
            return new TeamResponse(
                    team.id(),
                    team.name(),
                    team.code(),
                    team.image(),
                    team.outcome(),
                    team.gameWins(),
                    team.wins(),
                    team.losses()
            );
        }
    }

    /**
     * 배팅 후보 매치의 세트 응답이다.
     *
     * @param gameId 외부 게임 ID
     * @param number 매치 내 세트 번호
     * @param state 외부 피드의 세트 상태
     * @param feedFinished livestats 종료 프레임 수신 여부
     * @param winnerTeamId 세트 승리 팀 ID 또는 미확정이면 null
     * @param statsUnavailable 라이브 통계 피드 이용 불가 여부
     */
    public record GameResponse(
            String gameId,
            Integer number,
            String state,
            boolean feedFinished,
            String winnerTeamId,
            boolean statsUnavailable
    ) {

        /** 내부 세트 조회 모델을 API 세트 응답으로 변환한다. */
        private static GameResponse from(BettingCandidateView.Game game) {
            return new GameResponse(
                    game.gameId(),
                    game.number(),
                    game.state(),
                    game.feedFinished(),
                    game.winnerTeamId(),
                    game.statsUnavailable()
            );
        }
    }
}
