package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponIssueMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

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
