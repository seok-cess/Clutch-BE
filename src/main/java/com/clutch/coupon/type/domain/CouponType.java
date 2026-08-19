package com.clutch.coupon.type.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 이름과 할인 혜택을 정의하는 쿠폰 종류.
 */
@Getter
@Entity
@Table(name = "coupon_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponType {

    /** 쿠폰 종류 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_type_id", nullable = false)
    private Long id;

    /** 관리자와 사용자 화면에 표시할 쿠폰 이름. */
    @Column(name = "coupon_name", nullable = false, length = 100)
    private String couponName;

    /** 정률 또는 정액 할인 계산 방식. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private CouponDiscountType discountType;

    /** 정률 할인율 또는 정액 할인 금액. */
    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** 신규 쿠폰 이벤트에서 선택할 수 있는 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CouponTypeStatus status;

    /** 쿠폰 종류가 생성된 시각. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 쿠폰 종류가 마지막으로 수정된 시각. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private CouponType(
            String couponName,
            CouponDiscountType discountType,
            BigDecimal discountValue
    ) {
        validateDefinition(couponName, discountType, discountValue);
        this.couponName = couponName.trim();
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.status = CouponTypeStatus.ACTIVE;
    }

    /**
     * 활성 상태의 쿠폰 종류를 생성한다.
     *
     * @param couponName 쿠폰 이름
     * @param discountType 할인 계산 방식
     * @param discountValue 할인율 또는 할인 금액
     * @return 생성된 쿠폰 종류
     */
    public static CouponType create(
            String couponName,
            CouponDiscountType discountType,
            BigDecimal discountValue
    ) {
        return new CouponType(couponName, discountType, discountValue);
    }

    /**
     * 아직 사용되지 않은 쿠폰 종류의 혜택 정의를 변경한다.
     *
     * @param couponName 변경할 쿠폰 이름
     * @param discountType 변경할 할인 계산 방식
     * @param discountValue 변경할 할인율 또는 할인 금액
     */
    public void updateDefinition(
            String couponName,
            CouponDiscountType discountType,
            BigDecimal discountValue
    ) {
        validateDefinition(couponName, discountType, discountValue);
        this.couponName = couponName.trim();
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    /**
     * 신규 쿠폰 이벤트에서 사용할 수 있는 상태로 변경한다.
     */
    public void activate() {
        this.status = CouponTypeStatus.ACTIVE;
    }

    /**
     * 신규 쿠폰 이벤트에서 선택할 수 없는 상태로 변경한다.
     */
    public void deactivate() {
        this.status = CouponTypeStatus.INACTIVE;
    }

    /**
     * 신규 쿠폰 이벤트에서 선택 가능한지 확인한다.
     *
     * @return 활성 상태이면 {@code true}
     */
    public boolean isActive() {
        return status == CouponTypeStatus.ACTIVE;
    }

    private static void validateDefinition(
            String couponName,
            CouponDiscountType discountType,
            BigDecimal discountValue
    ) {
        if (couponName == null || couponName.isBlank()
                || couponName.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "쿠폰 이름은 1자 이상 100자 이하여야 합니다."
            );
        }
        if (discountType == null) {
            throw new IllegalArgumentException("할인 유형은 필수입니다.");
        }
        if (discountValue == null
                || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("할인 값은 0보다 커야 합니다.");
        }
        int fractionDigits = Math.max(discountValue.scale(), 0);
        int integerDigits = Math.max(
                discountValue.precision() - discountValue.scale(),
                0
        );
        if (integerDigits > 8 || fractionDigits > 2) {
            throw new IllegalArgumentException(
                    "할인 값은 정수 8자리와 소수 2자리 이하여야 합니다."
            );
        }
        if (discountType == CouponDiscountType.RATE
                && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "정률 할인 값은 100 이하여야 합니다."
            );
        }
    }
}
