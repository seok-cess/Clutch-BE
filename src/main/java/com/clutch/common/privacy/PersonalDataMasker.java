package com.clutch.common.privacy;

import org.springframework.stereotype.Component;

/** 개인정보 원문을 API 응답용 마스킹 문자열로 변환한다. */
@Component
public class PersonalDataMasker {

    /**
     * 이름의 첫 글자와 마지막 글자만 남기고 가운데 글자를 마스킹한다.
     *
     * @param name 마스킹할 사용자 이름
     * @return 마스킹된 이름, 입력값이 없으면 {@code null}
     */
    public String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        if (normalized.length() == 1) {
            return "*";
        }
        if (normalized.length() == 2) {
            return normalized.charAt(0) + "*";
        }
        return normalized.charAt(0)
                + "*".repeat(normalized.length() - 2)
                + normalized.charAt(normalized.length() - 1);
    }

    /**
     * 이메일 로컬 파트의 앞 최대 세 글자만 남기고 나머지를 마스킹한다.
     *
     * @param email 마스킹할 이메일 주소
     * @return 마스킹된 이메일, 입력값이 없으면 {@code null}
     */
    public String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "***";
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        int visibleLength = Math.min(3, localPart.length());
        return localPart.substring(0, visibleLength) + "***@" + domain;
    }

    /**
     * 전화번호의 앞 세 자리와 마지막 네 자리만 남기고 가운데를 마스킹한다.
     *
     * @param phoneNumber 마스킹할 전화번호
     * @return 마스킹된 전화번호, 입력값이 없으면 {@code null}
     */
    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return "***-****";
        }
        String prefix = digits.startsWith("02")
                ? digits.substring(0, 2)
                : digits.substring(0, 3);
        return prefix
                + "-****-"
                + digits.substring(digits.length() - 4);
    }
}
