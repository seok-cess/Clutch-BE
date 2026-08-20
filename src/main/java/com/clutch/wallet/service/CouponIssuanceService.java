package com.clutch.wallet.service;

import com.clutch.coupon.contract.issuance.CouponIssuanceCommand;
import com.clutch.coupon.contract.issuance.CouponIssuanceResult;
import com.clutch.coupon.contract.issuance.CouponIssuanceRecoveryReader;
import com.clutch.coupon.contract.issuance.CouponIssuer;
import com.clutch.coupon.contract.kafka.CouponClaimAcceptedEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultStatus;
import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.WalletOutbox;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.repository.WalletOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * 쿠폰 생성 서비스
 */
@Service
public class CouponIssuanceService implements
        CouponIssuer,
        CouponIssuanceRecoveryReader {

    private static final int EVENT_VERSION = 1;

    private final UserCouponRepository userCouponRepository;
    private final WalletOutboxRepository walletOutboxRepository;
    private final ObjectMapper objectMapper;

    public CouponIssuanceService(
            UserCouponRepository userCouponRepository,
            WalletOutboxRepository walletOutboxRepository,
            ObjectMapper objectMapper
    ) {
        this.userCouponRepository = userCouponRepository;
        this.walletOutboxRepository = walletOutboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 동기 쿠폰 발급
     *
     * @param command 쿠폰 발급 명령
     * @return 쿠폰 발급 결과
     */
    @Override
    @Transactional
    public CouponIssuanceResult issue(
            CouponIssuanceCommand command
    ) {
        UserCoupon coupon = new UserCoupon(
                command.claimId(),
                command.userId(),
                command.couponEventId(),
                command.couponEventOccurrenceId(),
                command.couponEventItemId(),
                generateCouponCode(),
                command.discountType(),
                command.discountValue(),
                command.expiresAt()
        );

        UserCoupon savedCoupon =
                userCouponRepository.saveAndFlush(coupon);

        writeResultOutbox(
                command.claimId(),
                savedCoupon.getId(),
                CouponIssueResultStatus.SUCCEEDED,
                null
        );

        return new CouponIssuanceResult(
                savedCoupon.getId()
        );
    }

    /** 쿠폰 이벤트 항목별 실제 발급 수량 */
    @Override
    @Transactional(readOnly = true)
    public long countIssuedCoupons(Long couponEventItemId) {
        return userCouponRepository.countByCouponEventItemId(
                couponEventItemId
        );
    }

    /** 쿠폰 이벤트 회차별 실제 발급 사용자 목록 */
    @Override
    @Transactional(readOnly = true)
    public java.util.List<Long> findIssuedUserIds(
            Long couponEventOccurrenceId
    ) {
        return userCouponRepository.findUserIdsByOccurrenceId(
                couponEventOccurrenceId
        );
    }

    /**
     * Kafka 쿠폰 발급 이벤트 호환 처리
     *
     * @param event 쿠폰 발급 접수 이벤트
     */
    @Transactional
    public void issue(
            CouponClaimAcceptedEvent event
    ) {
        issue(
                new CouponIssuanceCommand(
                        event.claimId(),
                        event.userId(),
                        event.couponEventId(),
                        event.couponEventOccurrenceId(),
                        event.couponEventItemId(),
                        event.discountType(),
                        event.discountValue(),
                        event.expiresAt()
                )
        );
    }

    /**
     * 쿠폰 생성 결과 Outbox 저장
     *
     * @param claimId 쿠폰 발급 요청 식별자
     * @param couponId 발급 쿠폰 식별자
     * @param status 쿠폰 생성 결과 상태
     * @param failureReason 쿠폰 생성 실패 사유
     */
    private void writeResultOutbox(
            Long claimId,
            Long couponId,
            CouponIssueResultStatus status,
            String failureReason
    ) {
        CouponIssueResultEvent resultEvent =
                new CouponIssueResultEvent(
                        EVENT_VERSION,
                        UUID.randomUUID().toString(),
                        claimId,
                        couponId,
                        status,
                        failureReason,
                        Instant.now()
                );

        WalletOutbox outbox = WalletOutbox.create(
                claimId,
                CouponKafkaTopics.ISSUE_RESULT,
                serialize(resultEvent)
        );

        walletOutboxRepository.save(outbox);
    }

    /**
     * 쿠폰 코드 생성
     *
     * @return 쿠폰 코드
     */
    private String generateCouponCode() {
        return "CPN-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    /**
     * 결과 이벤트 직렬화
     *
     * @param event 쿠폰 생성 결과 이벤트
     * @return 이벤트 JSON
     */
    private String serialize(
            CouponIssueResultEvent event
    ) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "발급 결과 이벤트 직렬화 실패",
                    exception
            );
        }
    }
}
