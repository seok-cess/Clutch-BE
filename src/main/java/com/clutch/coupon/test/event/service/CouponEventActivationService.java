package com.clutch.coupon.test.event.service;

import com.clutch.coupon.claim.outbox.CouponBenefitSnapshotRepository;
import com.clutch.coupon.claim.redis.CouponStockInitializer;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponEventPhase;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventPhaseRepository;
import com.clutch.coupon.test.event.api.dto.CouponEventActivationResponse;
import com.clutch.coupon.test.event.domain.CouponEvent;
import com.clutch.coupon.test.event.domain.CouponEventOccurrence;
import com.clutch.coupon.test.event.domain.CouponEventTrigger;
import com.clutch.coupon.test.event.exception.CouponEventErrorCode;
import com.clutch.coupon.test.event.exception.CouponEventException;
import com.clutch.coupon.test.event.repository.CouponEventOccurrenceRepository;
import com.clutch.coupon.test.event.repository.CouponEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 관리자 수동 쿠폰 오픈과 사용자 테스트용 활성 회차 조회를 처리한다. */
@Service
@Slf4j
@RequiredArgsConstructor
public class CouponEventActivationService {

    private final CouponEventRepository couponEventRepository;
    private final CouponEventItemRepository couponEventItemRepository;
    private final CouponEventPhaseRepository couponEventPhaseRepository;
    private final CouponBenefitSnapshotRepository benefitSnapshotRepository;
    private final CouponEventOccurrenceRepository occurrenceRepository;
    private final CouponStockInitializer couponStockInitializer;
    private final CouponStockRecoveryStateManager recoveryStateManager;
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

        initializeStockAfterCommit(event.getId(), occurrence);

