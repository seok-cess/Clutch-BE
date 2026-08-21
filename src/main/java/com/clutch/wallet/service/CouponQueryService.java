package com.clutch.wallet.service;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.web.exception.CouponNotFoundException;
import com.clutch.wallet.web.dto.CouponPageResponse;
import com.clutch.wallet.web.dto.CouponResponse;
import com.clutch.wallet.web.exception.InvalidCouponQueryException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CouponQueryService {

    private final UserCouponRepository userCouponRepository;

    public CouponQueryService(UserCouponRepository userCouponRepository) {
        this.userCouponRepository = userCouponRepository;
    }

    public CouponPageResponse getMyCoupons(Long userId, UserCouponStatus status, Instant cursorExpiresAt, Long cursorId, int size) {
        List<UserCoupon> fetched = userCouponRepository.findPage(
                userId, status, cursorExpiresAt, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = fetched.size() > size;
        List<UserCoupon> page = hasNext ? fetched.subList(0, size) : fetched;

        String nextCursor = null;
        if(hasNext){
            UserCoupon last = page.get(page.size() - 1);
            nextCursor = last.getExpiresAt().toEpochMilli() + "_" + last.getId();
        }

        List<CouponResponse> items = page.stream().map(CouponResponse::from).toList();
        return new CouponPageResponse(items, nextCursor, hasNext);
    }

    public CouponResponse getMyCoupon(Long userId, Long couponId){
        UserCoupon coupon = userCouponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(CouponNotFoundException::new);
        return CouponResponse.from(coupon);
    }
}
