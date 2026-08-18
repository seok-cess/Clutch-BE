package com.clutch.wallet.kafka;

import com.clutch.coupon.contract.kafka.CouponClaimAcceptedEvent;
import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import com.clutch.wallet.service.CouponIssuanceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CouponClaimAcceptedConsumer {

    private final CouponIssuanceService couponIssuanceService;
    private final ObjectMapper objectMapper;

    public CouponClaimAcceptedConsumer(CouponIssuanceService couponIssuanceService, ObjectMapper objectMapper){
        this.couponIssuanceService = couponIssuanceService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = CouponKafkaTopics.CLAIM_ACCEPTED, groupId = "coupon-wallet-issuer")
    public void onClaimAccepted(String payload){
        CouponClaimAcceptedEvent event = parse(payload);
        try{
            couponIssuanceService.issue(event);
        }catch(DataIntegrityViolationException e){

        }
    }

    private CouponClaimAcceptedEvent parse(String payload){
        try{
            return objectMapper.readValue(payload, CouponClaimAcceptedEvent.class);
        }catch(Exception e){
            throw new IllegalStateException("쿠폰 발급 이벤트 역직렬화 실패", e);
        }
    }
}
