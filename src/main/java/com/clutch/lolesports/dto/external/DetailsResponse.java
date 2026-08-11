package com.clutch.lolesports.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET https://feed.lolesports.com/livestats/v1/details/{gameId}?startingTime={RFC3339} 응답 (선수 상세).
 * 2026-08-07 종료된 게임(115548128962971872)으로 실검증 완료 — participants 의
 * items(정수 ID 배열), perkMetadata.perks(룬 ID 배열), killParticipation/championDamageShare,
 * wardsPlaced/Destroyed, totalGoldEarned 정상 파싱 확인.
 *  - 아이템/룬 ID → 이름/아이콘 변환은 Data Dragon 매핑 필요, 다음 단계 TODO (지금은 ID 그대로 노출)
 *  - abilities 필드는 미확인 (파싱 실패해도 ignoreUnknown 아님 — null 로 들어옴)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetailsResponse(List<Frame> frames) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Frame(String rfc460Timestamp, List<Participant> participants) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(
            Integer participantId,
            Integer level,
            Integer kills,
            Integer deaths,
            Integer assists,
            Long totalGoldEarned,
            Integer creepScore,
            Double killParticipation,
            Double championDamageShare,
            Integer wardsPlaced,
            Integer wardsDestroyed,
            Long attackDamage,
            Long abilityPower,
            List<Long> items,          // 아이템 ID 정수 배열 (Data Dragon 매핑은 TODO)
            PerkMetadata perkMetadata,
            List<String> abilities
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerkMetadata(Long styleId, Long subStyleId, List<Long> perks) {
    }
}
