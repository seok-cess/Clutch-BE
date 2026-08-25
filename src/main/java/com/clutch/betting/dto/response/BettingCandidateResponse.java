package com.clutch.betting.dto.response;

import com.clutch.betting.dto.BettingCandidateView;

import java.util.List;

/** 실제로 열린 배팅 이벤트에 연결된 예정·라이브 매치의 응답이다. */
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

    /** 배팅 후보 매치를 API 응답으로 변환한다. */
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

    /** 배팅 후보 매치의 참가 팀 응답이다. */
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

    /** 배팅 후보 매치의 세트 응답이다. */
    public record GameResponse(
            String gameId,
            Integer number,
            String state,
            boolean feedFinished,
            String winnerTeamId,
            boolean statsUnavailable
    ) {

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
