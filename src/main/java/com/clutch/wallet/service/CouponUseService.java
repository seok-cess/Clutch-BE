package com.clutch.wallet.service;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.web.dto.CouponResponse;
import com.clutch.wallet.web.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 사용자의 쿠폰 사용 유스케이스를 처리하는 서비스.
 */
@Service
@Transactional
public class CouponUseService {

    private final UserCouponRepository userCouponRepository;

    public CouponUseService(UserCouponRepository userCouponRepository){
        this.userCouponRepository = userCouponRepository;
    }

    /**
     * 사용자가 자신의 쿠폰을 사용 처리한다.
     *
     * @param userId 쿠폰을 사용하는 사용자 ID
     * @param couponId 사용할 사용자 쿠폰 ID
     * @return 사용 처리된 쿠폰 정보
     */
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

    /**
     * 사용 실패 원인을 조회해 상태에 맞는 예외를 던진다.
     *
     * @param userId 쿠폰을 사용하려던 사용자 ID
     * @param couponId 사용을 시도한 사용자 쿠폰 ID
     * @param now 실패 판단 기준 시각
     */
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
