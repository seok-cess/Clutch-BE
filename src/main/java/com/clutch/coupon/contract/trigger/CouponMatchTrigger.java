package com.clutch.coupon.contract.trigger;

/**
 * 경기 중 사건 중 쿠폰 이벤트를 열 수 있는 종류.
 *
 * 감지하는 쪽(lolesports 폴링)과 여는 쪽(coupon) 사이의 계약이다.
 * 감지 모듈이 쿠폰 내부 enum 을 직접 참조하지 않도록 여기에 둔다 —
 * 참조가 반대로 걸리면 쿠폰 도메인을 고칠 때마다 폴링이 깨진다.
 */
public enum CouponMatchTrigger {

    /** 그 세트에서 처음으로 킬이 나왔을 때 */
    FIRST_BLOOD,

    /** 한 선수가 짧은 시간 안에 5킬을 연속으로 처리했을 때 */
    PENTAKILL,

    /** 그 경기에서 처음으로 바론이 처치됐을 때 */
    FIRST_BARON_KILL,

    /** 팀이 드래곤을 처치했을 때 */
    DRAGON_KILL,

    /** 세트가 종료됐을 때 */
    GAME_END
}
