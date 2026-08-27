package com.clutch.coupon.test.event.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * 시연 화면이 보내는 경기 프레임 한 장.
 *
 * <p>실제 피드 없이도 감지기를 그대로 태우기 위한 입력이다. 화면이 트리거를
 * 직접 지목하지 않고 킬 수만 보내므로, 펜타킬 판정은 서버 감지기가 한다.</p>
 *
 * @param gameId 시연 세트 식별자. 감지 상태와 중복 방지 키가 이 값으로 묶인다
 * @param gameTimeSeconds 게임 시작 후 경과 초. 감지기의 시간창 판정 기준이다
 * @param blue 블루팀 참가자별 누적 킬
 * @param red 레드팀 참가자별 누적 킬
 */
public record SampleFrameRequest(
        @NotNull String gameId,
        @NotNull @PositiveOrZero Integer gameTimeSeconds,
        List<Participant> blue,
        List<Participant> red
) {

    /**
     * 참가자 한 명의 누적 킬.
     *
     * @param participantId 참가자 번호
     * @param kills 그 시점까지의 누적 킬
     */
    public record Participant(
            @NotNull Integer participantId,
            @NotNull @PositiveOrZero Integer kills
    ) {
    }
}
