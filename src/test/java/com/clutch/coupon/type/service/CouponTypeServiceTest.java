package com.clutch.coupon.type.service;

import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.type.api.dto.CouponTypeCreateRequest;
import com.clutch.coupon.type.api.dto.CouponTypeListResponse;
import com.clutch.coupon.type.api.dto.CouponTypeOptionListResponse;
import com.clutch.coupon.type.api.dto.CouponTypeResponse;
import com.clutch.coupon.type.api.dto.CouponTypeUpdateRequest;
import com.clutch.coupon.type.domain.CouponDiscountType;
import com.clutch.coupon.type.domain.CouponType;
import com.clutch.coupon.type.domain.CouponTypeStatus;
import com.clutch.coupon.type.exception.CouponTypeErrorCode;
import com.clutch.coupon.type.exception.CouponTypeException;
import com.clutch.coupon.type.repository.CouponTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponTypeServiceTest {

    @Mock
    private CouponTypeRepository couponTypeRepository;

    @Mock
    private CouponEventItemRepository couponEventItemRepository;

    private CouponTypeService couponTypeService;

    @BeforeEach
    void setUp() {
        couponTypeService = new CouponTypeService(
                couponTypeRepository,
                couponEventItemRepository
        );
    }

    @Test
    void 정률_쿠폰_종류를_등록한다() {
        CouponTypeCreateRequest request = new CouponTypeCreateRequest(
                "20% 할인 쿠폰",
                CouponDiscountType.RATE,
                BigDecimal.valueOf(20)
        );
        when(couponTypeRepository.save(any(CouponType.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        CouponTypeResponse response = couponTypeService.create(request);

        assertThat(response.couponTypeId()).isEqualTo(1L);
        assertThat(response.discountType())
                .isEqualTo(CouponDiscountType.RATE);
        assertThat(response.discountValue()).isEqualByComparingTo("20");
        assertThat(response.status()).isEqualTo(CouponTypeStatus.ACTIVE);
        assertThat(response.used()).isFalse();
    }

    @Test
    void 상태별_쿠폰_종류와_사용_여부를_조회한다() {
        CouponType first = couponType(1L, "10% 할인 쿠폰");
        CouponType second = couponType(2L, "20% 할인 쿠폰");
        PageRequest pageable = PageRequest.of(0, 20);
        when(couponTypeRepository.findByStatusOrderByIdDesc(
                CouponTypeStatus.ACTIVE,
                pageable
        )).thenReturn(new SliceImpl<>(
                List.of(second, first),
                pageable,
                true
        ));
        when(couponEventItemRepository.findUsedCouponTypeIds(List.of(2L, 1L)))
                .thenReturn(Set.of(1L));

        CouponTypeListResponse response = couponTypeService.findAll(
                CouponTypeStatus.ACTIVE,
                null,
                20
        );

        assertThat(response.couponTypes()).hasSize(2);
        assertThat(response.couponTypes().get(0).used()).isFalse();
        assertThat(response.couponTypes().get(1).used()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(1L);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void 커서보다_작은_쿠폰_종류를_다음_페이지로_조회한다() {
        CouponType couponType = couponType(7L, "10% 할인 쿠폰");
        PageRequest pageable = PageRequest.of(0, 10);
        when(couponTypeRepository.findByIdLessThanOrderByIdDesc(
                10L,
                pageable
        )).thenReturn(new SliceImpl<>(List.of(couponType), pageable, false));
        when(couponEventItemRepository.findUsedCouponTypeIds(List.of(7L)))
                .thenReturn(Set.of());

        CouponTypeListResponse response = couponTypeService.findAll(
                null,
                10L,
                10
        );

        assertThat(response.couponTypes())
                .extracting(CouponTypeResponse::couponTypeId)
                .containsExactly(7L);
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 이벤트_생성용_쿠폰은_활성_상태와_이름으로_검색한다() {
        CouponType couponType = couponType(3L, "20% 할인 쿠폰");
        PageRequest pageable = PageRequest.of(0, 20);
        when(couponTypeRepository
                .findByStatusAndCouponNameContainingIgnoreCaseOrderByIdDesc(
                        CouponTypeStatus.ACTIVE,
                        "20%",
                        pageable
                )).thenReturn(new SliceImpl<>(
                        List.of(couponType),
                        pageable,
                        false
                ));

        CouponTypeOptionListResponse response = couponTypeService.findOptions(
                " 20% ",
                null,
                20
        );

        assertThat(response.options()).hasSize(1);
        assertThat(response.options().getFirst().couponTypeId()).isEqualTo(3L);
        assertThat(response.options().getFirst().couponName())
                .isEqualTo("20% 할인 쿠폰");
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 목록_크기가_허용_범위를_벗어나면_조회할_수_없다() {
        assertThatThrownBy(() -> couponTypeService.findAll(null, null, 101))
                .isInstanceOfSatisfying(
                        CouponTypeException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponTypeErrorCode
                                        .INVALID_COUPON_TYPE_LIST_CONDITION)
                );
    }

    @Test
    void 사용되지_않은_쿠폰_종류의_혜택을_수정한다() {
        CouponType couponType = couponType(1L, "10% 할인 쿠폰");
        when(couponTypeRepository.findById(1L))
                .thenReturn(Optional.of(couponType));
        when(couponTypeRepository.saveAndFlush(couponType))
                .thenReturn(couponType);

        CouponTypeResponse response = couponTypeService.update(
                1L,
                new CouponTypeUpdateRequest(
                        "5천원 할인 쿠폰",
                        CouponDiscountType.AMOUNT,
                        BigDecimal.valueOf(5_000)
                )
        );

        assertThat(response.couponName()).isEqualTo("5천원 할인 쿠폰");
        assertThat(response.discountType())
                .isEqualTo(CouponDiscountType.AMOUNT);
        assertThat(response.discountValue()).isEqualByComparingTo("5000");
    }

    @Test
    void 사용된_쿠폰_종류의_혜택은_수정할_수_없다() {
        CouponType couponType = couponType(1L, "10% 할인 쿠폰");
        when(couponTypeRepository.findById(1L))
                .thenReturn(Optional.of(couponType));
        when(couponEventItemRepository.existsByCouponTypeId(1L))
                .thenReturn(true);

        assertThatThrownBy(() -> couponTypeService.update(
                1L,
                new CouponTypeUpdateRequest(
                        "20% 할인 쿠폰",
                        CouponDiscountType.RATE,
                        BigDecimal.valueOf(20)
                )
        )).isInstanceOfSatisfying(
                CouponTypeException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CouponTypeErrorCode.COUPON_TYPE_NOT_EDITABLE)
        );

        verify(couponTypeRepository, never()).saveAndFlush(any());
    }

    @Test
    void 사용된_쿠폰_종류도_비활성화할_수_있다() {
        CouponType couponType = couponType(1L, "10% 할인 쿠폰");
        when(couponTypeRepository.findById(1L))
                .thenReturn(Optional.of(couponType));
        when(couponTypeRepository.saveAndFlush(couponType))
                .thenReturn(couponType);
        when(couponEventItemRepository.existsByCouponTypeId(1L))
                .thenReturn(true);

        CouponTypeResponse response = couponTypeService.changeStatus(
                1L,
                CouponTypeStatus.INACTIVE
        );

        assertThat(response.status()).isEqualTo(CouponTypeStatus.INACTIVE);
        assertThat(response.used()).isTrue();
    }

    @Test
    void 사용되지_않은_쿠폰_종류를_물리_삭제한다() {
        CouponType couponType = couponType(1L, "삭제 쿠폰");
        when(couponTypeRepository.findById(1L))
                .thenReturn(Optional.of(couponType));

        couponTypeService.delete(1L);

        verify(couponTypeRepository).delete(couponType);
        verify(couponTypeRepository).flush();
    }

    @Test
    void 사용된_쿠폰_종류는_물리_삭제할_수_없다() {
        CouponType couponType = couponType(1L, "사용된 쿠폰");
        when(couponTypeRepository.findById(1L))
                .thenReturn(Optional.of(couponType));
        when(couponEventItemRepository.existsByCouponTypeId(1L))
                .thenReturn(true);

        assertThatThrownBy(() -> couponTypeService.delete(1L))
                .isInstanceOfSatisfying(
                        CouponTypeException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        CouponTypeErrorCode.COUPON_TYPE_NOT_DELETABLE
                                )
                );

        verify(couponTypeRepository, never()).delete(any());
    }

    @Test
    void 존재하지_않는_쿠폰_종류는_조회할_수_없다() {
        when(couponTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponTypeService.findById(99L))
                .isInstanceOfSatisfying(
                        CouponTypeException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponTypeErrorCode.COUPON_TYPE_NOT_FOUND)
                );
    }

    private CouponType couponType(Long id, String name) {
        return withId(CouponType.create(
                name,
                CouponDiscountType.RATE,
                BigDecimal.TEN
        ), id);
    }

    private <T> T withId(T target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
        return target;
    }
}
