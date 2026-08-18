package com.clutch.wallet.service;

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

@Service
public class CouponIssuanceService {

    private static final int EVENT_VERSION = 1;

    private final UserCouponRepository userCouponRepository;
    private final WalletOutboxRepository walletOutboxRepository;
    private final ObjectMapper objectMapper;

    public CouponIssuanceService(UserCouponRepository userCouponRepository,
                                 WalletOutboxRepository walletOutboxRepository,
                                 ObjectMapper objectMapper) {
        this.userCouponRepository = userCouponRepository;
        this.walletOutboxRepository = walletOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void issue(CouponClaimAcceptedEvent event) {
        UserCoupon coupon = new UserCoupon(
                event.claimId(),
                event.userId(),
                event.couponEventId(),
                event.couponEventOccurrenceId(),
                event.couponEventItemId(),
                generateCouponCode(),
                event.discountType(),
                event.discountValue(),
                event.expiresAt()
        );
        userCouponRepository.saveAndFlush(coupon);

        writeResultOutbox(event.claimId(), coupon.getId(), CouponIssueResultStatus.SUCCEEDED, null);
    }

    private void writeResultOutbox(Long claimId, Long couponId, CouponIssueResultStatus status, String failureReason) {
        CouponIssueResultEvent resultEvent = new CouponIssueResultEvent(
                EVENT_VERSION,
                UUID.randomUUID().toString(),
                claimId,
                couponId,
                status,
                failureReason,
                Instant.now()
        );
        WalletOutbox outbox = WalletOutbox.create(claimId, CouponKafkaTopics.ISSUE_RESULT, serialize(resultEvent));
        walletOutboxRepository.save(outbox);
    }

    private String generateCouponCode() {
        return "CPN-" + UUID.randomUUID().toString().replace("-", "").
                substring(0, 12).toUpperCase();
    }

    private String serialize(CouponIssueResultEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("발급 결과 이벤트 직렬화 실패", e);
        }
    }
}
