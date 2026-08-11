package com.clutch.lolesports.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET https://feed.lolesports.com/livestats/v1/window/{gameId}?startingTime={RFC3339} 응답 (팀 단위 스탯).
 * 2026-08-07 종료된 게임(115548128962971872)으로 실검증 완료 — gameMetadata(참가자 메타 포함),
 * frames(rfc460Timestamp, gameState in_game/finished, 팀·참가자 스탯) 전부 정상 파싱 확인.
 *  - 경기 시작 전에는 404 또는 204(No Content)가 내려올 수 있음
 * TODO(응답 확인 필요): 진행중 게임의 실시간 응답은 라이브 때 한 번 더 확인.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WindowResponse(
        String esportsGameId,
        String esportsMatchId,
        GameMetadata gameMetadata,
        List<Frame> frames
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameMetadata(
            String patchVersion,
            TeamMetadata blueTeamMetadata,
            TeamMetadata redTeamMetadata
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamMetadata(String esportsTeamId, List<ParticipantMetadata> participantMetadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParticipantMetadata(
            Integer participantId,   // blue 1~5, red 6~10
            String esportsPlayerId,
            String summonerName,
            String championId,
            String role
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Frame(
            String rfc460Timestamp,  // 프레임 중복 처리 기준값
            String gameState,        // in_game | paused | finished 추정
            TeamFrame blueTeam,
            TeamFrame redTeam
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamFrame(
            Long totalGold,
            Integer inhibitors,
            Integer towers,
            Integer barons,
            Integer totalKills,
            List<String> dragons,
            List<ParticipantFrame> participants
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParticipantFrame(
            Integer participantId,
            Long totalGold,
            Integer level,
            Integer kills,
            Integer deaths,
            Integer assists,
            Integer creepScore,
            Integer currentHealth,
            Integer maxHealth
    ) {
    }
}
