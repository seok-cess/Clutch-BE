package com.clutch.coupon.event.service;

import com.clutch.coupon.event.api.dto.CouponEventCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateResponse;
import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOpenMode;
import com.clutch.coupon.event.domain.CouponEventPhase;
import com.clutch.coupon.event.domain.CouponIssueMode;
import com.clutch.coupon.event.exception.CouponEventErrorCode;
import com.clutch.coupon.event.exception.CouponEventException;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventPhaseRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CouponEventService {

    private final CouponEventRepository couponEventRepository;
    private final CouponEventItemRepository couponEventItemRepository;
    private final CouponEventPhaseRepository couponEventPhaseRepository;

    @Transactional
    public CouponEventCreateResponse create(CouponEventCreateRequest request) {
        validateRequest(request);
        validateDuplicateTriggerEvent(request);

        CouponEvent event = CouponEvent.create(
                request.esportsMatchId(),
                request.eventName(),
                request.openMode(),
                request.issueMode(),
                normalizeTriggerType(request.triggerType()),
                request.claimWindowSeconds(),
                request.scheduledOpenAt()
        );
        CouponEvent savedEvent = couponEventRepository.save(event);

        List<CouponEventItemCreateResponse> itemResponses =
                saveItemsAndPhases(savedEvent.getId(), request.items());

        return new CouponEventCreateResponse(
                savedEvent.getId(),
                savedEvent.getEsportsMatchId(),
                savedEvent.getEventName(),
                savedEvent.getOpenMode(),
                savedEvent.getIssueMode(),
                savedEvent.getTriggerType(),
                savedEvent.getEventStatus(),
                savedEvent.getClaimWindowSeconds(),
                savedEvent.getScheduledOpenAt(),
                savedEvent.getCreatedAt(),
                itemResponses
        );
    }

    private List<CouponEventItemCreateResponse> saveItemsAndPhases(
            Long couponEventId,
            List<CouponEventItemCreateRequest> requests
    ) {
        List<CouponEventItemCreateResponse> responses = new ArrayList<>();

        for (int index = 0; index < requests.size(); index++) {
            CouponEventItemCreateRequest request = requests.get(index);
            CouponEventItem item = couponEventItemRepository.save(
                    CouponEventItem.create(
                            couponEventId,
                            request.couponTypeId(),
                            request.quantity()
                    )
            );
            CouponEventPhase phase = couponEventPhaseRepository.save(
                    CouponEventPhase.create(
                            couponEventId,
                            item.getId(),
                            index + 1,
                            request.openOffsetSeconds()
                    )
            );

            responses.add(new CouponEventItemCreateResponse(
                    phase.getId(),
                    item.getId(),
                    item.getCouponTypeId(),
                    item.getQuantity(),
                    item.getSuccessCount(),
                    phase.getPhaseSequence(),
                    phase.getOpenOffsetSeconds()
            ));
        }
        return List.copyOf(responses);
    }

    private void validateRequest(CouponEventCreateRequest request) {
        if (request.openMode() == CouponEventOpenMode.SCHEDULED) {
            validateScheduledEvent(request);
        } else if (request.openMode() == CouponEventOpenMode.GAME_TRIGGERED) {
            validateGameTriggeredEvent(request);
        }
        validateItems(request);
    }

    private void validateScheduledEvent(CouponEventCreateRequest request) {
        if (request.issueMode() != CouponIssueMode.SINGLE_FIRST_COME) {
            invalid("예약 이벤트는 일반 선착순 발급 방식이어야 합니다.");
        }
        if (request.esportsMatchId() != null
                || (request.triggerType() != null
                && !request.triggerType().isBlank())) {
            invalid("예약 이벤트에는 경기 ID와 트리거 종류를 설정할 수 없습니다.");
        }
        if (request.scheduledOpenAt() == null) {
            invalid("예약 이벤트에는 오픈 시간이 필요합니다.");
        }
        if (request.items().size() != 1
                || request.items().getFirst().openOffsetSeconds() != 0) {
            invalid("일반 선착순 이벤트는 오픈 시간 0초인 쿠폰 항목 한 개만 등록할 수 있습니다.");
        }
    }

    private void validateGameTriggeredEvent(CouponEventCreateRequest request) {
        if (request.issueMode() != CouponIssueMode.PHASED_FIRST_COME) {
            invalid("경기 트리거 이벤트는 차등 혜택 발급 방식이어야 합니다.");
        }
        if (request.esportsMatchId() == null
                || request.esportsMatchId() <= 0) {
            invalid("경기 트리거 이벤트에는 경기 ID가 필요합니다.");
        }
        if (request.triggerType() == null
                || request.triggerType().isBlank()) {
            invalid("경기 트리거 이벤트에는 트리거 종류가 필요합니다.");
        }
        if (request.scheduledOpenAt() != null) {
            invalid("경기 트리거 이벤트에는 예약 오픈 시간을 설정할 수 없습니다.");
        }
        if (request.items().size() < 2) {
            invalid("차등 혜택 이벤트에는 쿠폰 단계가 두 개 이상 필요합니다.");
        }
    }

    private void validateItems(CouponEventCreateRequest request) {
        Set<Long> couponTypeIds = new HashSet<>();
        Set<Integer> offsets = new HashSet<>();
        int previousOffset = -1;

        for (CouponEventItemCreateRequest item : request.items()) {
            if (!couponTypeIds.add(item.couponTypeId())) {
                invalid("한 이벤트에서 같은 쿠폰 종류를 중복 등록할 수 없습니다.");
            }
            if (!offsets.add(item.openOffsetSeconds())) {
                invalid("단계 오픈 시간은 중복될 수 없습니다.");
            }
            if (item.openOffsetSeconds() <= previousOffset) {
                invalid("쿠폰 단계는 오픈 시간이 빠른 순서로 입력해야 합니다.");
            }
            if (item.openOffsetSeconds() >= request.claimWindowSeconds()) {
                invalid("단계 오픈 시간은 신청 가능 시간보다 작아야 합니다.");
            }
            previousOffset = item.openOffsetSeconds();
        }

        if (request.items().getFirst().openOffsetSeconds() != 0) {
            invalid("첫 번째 쿠폰 단계는 이벤트 오픈 즉시 시작해야 합니다.");
        }
    }

    private void validateDuplicateTriggerEvent(CouponEventCreateRequest request) {
        if (request.openMode() != CouponEventOpenMode.GAME_TRIGGERED) {
            return;
        }
        if (couponEventRepository.existsByEsportsMatchIdAndTriggerType(
                request.esportsMatchId(),
                request.triggerType().trim()
        )) {
            throw new CouponEventException(
                    CouponEventErrorCode.COUPON_EVENT_DUPLICATED
            );
        }
    }

    private String normalizeTriggerType(String triggerType) {
        return triggerType == null ? null : triggerType.trim();
    }

    private void invalid(String message) {
        throw new CouponEventException(
                CouponEventErrorCode.INVALID_EVENT_CONFIGURATION,
                message
        );
    }
}
