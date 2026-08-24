package com.clutch.user.domain;

/**
 * 개인정보 마스킹.
 *
 * 이름·전화번호·이메일은 로그와 API 응답 어디에도 원본으로 나가지 않는다.
 * 관리자 화면도 예외가 아니다 — 발급 내역에서 "누구인지" 식별하는 데는
 * 마스킹된 값으로 충분하고, 원본이 필요한 업무가 아직 없다.
 *
 * 마스킹 규칙은 "몇 글자인지"는 남기되 "누구인지"는 알 수 없게 하는 선을 지킨다.
 * 전부 별표로 덮으면 같은 화면의 두 사람을 구분할 수 없어 운영이 불가능하다.
 */
public final class PersonalDataMasker {

    private PersonalDataMasker() {
    }

    /**
     * 이름 — 가운데를 가린다.
     *
     * <pre>
     * 김민준 → 김*준     세 글자 이상은 첫 글자와 끝 글자만 남긴다
     * 김민   → 김*       두 글자는 끝 글자를 가린다 (첫 글자만 남기면 성만 남는다)
     * 김     → *         한 글자는 통째로 가린다
     * </pre>
     */
    public static String name(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        int length = trimmed.length();
        if (length == 1) {
            return "*";
        }
        if (length == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0)
                + "*".repeat(length - 2)
                + trimmed.charAt(length - 1);
    }

    /**
     * 전화번호 — 가운데 국번을 가린다.
     *
     * <pre>
     * 01012345678   → 010-****-5678
     * 010-1234-5678 → 010-****-5678   하이픈이 있어도 같은 결과
     * 0212345678    → 02-****-5678    서울 지역번호(2자리)
     * </pre>
     *
     * 뒤 4자리를 남기는 것은 본인 확인 문의에서 관례적으로 쓰는 식별 단위다.
     */
    public static String phone(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            return "*".repeat(Math.max(1, digits.length()));
        }
        // 02 로 시작하는 서울 번호만 지역번호가 2자리다
        int prefixLength = digits.startsWith("02") ? 2 : 3;
        String prefix = digits.substring(0, prefixLength);
        String suffix = digits.substring(digits.length() - 4);
        return prefix + "-****-" + suffix;
    }

    /**
     * 이메일 — 아이디 뒷부분을 가리고 도메인은 남긴다.
     *
     * <pre>
     * hongildong@example.com → ho********@example.com
     * ab@example.com         → a*@example.com
     * a@example.com          → *@example.com
     * </pre>
     *
     * 도메인을 남기는 것은 가입 경로(회사 계정인지 등)를 확인해야 하는 문의가 있어서다.
     */
    public static String email(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int at = value.indexOf('@');
        if (at < 0) {
            // 이메일 형식이 아니면 통째로 가린다 — 무엇이 담겼는지 알 수 없다
            return "*".repeat(value.length());
        }
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        int visible = local.length() <= 3 ? 1 : 2;
        return local.substring(0, visible)
                + "*".repeat(local.length() - visible)
                + domain;
    }
}