        return toResponse(event, occurrence, remainingQuantity, true);
    }

    /**
     * 경기 중 사건이 감지되어 그 트리거로 등록된 이벤트를 연다.
     *
     * 수동 오픈과 달리 이벤트 ID 를 받지 않는다 — 감지하는 쪽(경기 폴링, 샘플 재생)은
     * 어떤 이벤트가 이 트리거를 기다리는지 모르기 때문이다. (경기, 트리거) 로 찾아 연다.
     *
     * 경기를 반드시 함께 본다. 트리거만으로 찾으면 어느 경기의 펜타킬이든
     * 가장 오래된 PENTAKILL 이벤트를 열어버려 전혀 다른 경기의 이벤트가 발동한다.
     *
     * 같은 사건이 두 번 감지돼도 sourceEventKey 가 같아 유니크 제약이 중복 오픈을
     * 막는다. 그래서 재시도나 중복 호출에 안전하다.
     *
     * @param esportsMatchId 사건이 일어난 경기. 이 경기에 걸린 이벤트만 연다
     * @param externalGameId 사건이 일어난 세트. 중복 방지 키에 들어간다
     * @param gameTimeSeconds 경기 내 발생 시각(초)
     * @return 연 회차. 조건에 맞는 이벤트가 없거나 이미 열려 있으면 빈 값
     */
    @Transactional
    public Optional<CouponEventActivationResponse> openByTrigger(
            CouponEventTrigger trigger,
            Long esportsMatchId,
            String externalGameId,
            Integer gameTimeSeconds
    ) {
        if (trigger == null || esportsMatchId == null) {
            return Optional.empty();
        }

        Optional<CouponEvent> found = couponEventRepository
                .findReadyByMatchAndTriggerForUpdate(
                        esportsMatchId, trigger.name(), CouponEventStatus.READY
                );
        if (found.isEmpty()) {
            log.debug("경기 {} 트리거 {} 로 열 수 있는 대기 이벤트가 없다",
                    esportsMatchId, trigger);
            return Optional.empty();
        }
        CouponEvent event = found.get();
        LocalDateTime now = now();

        long remainingQuantity = remainingQuantity(event.getId());
        if (remainingQuantity <= 0L) {
            log.info("트리거 {} — 이벤트 {} 재고 소진으로 열지 않는다", trigger, event.getId());
            return Optional.empty();
        }

        event.open();
        CouponEventOccurrence occurrence = occurrenceRepository.save(
                CouponEventOccurrence.triggeredOpen(
                        event.getId(),
                        trigger,
                        externalGameId,
                        gameTimeSeconds,
                        now,
                        event.getClaimWindowSeconds()
                )
        );

        initializeStockAfterCommit(event.getId(), occurrence);
        log.info("트리거 {} 로 이벤트 {} 오픈 — matchId={} gameId={} gameTime={}s 재고={}",
                trigger, event.getId(), esportsMatchId, externalGameId,
                gameTimeSeconds, remainingQuantity);

        return Optional.of(toResponse(event, occurrence, remainingQuantity, true));
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
                claimable,
                phases(event.getId())
        );
    }

    /**
     * 발급 단계 목록을 오픈 시간 순으로 만든다.
     *
     * <p>화면은 이 목록으로 "지금 무엇을 받는지"와 "언제 혜택이 바뀌는지"를 그린다.
     * 단계 선택 규칙은 {@code CouponClaimContext.findActivePhase} 와 같다 —
     * {@code openOffsetSeconds <= 경과초} 중 가장 큰 단계 하나만 활성이다.</p>
     *
     * <p>혜택 스냅샷이 없는 단계는 목록에서 뺀다. 여기서 예외를 던지면 화면이
     * 통째로 열리지 않는데, 발급 자체는 Redis 컨텍스트로 동작하므로
     * 표시 정보 하나 때문에 이벤트를 막을 이유가 없다.</p>
     */
    private List<CouponEventActivationResponse.Phase> phases(
            Long couponEventId
    ) {
        List<CouponEventPhase> eventPhases = couponEventPhaseRepository
                .findAllByCouponEventIdOrderByOpenOffsetSecondsAsc(
                        couponEventId
                );
        if (eventPhases.isEmpty()) {
            return List.of();
        }

        Map<Long, CouponEventItem> itemsById = couponEventItemRepository
                .findAllByCouponEventId(couponEventId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        CouponEventItem::getId,
                        java.util.function.Function.identity()
                ));

        List<CouponEventActivationResponse.Phase> result =
                new ArrayList<>(eventPhases.size());
        for (CouponEventPhase phase : eventPhases) {
            Long itemId = phase.getCouponEventItemId();
            CouponEventItem item = itemsById.get(itemId);
            benefitSnapshotRepository
                    .findByCouponEventItemId(itemId)
                    .ifPresent(benefit -> result.add(
                            new CouponEventActivationResponse.Phase(
                                    itemId,
                                    phase.getOpenOffsetSeconds(),
                                    benefit.discountType(),
                                    benefit.discountValue(),
                                    item == null ? 0L : item.remainingStock(),
                                    item == null ? 0L : item.getQuantity()
                            )
                    ));
        }
        return List.copyOf(result);
    }

    /**
     * DB에 회차가 확정된 뒤에만 Redis 재고 키를 준비한다.
     *
     * <p>초기화 실패는 실제 Redis 장애로 취급해 이후 발급을 fail-closed로 막고,
     * 기존 복구 스케줄러가 DB 기준 재구축을 수행하게 한다.</p>
     */
    private void initializeStockAfterCommit(
            Long couponEventId,
            CouponEventOccurrence occurrence
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            initializeStock(couponEventId, occurrence);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        initializeStock(couponEventId, occurrence);
                    }
                }
        );
    }

    private void initializeStock(
            Long couponEventId,
            CouponEventOccurrence occurrence
    ) {
        try {
            couponStockInitializer.initialize(
                    couponEventId,
                    occurrence.getId(),
                    occurrence.getOpenedAt(),
                    occurrence.getExpiresAt()
            );
        } catch (DataAccessException exception) {
            recoveryStateManager.markUnavailable();
            throw exception;
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
