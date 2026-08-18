package com.clutch.coupon.test.event.service;

import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.test.event.api.dto.CouponEventActivationResponse;
import com.clutch.coupon.test.event.domain.CouponEvent;
import com.clutch.coupon.test.event.domain.CouponEventOccurrence;
import com.clutch.coupon.test.event.exception.CouponEventErrorCode;
import com.clutch.coupon.test.event.exception.CouponEventException;
import com.clutch.coupon.test.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.test.event.repository.CouponEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 관리자 수동 쿠폰 오픈과 사용자 테스트용 활성 회차 조회를 처리한다. */
@Service
@RequiredArgsConstructor
public class CouponEventActivationService {

    private final CouponEventRepository couponEventRepository;
    private final CouponEventItemRepository couponEventItemRepository;
    private final CouponEventOccurrenceRepository occurrenceRepository;
    private final Clock clock;

    /** 경기 트리거와 무관하게 대기 중인 쿠폰 이벤트를 즉시 연다. */
    @Transactional
    public CouponEventActivationResponse manualOpen(Long couponEventId) {
        CouponEvent event = couponEventRepository
                .findByIdForUpdate(couponEventId)
                .orElseThrow(() -> new CouponEventException(
                        CouponEventErrorCode.COUPON_EVENT_NOT_FOUND
                ));
        LocalDateTime now = now();

        boolean alreadyOpen = occurrenceRepository
                .findFirstByCouponEventIdAndOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                        couponEventId,
                        CouponEventOccurrenceStatus.OPEN,
                        now,
                        now
                )
                .isPresent();
        if (alreadyOpen) {
            throw new CouponEventException(
                    CouponEventErrorCode.COUPON_EVENT_ALREADY_OPEN
            );
        }
        if (event.getEventStatus() != CouponEventStatus.READY) {
            throw new CouponEventException(
                    CouponEventErrorCode.COUPON_EVENT_NOT_OPENABLE
            );
        }

        long remainingQuantity = remainingQuantity(event.getId());
        if (remainingQuantity <= 0L) {
            throw new CouponEventException(
                    CouponEventErrorCode.COUPON_EVENT_STOCK_EXHAUSTED
            );
        }

        event.open();
        CouponEventOccurrence occurrence = occurrenceRepository.save(
                CouponEventOccurrence.manualOpen(
                        event.getId(),
                        now,
                        event.getClaimWindowSeconds()
                )
        );

        return toResponse(event, occurrence, remainingQuantity, true);
    }

    /** 전체 이벤트 중 가장 최근에 열린 테스트 발급 회차를 조회한다. */
    @Transactional(readOnly = true)
    public Optional<CouponEventActivationResponse> findActive() {
        LocalDateTime now = now();
        return occurrenceRepository
                .findFirstByOccurrenceStatusAndClosedAtIsNullAndOpenedAtLessThanEqualAndExpiresAtAfterOrderByOpenedAtDescIdDesc(
                        CouponEventOccurrenceStatus.OPEN,
                        now,
                        now
                )
                .flatMap(occurrence -> couponEventRepository
                        .findById(occurrence.getCouponEventId())
                        .map(event -> {
                            long remainingQuantity = remainingQuantity(
                                    event.getId()
                            );
                            return toResponse(
                                    event,
                                    occurrence,
                                    remainingQuantity,
                                    remainingQuantity > 0L
                            );
                        }));
    }

    /** 만료됐지만 아직 열린 상태인 테스트 회차와 이벤트를 종료한다. */
    @Transactional
    public int closeExpiredOccurrences() {
        LocalDateTime now = now();
        List<CouponEventOccurrence> expiredOccurrences = occurrenceRepository
                .findAllByOccurrenceStatusAndClosedAtIsNullAndExpiresAtLessThanEqual(
                        CouponEventOccurrenceStatus.OPEN,
                        now
                );
        int closedCount = 0;
        for (CouponEventOccurrence occurrence : expiredOccurrences) {
            if (!occurrence.closeIfExpired(now)) {
                continue;
            }
            couponEventRepository.findById(occurrence.getCouponEventId())
                    .ifPresent(CouponEvent::close);
            closedCount++;
        }
        return closedCount;
    }

    private long remainingQuantity(Long couponEventId) {
        return couponEventItemRepository
                .findAllByCouponEventId(couponEventId)
                .stream()
                .mapToLong(CouponEventItem::remainingStock)
                .sum();
    }

    private CouponEventActivationResponse toResponse(
            CouponEvent event,
            CouponEventOccurrence occurrence,
            long remainingQuantity,
            boolean claimable
    ) {
        return new CouponEventActivationResponse(
                event.getId(),
                occurrence.getId(),
                event.getEventName(),
                occurrence.getOpenedAt(),
                occurrence.getExpiresAt(),
                occurrence.getOccurrenceStatus(),
                remainingQuantity,
                claimable
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
