package com.clutch.coupon.claim.service;

import com.clutch.common.privacy.PersonalDataMasker;
import com.clutch.coupon.claim.api.dto.AdminCouponClaimListResponse;
import com.clutch.coupon.claim.api.dto.AdminCouponClaimResponse;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.repository.AdminCouponClaimQueryRepository;
import com.clutch.coupon.claim.repository.AdminCouponClaimRow;
import com.clutch.coupon.claim.repository.AdminCouponClaimSearchCondition;
import com.clutch.coupon.type.domain.CouponDiscountType;
import com.clutch.wallet.domain.UserCouponStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.INVALID_ADMIN_CLAIM_QUERY;

/** 관리자 쿠폰 발급 내역의 필터 검증, 마스킹 및 번호형 페이지 응답을 처리한다. */
@Service
@RequiredArgsConstructor
public class AdminCouponClaimService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EVENT_KEYWORD_LENGTH = 200;
    private static final int MAX_TRIGGER_KEYWORD_LENGTH = 50;

    private final AdminCouponClaimQueryRepository queryRepository;
    private final PersonalDataMasker personalDataMasker;
    private final Clock clock;

    /**
     * 관리자 발급 내역을 필터 조합과 페이지 번호 기준으로 조회한다.
     *
     * <p>이벤트 검색어를 ID 또는 이름 조건으로 구분하고, 조회된 개인정보를
     * 마스킹한다. 관리자 화면의 번호형 페이지네이션을 위해 같은 필터의 전체
     * 건수를 조회하고 전체 페이지 수를 계산한다. {@code page}는 0부터 시작한다.</p>
     *
     * @param eventKeyword 이벤트 ID 또는 이벤트 이름 검색어
     * @param triggerKeyword 경기 트리거 문자열 검색어
     * @param userId 발급을 요청한 사용자 ID
     * @param requestStatus 발급 요청 처리 상태
     * @param couponStatus 실제 발급 쿠폰의 유효 상태
     * @param couponTypeId 쿠폰 종류 ID
     * @param from 발급 요청 조회 시작 시각
     * @param to 발급 요청 조회 종료 시각
     * @param page 조회할 페이지 번호, 0부터 시작
     * @param size 한 페이지에서 조회할 내역 수
     * @return 마스킹 및 번호형 페이지 정보가 포함된 발급 내역 목록
     */
    @Transactional(readOnly = true)
    public AdminCouponClaimListResponse findAll(
            String eventKeyword,
            String triggerKeyword,
            Long userId,
            ClaimRequestStatus requestStatus,
            UserCouponStatus couponStatus,
            Long couponTypeId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        validate(userId, couponTypeId, from, to, page, size);

        String normalizedEventKeyword = normalize(
                eventKeyword,
                MAX_EVENT_KEYWORD_LENGTH
        );
        String normalizedTriggerKeyword = normalize(
                triggerKeyword,
                MAX_TRIGGER_KEYWORD_LENGTH
        );
        Long eventIdKeyword = parseEventId(normalizedEventKeyword);
        String eventNameKeyword = eventIdKeyword == null
                ? normalizedEventKeyword
                : null;

        AdminCouponClaimSearchCondition condition =
                new AdminCouponClaimSearchCondition(
                        eventIdKeyword,
                        eventNameKeyword,
                        normalizedTriggerKeyword,
                        userId,
                        requestStatus,
                        couponStatus,
                        couponTypeId,
                        from,
                        to,
                        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                );

        List<AdminCouponClaimRow> rows = queryRepository.findAll(
                condition,
                size,
                (long) page * size
        );
        long totalElements = queryRepository.count(condition);
        int totalPages = Math.toIntExact(Math.ceilDiv(totalElements, (long) size));
        List<AdminCouponClaimResponse> claims = rows.stream()
                .map(this::toResponse)
                .toList();

        return new AdminCouponClaimListResponse(
                claims,
                page,
                size,
                totalElements,
                totalPages,
                page > 0 && totalElements > 0,
                page + 1 < totalPages
        );
    }

    /**
     * 내부 조인 조회 결과를 개인정보가 마스킹된 API 응답으로 변환한다.
     *
     * @param row 발급 요청과 실제 쿠폰을 조인한 조회 결과
     * @return 관리자 화면에 반환할 발급 내역 한 행
     */
    private AdminCouponClaimResponse toResponse(AdminCouponClaimRow row) {
        return new AdminCouponClaimResponse(
                row.claimRequestId(),
                row.requestedAt(),
                row.completedAt(),
                row.couponEventId(),
                row.eventName(),
                row.triggerType(),
                row.couponEventOccurrenceId(),
                row.userId(),
                personalDataMasker.maskName(row.userName()),
                personalDataMasker.maskEmail(row.userEmail()),
                personalDataMasker.maskPhoneNumber(row.userPhoneNumber()),
                row.couponTypeId(),
                row.couponName(),
                CouponDiscountType.valueOf(row.discountType()),
                row.discountValue(),
                ClaimRequestStatus.valueOf(row.requestStatus()),
                row.failureReason(),
                row.userCouponId(),
                row.couponStatus() == null
                        ? null
                        : UserCouponStatus.valueOf(row.couponStatus())
        );
    }

    /**
     * ID, 페이지 번호, 페이지 크기 및 조회 기간 조건의 유효성을 검증한다.
     *
     * @param userId 사용자 ID
     * @param couponTypeId 쿠폰 종류 ID
     * @param from 조회 시작 시각
     * @param to 조회 종료 시각
     * @param page 조회할 페이지 번호, 0부터 시작
     * @param size 페이지 크기
     */
    private void validate(
            Long userId,
            Long couponTypeId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        if (userId != null && userId <= 0) {
            throw invalidQuery();
        }
        if (couponTypeId != null && couponTypeId <= 0) {
            throw invalidQuery();
        }
        if (page < 0) {
            throw invalidQuery();
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidQuery();
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidQuery();
        }
    }

    /**
     * 문자열 검색 조건의 앞뒤 공백을 제거하고 최대 길이를 검증한다.
     *
     * @param value 정규화할 검색 문자열
     * @param maxLength 허용할 최대 문자열 길이
     * @return 정규화된 문자열, 값이 없으면 {@code null}
     */
    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidQuery();
        }
        return normalized;
    }

    /**
     * 숫자로만 구성된 이벤트 검색어를 이벤트 ID로 변환한다.
     *
     * @param eventKeyword 정규화된 이벤트 검색어
     * @return 변환된 이벤트 ID, 숫자 검색어가 아니면 {@code null}
     */
    private Long parseEventId(String eventKeyword) {
        if (eventKeyword == null || !eventKeyword.matches("[0-9]+")) {
            return null;
        }
        try {
            long eventId = Long.parseLong(eventKeyword);
            if (eventId <= 0) {
                throw invalidQuery();
            }
            return eventId;
        } catch (NumberFormatException exception) {
            throw invalidQuery();
        }
    }

    /**
     * 잘못된 관리자 발급 내역 조회 조건 예외를 생성한다.
     *
     * @return 관리자 조회 조건 오류 예외
     */
    private CouponClaimException invalidQuery() {
        return new CouponClaimException(INVALID_ADMIN_CLAIM_QUERY);
    }
}
