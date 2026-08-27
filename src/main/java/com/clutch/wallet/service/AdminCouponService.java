package com.clutch.wallet.service;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.web.dto.CouponResponse;
import com.clutch.wallet.web.exception.CouponAlreadyCancelledException;
import com.clutch.wallet.web.exception.CouponAlreadyUsedException;
import com.clutch.wallet.web.exception.CouponCancelFailedException;
import com.clutch.wallet.web.exception.CouponExpiredException;
import com.clutch.wallet.web.exception.CouponNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 관리자의 쿠폰 취소 유스케이스를 처리하는 서비스.
 */
@Service
@Transactional
public class AdminCouponService {

    private final UserCouponRepository userCouponRepository;
    private final Clock clock;

    public AdminCouponService(
            UserCouponRepository userCouponRepository,
            Clock clock
    ){
        this.userCouponRepository = userCouponRepository;
        this.clock = clock;
    }

    /**
     * 관리자가 사용자 쿠폰을 취소한다.
     *
     * @param couponId 취소할 사용자 쿠폰 ID
     * @param reason 취소 사유
     * @return 취소된 쿠폰 정보
     */
    public CouponResponse cancel(Long couponId, String reason){
        Instant now = clock.instant();
        int updated = userCouponRepository.cancel(couponId, now, reason);

        if(updated == 0){
            handlerCancelFailure(couponId, now);
        }

        UserCoupon coupon = userCouponRepository.findById(couponId)
                .orElseThrow(CouponNotFoundException::new);
        return CouponResponse.from(coupon, now);
    }

    /**
     * 취소 실패 원인을 조회해 상태에 맞는 예외를 던진다.
     *
     * @param couponId 취소를 시도한 사용자 쿠폰 ID
     * @param now 취소 실패 상태를 판단할 UTC 기준 시각
     */
    private void handlerCancelFailure(Long couponId, Instant now){
        UserCoupon coupon = userCouponRepository.findById(couponId)
                .orElseThrow(CouponNotFoundException::new);

        UserCouponStatus effectiveStatus = coupon.getEffectiveStatus(now);
        if(effectiveStatus == UserCouponStatus.USED){
            throw new CouponAlreadyUsedException();
        }
        if(effectiveStatus == UserCouponStatus.CANCELLED){
            throw new CouponAlreadyCancelledException();
        }
        if(effectiveStatus == UserCouponStatus.EXPIRED){
            throw new CouponExpiredException();
        }
        throw new CouponCancelFailedException();
    }

}
