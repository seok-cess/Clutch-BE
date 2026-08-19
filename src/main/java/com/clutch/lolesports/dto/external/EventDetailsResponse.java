package com.clutch.lolesports.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET /getEventDetails?hl=ko-KR&id={matchId} 응답.
 * match.games[] 에서 인게임 통계용 gameId 를 얻는다.
 * TODO(응답 확인 필요): 작성 시점(2026-08-07)에 라이브 경기가 없어 실응답 미검증. 커뮤니티 문서 기준 추정.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDetailsResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(Event event) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String id, String type, ScheduleResponse.League league,
                        Tournament tournament, Match match) {
    }

    /** 이 매치가 속한 대회. LCK 는 시즌이 스플릿으로 나뉘어 매치마다 달라진다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tournament(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(List<ScheduleResponse.Team> teams, List<Game> games, ScheduleResponse.Strategy strategy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Game(
            String id,          // livestats 호출에 쓰는 gameId
            Integer number,     // 세트 번호 (1, 2, 3, ...)
            String state,       // unstarted | inProgress | completed
            List<GameTeam> teams
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameTeam(String id, String side) { // side: blue | red
    }
}
