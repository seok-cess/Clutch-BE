package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventOpenMode;
import com.clutch.coupon.event.domain.CouponIssueMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CouponEventUpdateRequest(
        Long esportsMatchId,

        @NotBlank(message = "이벤트 이름은 필수입니다.")
        @Size(max = 200, message = "이벤트 이름은 200자 이하여야 합니다.")
        String eventName,

        @NotNull(message = "오픈 방식은 필수입니다.")
        CouponEventOpenMode openMode,

        @NotNull(message = "발급 방식은 필수입니다.")
        CouponIssueMode issueMode,

        String triggerType,

        @NotNull(message = "신청 가능 시간은 필수입니다.")
        @Positive(message = "신청 가능 시간은 1초 이상이어야 합니다.")
        Integer claimWindowSeconds,

        LocalDateTime scheduledOpenAt,

        @NotEmpty(message = "쿠폰 항목은 한 개 이상 필요합니다.")
        List<@NotNull(message = "쿠폰 항목은 null일 수 없습니다.")
                @Valid CouponEventItemCreateRequest> items
) {

    public CouponEventCreateRequest toCreateRequest() {
        return new CouponEventCreateRequest(
                esportsMatchId,
                eventName,
                openMode,
                issueMode,
                triggerType,
                claimWindowSeconds,
                scheduledOpenAt,
                items
        );
    }
}
