package com.clutch.lolesports.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET /getStandings?hl=ko-KR&tournamentId={id} 응답.
 * 2026-08-07 실호출로 구조 확인함 — 커뮤니티 문서의 최상위 rankings[] 가 아니라
 * data.standings[].stages[].sections[].rankings[] 구조로 내려온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StandingsResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<Standing> standings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Standing(List<Stage> stages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stage(String id, String name, String slug, String type, List<Section> sections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(String name, List<Ranking> rankings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ranking(Integer ordinal, List<Team> teams) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(String id, String slug, String name, String code, String image, TeamRecord record) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRecord(Integer wins, Integer losses) {
    }
}
