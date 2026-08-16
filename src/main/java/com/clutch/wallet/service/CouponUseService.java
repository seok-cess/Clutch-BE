package com.clutch.wallet.service;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.web.dto.CouponResponse;
import com.clutch.wallet.web.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class CouponUseService {

    private final UserCouponRepository userCouponRepository;

    public CouponUseService(UserCouponRepository userCouponRepository){
        this.userCouponRepository = userCouponRepository;
    }

    public CouponResponse use(Long userId, Long couponId){
        Instant now = Instant.now();
        int updated = userCouponRepository.markAsUsed(couponId, userId, now);

        if(updated == 0){
            handlerFailure(userId, couponId, now);
        }

        UserCoupon coupon = userCouponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(CouponNotFoundException::new);
        return CouponResponse.from(coupon);
    }

    private void handlerFailure(Long userId, Long couponId, Instant now){
        UserCoupon coupon = userCouponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(CouponNotFoundException::new);

        if(coupon.getStatus() == UserCouponStatus.USED){
            throw new CouponAlreadyUsedException();
        }
        if(coupon.getStatus() == UserCouponStatus.CANCELLED){
            throw new CouponAlreadyCancelledException();
        }
        if(!coupon.getExpiresAt().isAfter(now)){
            throw new CouponExpiredException();
        }
        throw new CouponUseFailedException();
    }

}
