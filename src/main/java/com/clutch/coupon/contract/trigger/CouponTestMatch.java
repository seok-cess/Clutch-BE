package com.clutch.coupon.contract.trigger;

/**
 * 테스트·시연용으로 예약한 경기 식별자.
 *
 * <p>replay 스텁은 실행할 때마다 새 경기 ID 를 만든다({@code replay-<runId>-m1}).
 * 그래서 재생 경기에 쿠폰 이벤트를 미리 걸어둘 수 없다 — 다음 실행이면 ID 가 달라진다.</p>
 *
 * <p>대신 이 고정 ID 로 이벤트를 만들어 두고, 재생 중 감지된 트리거를 이 ID 로 돌린다.
 * 실제 경기 ID 는 모두 양수 auto-increment 이므로 음수를 쓰면 절대 겹치지 않는다.</p>
 *
 * <p>{@code coupon_event.esports_match_id} 에는 FK(fk_coupon_event_esports_match)가 걸려 있어
 * 존재하지 않는 경기로는 이벤트를 만들 수 없다. 그래서 V15 마이그레이션이 이 ID 로
 * 실제 {@code esports_match} 행을 하나 만들어 둔다 — 그 행을 지우면 테스트 이벤트를
 * 저장할 수 없게 된다.</p>
 */
public final class CouponTestMatch {

    /** 테스트 이벤트가 매달리는 예약 경기 ID */
    public static final long SAMPLE_MATCH_ID = -1L;

    private CouponTestMatch() {
    }

    /** 이 경기 ID 가 테스트 전용인가 */
    public static boolean isSample(Long esportsMatchId) {
        return esportsMatchId != null && esportsMatchId == SAMPLE_MATCH_ID;
    }
}
