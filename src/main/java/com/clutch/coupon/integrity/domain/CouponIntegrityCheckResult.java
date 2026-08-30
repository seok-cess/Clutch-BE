package com.clutch.coupon.integrity.domain;

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

@Getter
@Entity
@Table(name = "coupon_integrity_check_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIntegrityCheckResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_integrity_check_result_id", nullable = false)
    private Long id;
    @Column(name = "coupon_integrity_check_id", nullable = false)
    private Long checkId;
    @Column(name = "check_code", nullable = false, length = 100)
    private String checkCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private IntegrityVerdict severity;
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false, length = 20)
    private IntegrityVerdict verdict;
    @Column(name = "violation_count", nullable = false)
    private long violationCount;
    @Column(name = "description", nullable = false, length = 500)
    private String description;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static CouponIntegrityCheckResult from(Long checkId, CouponIntegrityResult result) {
        CouponIntegrityCheckResult entity = new CouponIntegrityCheckResult();
        entity.checkId = checkId;
        entity.checkCode = result.checkCode();
        entity.severity = result.severity();
        entity.verdict = result.verdict();
        entity.violationCount = result.violationCount();
        entity.description = result.description();
        entity.displayOrder = result.displayOrder();
        return entity;
    }
}
