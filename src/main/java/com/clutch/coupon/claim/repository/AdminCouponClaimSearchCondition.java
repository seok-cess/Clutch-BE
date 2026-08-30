package com.clutch.coupon.claim.repository;

import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.wallet.domain.UserCouponStatus;

import java.time.LocalDateTime;

/**
 * 관리자 발급 내역 동적 조회 조건.
 *
 * @param eventIdKeyword 정확히 일치시킬 이벤트 ID 검색 조건
 * @param eventNameKeyword 부분 일치시킬 이벤트 이름 검색 조건
 * @param triggerKeyword 부분 일치시킬 경기 트리거 문자열
 * @param userId 발급을 요청한 사용자 ID
 * @param requestStatus 발급 요청 처리 상태
 * @param couponStatus 실제 발급 쿠폰의 유효 상태
 * @param couponTypeId 쿠폰 종류 ID
 * @param from 발급 요청 조회 시작 시각
 * @param to 발급 요청 조회 종료 시각
 * @param statusReferenceTime 쿠폰 만료 상태를 계산할 UTC 기준 시각
 */
public record AdminCouponClaimSearchCondition(
        Long eventIdKeyword,
        String eventNameKeyword,
        String triggerKeyword,
        Long userId,
        ClaimRequestStatus requestStatus,
        UserCouponStatus couponStatus,
        Long couponTypeId,
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime statusReferenceTime
) {
}
