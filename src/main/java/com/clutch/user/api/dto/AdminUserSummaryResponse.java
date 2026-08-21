package com.clutch.user.api.dto;

import com.clutch.user.domain.PersonalDataMasker;
import com.clutch.user.domain.User;

import java.time.LocalDateTime;

/**
 * 관리자 화면에 내보내는 회원 정보.
 *
 * 이름·전화번호·이메일은 마스킹해서 담는다. 발급 내역에서 "누구인지" 가려내는 데는
 * 마스킹된 값으로 충분하고, 원본이 필요한 업무가 아직 없다. 원본을 내보내는 경로를
 * 만들려면 권한 분리와 조회 감사 로그가 함께 있어야 한다.
 *
 * @param userId 회원 식별자 — 개인정보가 아니라 그대로 내보낸다
 */
public record AdminUserSummaryResponse(
        Long userId,
        String role,
        String maskedName,
        String maskedPhoneNumber,
        String maskedEmail,
        long point,
        LocalDateTime createdAt
) {

    public static AdminUserSummaryResponse from(User user) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getRole() == null ? null : user.getRole().name(),
                PersonalDataMasker.name(user.getName()),
                PersonalDataMasker.phone(user.getPhoneNumber()),
                PersonalDataMasker.email(user.getEmail()),
                user.getPoint(),
                user.getCreatedAt()
        );
    }
}
