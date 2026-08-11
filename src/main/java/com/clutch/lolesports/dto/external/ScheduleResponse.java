package com.clutch.lolesports.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET /getSchedule?hl=ko-KR&leagueId={id} 응답.
 * 2026-08-07 실호출로 구조 확인함.
 * getLive(?hl=ko-KR) 응답도 동일하게 data.schedule.events[] 구조라 이 DTO를 공용으로 사용한다.
 * (getLive 의 이벤트에는 streams 등 추가 필드가 있으나 ignoreUnknown 으로 무시)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduleResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(Schedule schedule) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Schedule(Pages pages, List<Event> events) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pages(String older, String newer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            String startTime,   // RFC3339 (예: 2026-05-17T08:00:00Z)
            String state,       // unstarted | inProgress | completed
            String type,        // match | show
            String blockName,   // 예: "7주 차"
            League league,
            Match match         // type=show 이면 null
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record League(String name, String slug) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(String id, List<String> flags, List<Team> teams, Strategy strategy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /** id 는 getEventDetails 응답에만 있고 getSchedule 에는 없을 수 있다 (null 허용) */
    public record Team(String id, String name, String code, String image, Result result, TeamRecord record) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String outcome, Integer gameWins) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRecord(Integer wins, Integer losses) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Strategy(String type, Integer count) {
    }
}
