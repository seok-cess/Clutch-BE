package com.clutch.coupon.claim.repository;

import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.wallet.domain.UserCouponStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCouponClaimQueryRepositoryTest {

    private static final LocalDateTime STATUS_REFERENCE_TIME =
            LocalDateTime.of(2026, 8, 21, 12, 0);

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private AdminCouponClaimQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AdminCouponClaimQueryRepository(jdbcTemplate);
    }

    @Test
    void 필터가_없으면_claim_PK로_ID를_조회한_뒤_해당_ID만_상세_조인한다() {
        when(jdbcTemplate.queryForList(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        )).thenReturn(List.of(30L, 29L));
        when(jdbcTemplate.query(
                anyString(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers
                        .<RowMapper<AdminCouponClaimRow>>any()
        )).thenReturn(List.of());

        repository.findAll(emptyCondition(), 21);

        ArgumentCaptor<String> idQueryCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> idParametersCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForList(
                idQueryCaptor.capture(),
                idParametersCaptor.capture(),
                eq(Long.class)
        );
        assertThat(idQueryCaptor.getValue())
                .contains("FROM coupon_claim_request claim")
                .contains("ORDER BY claim.coupon_claim_request_id DESC")
                .contains("LIMIT :limit")
                .doesNotContain("JOIN coupon_event event")
                .doesNotContain("JOIN coupon_event_item filtered_item")
                .doesNotContain("JOIN user_coupon filtered_coupon");
        assertThat(idParametersCaptor.getValue().getValue("limit"))
                .isEqualTo(21);

        ArgumentCaptor<String> detailQueryCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> detailParametersCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(
                detailQueryCaptor.capture(),
                detailParametersCaptor.capture(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<AdminCouponClaimRow>>any()
        );
        assertThat(detailQueryCaptor.getValue())
                .contains("JOIN coupon_event event")
                .contains("JOIN coupon_event_item item")
                .contains("issued_coupon.expires_at <= :statusReferenceTime")
                .contains("THEN 'EXPIRED'")
                .contains("IN (:claimRequestIds)");
        assertThat(detailParametersCaptor.getValue()
                .getValue("claimRequestIds"))
                .isEqualTo(List.of(30L, 29L));
        assertThat(detailParametersCaptor.getValue()
                .getValue("statusReferenceTime"))
                .isEqualTo(STATUS_REFERENCE_TIME);
    }

    @Test
    void 조인_필터가_있을_때_ID_조회에_필요한_테이블만_추가한다() {
        when(jdbcTemplate.queryForList(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        )).thenReturn(List.of());
        AdminCouponClaimSearchCondition condition =
                new AdminCouponClaimSearchCondition(
                        null,
                        "펜타킬",
                        "PENTA",
                        null,
                        ClaimRequestStatus.FAILED,
                        UserCouponStatus.ISSUED,
                        10L,
                        null,
                        null,
                        null,
                        STATUS_REFERENCE_TIME
                );

        repository.findAll(condition, 21);

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parametersCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForList(
                queryCaptor.capture(),
                parametersCaptor.capture(),
                eq(Long.class)
        );
        assertThat(queryCaptor.getValue())
                .contains("JOIN coupon_event event")
                .contains("JOIN user_coupon filtered_coupon")
                .contains("JOIN coupon_event_item filtered_item")
                .contains("event.event_name LIKE :eventNameKeyword")
                .contains("event.trigger_type LIKE :triggerKeyword")
                .contains("filtered_coupon.coupon_status = 'ISSUED'")
                .contains("filtered_coupon.expires_at > :statusReferenceTime")
                .contains("filtered_item.coupon_type_id = :couponTypeId");
        assertThat(parametersCaptor.getValue().getValue("eventNameKeyword"))
                .isEqualTo("%펜타킬%");
        assertThat(parametersCaptor.getValue().getValue("triggerKeyword"))
                .isEqualTo("%PENTA%");
        assertThat(parametersCaptor.getValue().getValue("requestStatus"))
                .isEqualTo("FAILED");
        assertThat(parametersCaptor.getValue()
                .getValue("statusReferenceTime"))
                .isEqualTo(STATUS_REFERENCE_TIME);
    }

    @Test
    void EXPIRED_필터는_저장된_만료와_시간상_만료를_함께_조회한다() {
        when(jdbcTemplate.queryForList(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        )).thenReturn(List.of());
        AdminCouponClaimSearchCondition condition =
                new AdminCouponClaimSearchCondition(
                        null, null, null, null, null,
                        UserCouponStatus.EXPIRED,
                        null, null, null, null,
                        STATUS_REFERENCE_TIME
                );

        repository.findAll(condition, 21);

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(
                queryCaptor.capture(),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        );
        assertThat(queryCaptor.getValue())
                .contains("filtered_coupon.coupon_status = 'EXPIRED'")
                .contains("filtered_coupon.coupon_status = 'ISSUED'")
                .contains("filtered_coupon.expires_at <= :statusReferenceTime");
    }

    private AdminCouponClaimSearchCondition emptyCondition() {
        return new AdminCouponClaimSearchCondition(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                STATUS_REFERENCE_TIME
        );
    }
}
