package com.clutch.wallet.service;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import com.clutch.wallet.repository.UserCouponRepository;
import com.clutch.wallet.web.exception.CouponNotFoundException;
import com.clutch.wallet.web.dto.CouponPageResponse;
import com.clutch.wallet.web.dto.CouponResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 사용자 쿠폰 목록 및 단건 조회를 담당하는 서비스.
 */
@Service
@Transactional(readOnly = true)
public class CouponQueryService {

    private final UserCouponRepository userCouponRepository;

    public CouponQueryService(UserCouponRepository userCouponRepository) {
        this.userCouponRepository = userCouponRepository;
    }

    /**
     * 커서 기반 페이징으로 사용자의 쿠폰 목록을 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @param status 조회할 쿠폰 상태, 전체 조회 시 {@code null}
     * @param cursor 이전 페이지의 마지막 커서, 첫 조회 시 {@code null}
     * @param size 한 번에 조회할 쿠폰 수
     * @return 쿠폰 목록과 다음 커서 정보
     */
    public CouponPageResponse getMyCoupons(Long userId, UserCouponStatus status, String cursor, int size) {
        Instant cursorExpiresAt = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("_", 2);
            cursorExpiresAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            cursorId = Long.valueOf(parts[1]);
        }

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

    /**
     * 사용자 소유의 쿠폰을 단건 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @param couponId 조회할 쿠폰 ID
     * @return 조회된 쿠폰 정보
     */
    public CouponResponse getMyCoupon(Long userId, Long couponId){
        UserCoupon coupon = userCouponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(CouponNotFoundException::new);
        return CouponResponse.from(coupon);
    }
}
