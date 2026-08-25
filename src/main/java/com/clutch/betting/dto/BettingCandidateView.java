package com.clutch.betting.dto;

import java.util.List;

/** 현재 배팅 가능한 매치와 화면 표시에 필요한 세트 정보를 전달하는 조회 모델이다. */
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

    /** 배팅 후보 매치의 참가 팀 정보다. */
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

    /** 배팅 후보 매치에 포함된 세트 정보다. */
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
