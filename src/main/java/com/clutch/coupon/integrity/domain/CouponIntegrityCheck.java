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

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "coupon_integrity_check")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIntegrityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_integrity_check_id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 20)
    private IntegrityExecutionStatus executionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_verdict", length = 20)
    private IntegrityVerdict overallVerdict;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "as_of_utc") private LocalDateTime asOfUtc;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "user_count") private Long userCount;
    @Column(name = "claim_request_count") private Long claimRequestCount;
    @Column(name = "user_coupon_count") private Long userCouponCount;
    @Column(name = "coupon_event_count") private Long couponEventCount;
    @Column(name = "occurrence_count") private Long occurrenceCount;
    @Column(name = "event_item_count") private Long eventItemCount;
    @Column(name = "claim_request_min_id") private Long claimRequestMinId;
    @Column(name = "claim_request_max_id") private Long claimRequestMaxId;
    @Column(name = "claim_request_fingerprint") private Long claimRequestFingerprint;
    @Column(name = "user_coupon_min_id") private Long userCouponMinId;
    @Column(name = "user_coupon_max_id") private Long userCouponMaxId;
    @Column(name = "user_coupon_fingerprint") private Long userCouponFingerprint;
    @Column(name = "check_count") private Long checkCount;
    @Column(name = "pass_count") private Long passCount;
    @Column(name = "info_count") private Long infoCount;
    @Column(name = "warn_count") private Long warnCount;
    @Column(name = "fail_count") private Long failCount;
    @Column(name = "error_code", length = 100) private String errorCode;
    @Column(name = "error_message", length = 500) private String errorMessage;

    public static CouponIntegrityCheck start(Long requestedBy, LocalDateTime startedAt) {
        CouponIntegrityCheck check = new CouponIntegrityCheck();
        check.executionStatus = IntegrityExecutionStatus.RUNNING;
        check.requestedBy = requestedBy;
        check.startedAt = startedAt;
        return check;
    }

    public void complete(CouponIntegritySnapshot snapshot, LocalDateTime completedAt) {
        this.executionStatus = IntegrityExecutionStatus.COMPLETED;
        this.overallVerdict = snapshot.overallVerdict();
        this.asOfUtc = snapshot.asOfUtc();
        this.completedAt = completedAt;
        this.userCount = snapshot.userCount();
        this.claimRequestCount = snapshot.claimRequestCount();
        this.userCouponCount = snapshot.userCouponCount();
        this.couponEventCount = snapshot.couponEventCount();
        this.occurrenceCount = snapshot.occurrenceCount();
        this.eventItemCount = snapshot.eventItemCount();
        this.claimRequestMinId = snapshot.claimRequestFingerprint().minId();
        this.claimRequestMaxId = snapshot.claimRequestFingerprint().maxId();
        this.claimRequestFingerprint = snapshot.claimRequestFingerprint().fingerprint();
        this.userCouponMinId = snapshot.userCouponFingerprint().minId();
        this.userCouponMaxId = snapshot.userCouponFingerprint().maxId();
        this.userCouponFingerprint = snapshot.userCouponFingerprint().fingerprint();
        this.checkCount = (long) snapshot.results().size();
        this.passCount = snapshot.count(IntegrityVerdict.PASS);
        this.infoCount = snapshot.count(IntegrityVerdict.INFO);
        this.warnCount = snapshot.count(IntegrityVerdict.WARN);
        this.failCount = snapshot.count(IntegrityVerdict.FAIL);
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void fail(String errorCode, String errorMessage, LocalDateTime completedAt) {
        if (executionStatus != IntegrityExecutionStatus.RUNNING) {
            return;
        }
        this.executionStatus = IntegrityExecutionStatus.FAILED;
        this.overallVerdict = null;
        this.completedAt = completedAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
