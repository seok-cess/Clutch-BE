package com.clutch.coupon.test.event.domain;

/**
 * 쿠폰 이벤트를 여는 경기 트리거.
 *
 * 관리자가 이벤트를 만들 때 고르는 값이며, 회차를 열 때 어떤 사건 때문에
 * 열렸는지를 함께 남긴다. 발급 내역에서 "무엇이 이 이벤트를 발동시켰나"를
 * 되짚을 수 있어야 운영에서 원인을 찾을 수 있다.
 */
public enum CouponEventTrigger {

    /** 관리자가 직접 연다. 트리거 조건 없이 즉시 오픈된다 */
    MANUAL("수동 오픈"),

    /** 수동 테스트용. 백엔드가 날짜와 당일 순번을 붙여 저장한다 (CLUTCH-216) */
    MANUAL_TEST("수동 테스트"),

    /** 그 세트에서 처음으로 킬이 나왔을 때 */
    FIRST_BLOOD("첫 킬"),

    /** 한 선수가 5킬을 연속으로 처리했을 때 */
    PENTAKILL("펜타킬"),

    /** 그 경기에서 처음으로 바론이 처치됐을 때 */
    FIRST_BARON_KILL("첫 바론 처치"),

    /** 팀이 드래곤을 처치했을 때 */
    DRAGON_KILL("드래곤 처치"),

    /** 세트가 종료됐을 때 */
    GAME_END("세트 종료");

    private final String displayName;

    CouponEventTrigger(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 문자열을 트리거로 해석한다. 기존 데이터에 자유 문자열이 들어 있어
     * 알 수 없는 값은 MANUAL 로 본다 — 트리거를 못 읽었다고 이벤트 조회가
     * 실패하면 운영이 막힌다.
     */
    public static CouponEventTrigger from(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL;
        }
        for (CouponEventTrigger trigger : values()) {
            if (trigger.name().equalsIgnoreCase(value.trim())) {
                return trigger;
            }
        }
        return MANUAL;
    }
}
