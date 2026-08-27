package com.clutch.coupon.test.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 관리자 수동 오픈과 사용자 테스트 화면에서 공유하는 응답. */
public record CouponEventActivationResponse(
        Long couponEventId,
        Long couponEventOccurrenceId,
        String eventName,
        LocalDateTime openedAt,
        LocalDateTime expiresAt,
        CouponEventOccurrenceStatus occurrenceStatus,
        long remainingQuantity,
        boolean claimable,
        /**
         * 단계별 선착순(PHASED_FIRST_COME) 이벤트의 단계 목록.
         * 오픈 시간이 빠른 순서다. 일반 선착순이면 항목이 하나뿐이다.
         */
        List<Phase> phases
) {

    /**
     * 발급 단계 하나. 화면이 "지금 무엇을 받는지"와
     * "언제 혜택이 바뀌는지"를 그리는 데 필요한 값만 담는다.
     *
     * @param couponEventItemId 이 단계의 발급 항목 — 재고 조회 단위이자 발급 결과의 식별자
     * @param openOffsetSeconds 이벤트 오픈 시점부터 이 단계가 시작되기까지의 시간(초)
     * @param discountType 할인 유형 (RATE · AMOUNT)
     * @param discountValue 할인 값
     * @param remainingStock 이 단계의 남은 수량 — 단계마다 재고가 분리돼 있다
     * @param totalStock 이 단계의 전체 수량. 화면이 소진 정도를 비율로 그리려면
     *                   남은 수량만으로는 부족해 기준값이 함께 필요하다
     */
    public record Phase(
            Long couponEventItemId,
            int openOffsetSeconds,
            String discountType,
            BigDecimal discountValue,
            long remainingStock,
            long totalStock
    ) {
    }
}
