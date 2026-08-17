package com.clutch.wallet.service;

import com.clutch.coupon.contract.kafka.CouponClaimAcceptedEvent;
import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.repository.UserCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CouponIssuanceService {

    private final UserCouponRepository userCouponRepository;

    public CouponIssuanceService(UserCouponRepository userCouponRepository){
        this.userCouponRepository = userCouponRepository;
    }

    @Transactional
    public void issue(CouponClaimAcceptedEvent event){
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
    }

    private String generateCouponCode(){
        return "CPN-" + UUID.randomUUID().toString().replace("-", "").
                substring(0, 12).toUpperCase();
    }
}
