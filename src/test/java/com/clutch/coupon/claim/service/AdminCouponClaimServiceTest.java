package com.clutch.coupon.claim.service;

import com.clutch.common.privacy.PersonalDataMasker;
import com.clutch.coupon.claim.api.dto.AdminCouponClaimListResponse;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.repository.AdminCouponClaimQueryRepository;
import com.clutch.coupon.claim.repository.AdminCouponClaimRow;
import com.clutch.coupon.claim.repository.AdminCouponClaimSearchCondition;
import com.clutch.wallet.domain.UserCouponStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCouponClaimServiceTest {

    private static final Instant REFERENCE_TIME =
            Instant.parse("2026-08-21T12:00:00Z");

    @Mock
    private AdminCouponClaimQueryRepository queryRepository;

    private AdminCouponClaimService service;

    @BeforeEach
    void setUp() {
        service = new AdminCouponClaimService(
                queryRepository,
                new PersonalDataMasker(),
                Clock.fixed(REFERENCE_TIME, ZoneOffset.UTC)
        );
    }

    @Test
    void 트리거_문자열을_검색하고_번호형_페이지와_마스킹_결과를_반환한다() {
        when(queryRepository.findAll(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(2L)
        )).thenReturn(List.of(row(28L), row(27L)));
        when(queryRepository.count(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(5L);

        AdminCouponClaimListResponse response = service.findAll(
                "펜타킬 이벤트",
                "PENTA",
                null,
                ClaimRequestStatus.SUCCEEDED,
                UserCouponStatus.ISSUED,
                null,
                null,
                null,
                1,
                2
        );

        assertThat(response.claims()).hasSize(2);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasPrevious()).isTrue();
        assertThat(response.hasNext()).isTrue();
        assertThat(response.claims().getFirst().maskedName())
                .isEqualTo("김*정");
        assertThat(response.claims().getFirst().maskedEmail())
                .isEqualTo("use***@example.com");
        assertThat(response.claims().getFirst().maskedPhoneNumber())
                .isEqualTo("010-****-5678");

        ArgumentCaptor<AdminCouponClaimSearchCondition> captor =
                ArgumentCaptor.forClass(
                        AdminCouponClaimSearchCondition.class
                );
        verify(queryRepository).findAll(
                captor.capture(),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(2L)
        );
        assertThat(captor.getValue().eventNameKeyword())
                .isEqualTo("펜타킬 이벤트");
        assertThat(captor.getValue().triggerKeyword()).isEqualTo("PENTA");
        assertThat(captor.getValue().statusReferenceTime())
                .isEqualTo(LocalDateTime.ofInstant(
                        REFERENCE_TIME,
                        ZoneOffset.UTC
                ));
    }

    @Test
    void 숫자로만_입력한_이벤트_검색어는_ID_조건으로_변환한다() {
        when(queryRepository.findAll(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(0L)
        )).thenReturn(List.of());
        when(queryRepository.count(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(0L);

        service.findAll(
                "123",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20
        );

        ArgumentCaptor<AdminCouponClaimSearchCondition> captor =
                ArgumentCaptor.forClass(
                        AdminCouponClaimSearchCondition.class
                );
        verify(queryRepository).findAll(
                captor.capture(),
                org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(0L)
        );
        assertThat(captor.getValue().eventIdKeyword()).isEqualTo(123L);
        assertThat(captor.getValue().eventNameKeyword()).isNull();
    }

    @Test
    void DB에_존재하는_취소_요청과_만료_쿠폰_상태를_응답한다() {
        AdminCouponClaimRow row = row(30L);
        row = new AdminCouponClaimRow(
                row.claimRequestId(), row.requestedAt(), row.completedAt(),
                row.couponEventId(), row.eventName(), row.triggerType(),
                row.couponEventOccurrenceId(), row.userId(), row.userName(),
                row.userEmail(), row.userPhoneNumber(), row.couponTypeId(),
                row.couponName(), row.discountType(), row.discountValue(),
                "CANCELLED", row.failureReason(), row.userCouponId(),
                "EXPIRED"
        );
        when(queryRepository.findAll(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(0L)
        )).thenReturn(List.of(row));
        when(queryRepository.count(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1L);

        AdminCouponClaimListResponse response = service.findAll(
                null, null, null, null, null, null,
                null, null, 0, 20
        );

        assertThat(response.claims().getFirst().requestStatus())
                .isEqualTo(ClaimRequestStatus.CANCELLED);
        assertThat(response.claims().getFirst().couponStatus())
                .isEqualTo(UserCouponStatus.EXPIRED);
    }

    @Test
    void 페이지가_0_미만이면_발급_내역을_조회할_수_없다() {
        assertThatThrownBy(() -> service.findAll(
                null, null, null, null, null, null,
                null, null, -1, 20
        )).isInstanceOf(CouponClaimException.class);
    }

    private AdminCouponClaimRow row(Long claimRequestId) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 21, 12, 0);
        return new AdminCouponClaimRow(
                claimRequestId,
                now,
                now,
                10L,
                "펜타킬 이벤트",
                "PENTA_KILL",
                20L,
                30L,
                "김현정",
                "user001@example.com",
                "01012345678",
                40L,
                "20% 할인 쿠폰",
                "RATE",
                BigDecimal.valueOf(20),
                "SUCCEEDED",
                null,
                50L,
                "ISSUED"
        );
    }
}
