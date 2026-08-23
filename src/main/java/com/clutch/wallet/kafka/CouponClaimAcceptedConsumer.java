package com.clutch.wallet.kafka;

import com.clutch.coupon.contract.kafka.CouponClaimAcceptedEvent;
import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import com.clutch.wallet.service.CouponIssuanceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 쿠폰 발급 접수(claim accepted) 이벤트를 구독해 실제 쿠폰 발급을 수행하는 Kafka 컨슈머.
 */
@Component
public class CouponClaimAcceptedConsumer {

    private final CouponIssuanceService couponIssuanceService;
    private final ObjectMapper objectMapper;

    public CouponClaimAcceptedConsumer(CouponIssuanceService couponIssuanceService, ObjectMapper objectMapper){
        this.couponIssuanceService = couponIssuanceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 쿠폰 발급 접수 이벤트를 받아 쿠폰을 발급한다.
     *
     * <p>claimId 유니크 제약 위반(중복 수신 등)은 무시한다.</p>
     *
     * @param payload 직렬화된 쿠폰 발급 접수 이벤트
     */
    @KafkaListener(topics = CouponKafkaTopics.CLAIM_ACCEPTED, groupId = "coupon-wallet-issuer")
    public void onClaimAccepted(String payload){
        CouponClaimAcceptedEvent event = parse(payload);
        try{
            couponIssuanceService.issue(event);
        }catch(DataIntegrityViolationException e){

        }
    }

    /**
     * Kafka 메시지 페이로드를 쿠폰 발급 접수 이벤트로 역직렬화한다.
     *
     * @param payload 직렬화된 이벤트 페이로드
     * @return 역직렬화된 쿠폰 발급 접수 이벤트
     */
    private CouponClaimAcceptedEvent parse(String payload){
        try{
            return objectMapper.readValue(payload, CouponClaimAcceptedEvent.class);
        }catch(Exception e){
            throw new IllegalStateException("쿠폰 발급 이벤트 역직렬화 실패", e);
        }
    }
}
