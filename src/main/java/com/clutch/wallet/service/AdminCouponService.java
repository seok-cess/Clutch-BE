package com.clutch.wallet.service;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.web.dto.CouponResponse;
import com.clutch.wallet.web.exception.CouponAlreadyCancelledException;
import com.clutch.wallet.web.exception.CouponAlreadyUsedException;
import com.clutch.wallet.web.exception.CouponCancelFailedException;
import com.clutch.wallet.web.exception.CouponNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class AdminCouponService {

    private final UserCouponRepository userCouponRepository;

    public AdminCouponService(UserCouponRepository userCouponRepository){
        this.userCouponRepository = userCouponRepository;
    }

    public CouponResponse cancel(Long couponId, String reason){
        Instant now = Instant.now();
        int updated = userCouponRepository.cancel(couponId, now, reason);

        if(updated == 0){
            handlerCancelFailure(couponId);
        }

        UserCoupon coupon = userCouponRepository.findById(couponId)
                .orElseThrow(CouponNotFoundException::new);
        return CouponResponse.from(coupon);
    }

    private void handlerCancelFailure(Long couponId){
        UserCoupon coupon = userCouponRepository.findById(couponId)
                .orElseThrow(CouponNotFoundException::new);

        if(coupon.getStatus() == UserCouponStatus.USED){
            throw new CouponAlreadyUsedException();
        }
        if(coupon.getStatus() == UserCouponStatus.CANCELLED){
            throw new CouponAlreadyCancelledException();
        }
        throw new CouponCancelFailedException();
    }

}
