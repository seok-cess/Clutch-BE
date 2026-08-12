package com.clutch.coupon.claim.service;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateRequest;
import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.event.domain.CouponEvent;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 발급 요청 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponClaimServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long COUPON_EVENT_ID = 10L;
    private static final Long COUPON_EVENT_ITEM_ID = 20L;

    @Mock
    private CouponEventRepository couponEventRepository;

    @Mock
    private CouponEventItemRepository couponEventItemRepository;

    @Mock
    private CouponClaimRequestRepository couponClaimRequestRepository;

    @Mock
    private CouponEvent couponEvent;

    @Mock
    private CouponEventItem couponEventItem;

    @InjectMocks
    private CouponClaimService couponClaimService;

    /**
     * 정상 쿠폰 발급 요청 검증
     */
    @Test
    void claimSucceeds() {
        // given
        givenOpenEventAndItem();

        when(couponClaimRequestRepository
                .existsByUserIdAndCouponEventItemId(
                        USER_ID,
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(false);

        when(couponEventItem.hasRemainingStock())
                .thenReturn(true);

        when(couponClaimRequestRepository
                .save(any(CouponClaimRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CouponClaimCreateRequest request =
                new CouponClaimCreateRequest(
                        COUPON_EVENT_ITEM_ID
                );

        // when
        CouponClaimCreateResponse response =
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        request
                );

        // then
        assertThat(response.couponEventId())
                .isEqualTo(COUPON_EVENT_ID);
        assertThat(response.couponEventItemId())
                .isEqualTo(COUPON_EVENT_ITEM_ID);
        assertThat(response.requestStatus())
                .isEqualTo(ClaimRequestStatus.SUCCEEDED);

        ArgumentCaptor<CouponClaimRequest> captor =
                ArgumentCaptor.forClass(
                        CouponClaimRequest.class
                );

        verify(couponClaimRequestRepository)
                .save(captor.capture());

        CouponClaimRequest savedClaimRequest =
                captor.getValue();

        assertThat(savedClaimRequest.getUserId())
                .isEqualTo(USER_ID);
        assertThat(savedClaimRequest.getRequestStatus())
                .isEqualTo(ClaimRequestStatus.SUCCEEDED);

        verify(couponEventItem).increaseSuccessCount();
    }

    /**
     * 미존재 쿠폰 이벤트 실패 검증
     */
    @Test
    void claimFailsWhenEventDoesNotExist() {
        // given
        when(couponEventRepository.findById(COUPON_EVENT_ID))
                .thenReturn(Optional.empty());

        CouponClaimCreateRequest request =
                new CouponClaimCreateRequest(
                        COUPON_EVENT_ITEM_ID
                );

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        request
                ))
                .isInstanceOf(IllegalArgumentException.class);

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * 미존재 쿠폰 이벤트 항목 실패 검증
     */
    @Test
    void claimFailsWhenEventItemDoesNotExist() {
        // given
        when(couponEventRepository.findById(COUPON_EVENT_ID))
                .thenReturn(Optional.of(couponEvent));

        when(couponEventItemRepository
                .findByCouponEventIdAndId(
                        COUPON_EVENT_ID,
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(Optional.empty());

        CouponClaimCreateRequest request =
                new CouponClaimCreateRequest(
                        COUPON_EVENT_ITEM_ID
                );

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        request
                ))
                .isInstanceOf(IllegalArgumentException.class);

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * 종료 쿠폰 이벤트 실패 검증
     */
    @Test
    void claimFailsWhenEventIsClosed() {
        // given
        when(couponEventRepository.findById(COUPON_EVENT_ID))
                .thenReturn(Optional.of(couponEvent));

        when(couponEventItemRepository
                .findByCouponEventIdAndId(
                        COUPON_EVENT_ID,
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(Optional.of(couponEventItem));

        when(couponEvent.isOpenAt(any(LocalDateTime.class)))
                .thenReturn(false);

        CouponClaimCreateRequest request =
                new CouponClaimCreateRequest(
                        COUPON_EVENT_ITEM_ID
                );

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        request
                ))
                .isInstanceOf(IllegalStateException.class);

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * 중복 쿠폰 발급 요청 실패 검증
     */
    @Test
    void claimFailsWhenAlreadyClaimed() {
        // given
        givenOpenEventAndItem();

        when(couponClaimRequestRepository
                .existsByUserIdAndCouponEventItemId(
                        USER_ID,
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(true);

        CouponClaimCreateRequest request =
                new CouponClaimCreateRequest(
                        COUPON_EVENT_ITEM_ID
                );

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        request
                ))
                .isInstanceOf(IllegalStateException.class);

        verify(couponEventItem, never())
                .increaseSuccessCount();

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * 쿠폰 재고 소진 실패 검증
     */
    @Test
    void claimFailsWhenStockIsEmpty() {
        // given
        givenOpenEventAndItem();

        when(couponClaimRequestRepository
                .existsByUserIdAndCouponEventItemId(
                        USER_ID,
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(false);

        when(couponEventItem.hasRemainingStock())
                .thenReturn(false);

        CouponClaimCreateRequest request =
                new CouponClaimCreateRequest(
                        COUPON_EVENT_ITEM_ID
                );

        // when, then
        assertThatThrownBy(() ->
                couponClaimService.claim(
                        USER_ID,
                        COUPON_EVENT_ID,
                        request
                ))
                .isInstanceOf(IllegalStateException.class);

        verify(couponEventItem, never())
                .increaseSuccessCount();

        verify(couponClaimRequestRepository, never())
                .save(any(CouponClaimRequest.class));
    }

    /**
     * 진행 중 쿠폰 이벤트 및 항목 조건
     */
    private void givenOpenEventAndItem() {
        when(couponEventRepository.findById(COUPON_EVENT_ID))
                .thenReturn(Optional.of(couponEvent));

        when(couponEventItemRepository
                .findByCouponEventIdAndId(
                        COUPON_EVENT_ID,
                        COUPON_EVENT_ITEM_ID
                ))
                .thenReturn(Optional.of(couponEventItem));

        when(couponEvent.isOpenAt(any(LocalDateTime.class)))
                .thenReturn(true);
    }
}