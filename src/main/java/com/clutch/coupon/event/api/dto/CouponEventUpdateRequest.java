package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponIssueMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 관리자 쿠폰 이벤트 설정 수정 요청.
 *
 * @param esportsMatchId 변경할 경기 ID
 * @param eventName 변경할 이벤트 이름
 * @param issueMode 변경할 쿠폰 발급 방식
 * @param triggerType 변경할 경기 트리거 종류
 * @param claimWindowSeconds 변경할 쿠폰 신청 가능 시간(초)
 * @param items 교체할 쿠폰 종류, 수량 및 단계 설정
 */
public record CouponEventUpdateRequest(
        @NotNull(message = "경기 ID는 필수입니다.")
        @Positive(message = "경기 ID는 양수여야 합니다.")
        Long esportsMatchId,

        @NotBlank(message = "이벤트 이름은 필수입니다.")
        @Size(max = 200, message = "이벤트 이름은 200자 이하여야 합니다.")
        String eventName,

        @NotNull(message = "발급 방식은 필수입니다.")
        CouponIssueMode issueMode,

        @NotBlank(message = "트리거 종류는 필수입니다.")
        String triggerType,

        @NotNull(message = "신청 가능 시간은 필수입니다.")
        @Positive(message = "신청 가능 시간은 1초 이상이어야 합니다.")
        Integer claimWindowSeconds,

        @NotEmpty(message = "쿠폰 항목은 한 개 이상 필요합니다.")
        List<@NotNull(message = "쿠폰 항목은 null일 수 없습니다.")
                @Valid CouponEventItemCreateRequest> items
) {

    /**
     * 등록과 동일한 설정 검증을 재사용할 수 있도록 등록 요청 형태로 변환한다.
     *
     * @return 현재 수정 요청의 값을 담은 등록 요청 객체
     */
    public CouponEventCreateRequest toCreateRequest() {
        return new CouponEventCreateRequest(
                esportsMatchId,
                eventName,
                issueMode,
                triggerType,
                claimWindowSeconds,
                items
        );
    }
}
