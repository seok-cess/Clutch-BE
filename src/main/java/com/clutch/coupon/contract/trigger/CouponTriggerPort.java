package com.clutch.coupon.contract.trigger;

/**
 * 경기 감지 모듈이 쿠폰 이벤트를 여는 통로.
 *
 * 감지 쪽은 "무슨 사건이 어느 세트에서 몇 초에 일어났다"만 알린다.
 * 그 트리거를 기다리는 이벤트가 있는지, 재고가 남았는지, 이미 열렸는지는
 * 전부 쿠폰 도메인이 판단한다 — 감지 쪽이 알 필요도 없고 알 수도 없다.
 *
 * 구현은 중복 호출에 안전해야 한다. 폴링은 같은 프레임을 다시 볼 수 있고
 * 재기동 후 같은 사건을 또 감지할 수 있다.
 */
public interface CouponTriggerPort {

    /**
     * 경기 사건을 알려 해당 트리거의 쿠폰 이벤트를 연다.
     *
     * @param trigger 감지한 사건 종류
     * @param externalMatchId 사건이 일어난 경기의 외부 ID. 이 경기에 걸린 이벤트만 연다
     * @param externalGameId 사건이 일어난 세트의 외부 ID. 중복 방지 키에 들어간다
     * @param gameTimeSeconds 게임 시작 후 경과 초. 중복 방지 키에 들어간다
     */
    void fire(
            CouponMatchTrigger trigger,
            String externalMatchId,
            String externalGameId,
            Integer gameTimeSeconds
    );
}
