package com.clutch.coupon.event.service;

import com.clutch.coupon.event.api.dto.CouponEventCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventDetailResponse;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventItemCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventItemDetailResponse;
import com.clutch.coupon.event.api.dto.CouponEventListResponse;
import com.clutch.coupon.event.api.dto.CouponEventOccurrenceResponse;
import com.clutch.coupon.event.api.dto.CouponEventSummaryResponse;
import com.clutch.coupon.event.api.dto.CouponEventUpdateRequest;
import com.clutch.coupon.event.api.dto.CouponEventUpdateResponse;
import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.domain.CouponEventOpenMode;
import com.clutch.coupon.event.domain.CouponEventPhase;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;
import com.clutch.coupon.event.exception.CouponEventErrorCode;
import com.clutch.coupon.event.exception.CouponEventException;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.event.repository.CouponEventPhaseRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponEventService {

    private final CouponEventRepository couponEventRepository;
    private final CouponEventItemRepository couponEventItemRepository;
    private final CouponEventPhaseRepository couponEventPhaseRepository;
    private final CouponEventOccurrenceRepository couponEventOccurrenceRepository;

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

    @Transactional
    public CouponEventUpdateResponse update(
            Long couponEventId,
            CouponEventUpdateRequest updateRequest
    ) {
        CouponEvent event = couponEventRepository.findById(couponEventId)
                .orElseThrow(() -> new CouponEventException(
                        CouponEventErrorCode.COUPON_EVENT_NOT_FOUND
                ));
        if (event.getEventStatus() != CouponEventStatus.READY) {
            throw new CouponEventException(
                    CouponEventErrorCode.COUPON_EVENT_NOT_EDITABLE
            );
        }

        CouponEventCreateRequest request = updateRequest.toCreateRequest();
        validateRequest(request);
        validateDuplicateTriggerEventForUpdate(couponEventId, request);

        event.updateConfiguration(
                request.esportsMatchId(),
                request.eventName(),
                request.openMode(),
                request.issueMode(),
                normalizeTriggerType(request.triggerType()),
                request.claimWindowSeconds(),
                request.scheduledOpenAt()
        );
        CouponEvent savedEvent = couponEventRepository.saveAndFlush(event);

        replaceItemsAndPhases(couponEventId);
        List<CouponEventItemCreateResponse> itemResponses =
                saveItemsAndPhases(couponEventId, request.items());

        return new CouponEventUpdateResponse(
                savedEvent.getId(),
                savedEvent.getEsportsMatchId(),
                savedEvent.getEventName(),
                savedEvent.getOpenMode(),
                savedEvent.getIssueMode(),
                savedEvent.getTriggerType(),
                savedEvent.getEventStatus(),
                savedEvent.getClaimWindowSeconds(),
                savedEvent.getScheduledOpenAt(),
                savedEvent.getUpdatedAt(),
                itemResponses
        );
    }

    private void replaceItemsAndPhases(Long couponEventId) {
        couponEventPhaseRepository.deleteAllByCouponEventId(couponEventId);
        couponEventPhaseRepository.flush();
        couponEventItemRepository.deleteAllByCouponEventId(couponEventId);
        couponEventItemRepository.flush();
    }

    @Transactional(readOnly = true)
    public CouponEventListResponse findAll(
            CouponEventStatus status,
            Long cursor,
            int size
    ) {
        validateListCondition(cursor, size);

        Slice<CouponEvent> eventSlice = findEventSlice(
                status,
                cursor,
                PageRequest.of(0, size)
        );
        List<CouponEvent> events = eventSlice.getContent();
        Map<Long, List<CouponEventItem>> itemsByEventId =
                findItemsByEventId(events);

        List<CouponEventSummaryResponse> responses = events.stream()
                .map(event -> toSummaryResponse(
                        event,
                        itemsByEventId.getOrDefault(event.getId(), List.of())
                ))
                .toList();
        Long nextCursor = eventSlice.hasNext() && !events.isEmpty()
                ? events.getLast().getId()
                : null;

        return new CouponEventListResponse(
                responses,
                nextCursor,
                eventSlice.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public CouponEventDetailResponse findById(Long couponEventId) {
        CouponEvent event = couponEventRepository.findById(couponEventId)
                .orElseThrow(() -> new CouponEventException(
                        CouponEventErrorCode.COUPON_EVENT_NOT_FOUND
                ));
        List<CouponEventItem> items =
                couponEventItemRepository.findAllByCouponEventId(couponEventId);
        Map<Long, CouponEventPhase> phaseByItemId =
                couponEventPhaseRepository
                        .findAllByCouponEventIdOrderByOpenOffsetSecondsAsc(
                                couponEventId
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                CouponEventPhase::getCouponEventItemId,
                                Function.identity()
                        ));

        List<CouponEventItemDetailResponse> itemResponses = items.stream()
                .sorted(Comparator.comparingInt(item -> {
                    CouponEventPhase phase = phaseByItemId.get(item.getId());
                    return phase == null
                            ? Integer.MAX_VALUE
                            : phase.getPhaseSequence();
                }))
                .map(item -> toItemDetailResponse(
                        item,
                        phaseByItemId.get(item.getId())
                ))
                .toList();
        long totalQuantity = totalQuantity(items);
        long issuedQuantity = issuedQuantity(items);
        CouponEventOccurrenceResponse latestOccurrence =
                couponEventOccurrenceRepository
                        .findFirstByCouponEventIdOrderByIdDesc(couponEventId)
                        .map(this::toOccurrenceResponse)
                        .orElse(null);

        return new CouponEventDetailResponse(
                event.getId(),
                event.getEsportsMatchId(),
                event.getEventName(),
                event.getOpenMode(),
                event.getIssueMode(),
                event.getTriggerType(),
                event.getEventStatus(),
                event.getClaimWindowSeconds(),
                event.getScheduledOpenAt(),
                totalQuantity,
                issuedQuantity,
                totalQuantity - issuedQuantity,
                event.getCreatedAt(),
                event.getUpdatedAt(),
                itemResponses,
                latestOccurrence
        );
    }

    private Slice<CouponEvent> findEventSlice(
            CouponEventStatus status,
            Long cursor,
            PageRequest pageable
    ) {
        if (status == null && cursor == null) {
            return couponEventRepository.findAllByOrderByIdDesc(pageable);
        }
        if (status == null) {
            return couponEventRepository.findByIdLessThanOrderByIdDesc(
                    cursor,
                    pageable
            );
        }
        if (cursor == null) {
            return couponEventRepository.findByEventStatusOrderByIdDesc(
                    status,
                    pageable
            );
        }
        return couponEventRepository
                .findByEventStatusAndIdLessThanOrderByIdDesc(
                        status,
                        cursor,
                        pageable
                );
    }

    private Map<Long, List<CouponEventItem>> findItemsByEventId(
            List<CouponEvent> events
    ) {
        if (events.isEmpty()) {
            return Map.of();
        }
        List<Long> eventIds = events.stream()
                .map(CouponEvent::getId)
                .toList();
        Map<Long, List<CouponEventItem>> result = new HashMap<>();
        couponEventItemRepository.findAllByCouponEventIdIn(eventIds)
                .forEach(item -> result
                        .computeIfAbsent(
                                item.getCouponEventId(),
                                ignored -> new ArrayList<>()
                        )
                        .add(item));
        return result;
    }

    private CouponEventSummaryResponse toSummaryResponse(
            CouponEvent event,
            List<CouponEventItem> items
    ) {
        long totalQuantity = totalQuantity(items);
        long issuedQuantity = issuedQuantity(items);
        return new CouponEventSummaryResponse(
                event.getId(),
                event.getEventName(),
                event.getEsportsMatchId(),
                event.getTriggerType(),
                event.getOpenMode(),
                event.getIssueMode(),
                event.getEventStatus(),
                event.getClaimWindowSeconds(),
                event.getScheduledOpenAt(),
                totalQuantity,
                issuedQuantity,
                totalQuantity - issuedQuantity,
                event.getCreatedAt()
        );
    }

    private CouponEventItemDetailResponse toItemDetailResponse(
            CouponEventItem item,
            CouponEventPhase phase
    ) {
        return new CouponEventItemDetailResponse(
                item.getId(),
                item.getCouponTypeId(),
                item.getQuantity(),
                item.getSuccessCount(),
                item.getQuantity() - item.getSuccessCount(),
                phase == null ? null : phase.getId(),
                phase == null ? null : phase.getPhaseSequence(),
                phase == null ? null : phase.getOpenOffsetSeconds()
        );
    }

    private CouponEventOccurrenceResponse toOccurrenceResponse(
            CouponEventOccurrence occurrence
    ) {
        return new CouponEventOccurrenceResponse(
                occurrence.getId(),
                occurrence.getMatchEventId(),
                occurrence.getSourceEventKey(),
                occurrence.getGameTimeSeconds(),
                occurrence.getSourceOccurredAt(),
                occurrence.getDetectedAt(),
                occurrence.getOpenedAt(),
                occurrence.getExpiresAt(),
                occurrence.getClosedAt(),
                occurrence.getOccurrenceStatus(),
                occurrence.getCloseReason()
        );
    }

    private long totalQuantity(List<CouponEventItem> items) {
        return items.stream()
                .mapToLong(CouponEventItem::getQuantity)
                .sum();
    }

    private long issuedQuantity(List<CouponEventItem> items) {
        return items.stream()
                .mapToLong(CouponEventItem::getSuccessCount)
                .sum();
    }

    private void validateListCondition(Long cursor, int size) {
        if (cursor != null && cursor <= 0) {
            invalid("커서는 양수여야 합니다.");
        }
        if (size < 1 || size > 100) {
            invalid("목록 크기는 1개 이상 100개 이하여야 합니다.");
        }
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

    private void validateDuplicateTriggerEventForUpdate(
            Long couponEventId,
            CouponEventCreateRequest request
    ) {
        if (request.openMode() != CouponEventOpenMode.GAME_TRIGGERED) {
            return;
        }
        if (couponEventRepository
                .existsByEsportsMatchIdAndTriggerTypeAndIdNot(
                        request.esportsMatchId(),
                        request.triggerType().trim(),
                        couponEventId
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
