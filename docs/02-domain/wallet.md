# 지갑 도메인 규칙

## 포인트

- 사용자 포인트는 정수다. `changePoint`는 덧셈 범위를 넘으면 실패하며, 묵시적
  오버플로를 허용하지 않는다.
- 시청 포인트는 5분 유효 시청 시간마다 100포인트를 지급하며, 지급과
  `watch_point_transaction` 기록은 함께 성공하거나 rollback된다.
- 배팅 등록은 포인트를 즉시 차감하고 `STAKE` 원장을 남긴다. 정산과 환불은 각각
  `PAYOUT`, `REFUND` 원장을 남긴다. 세부 금액 규칙은 [`betting.md`](betting.md)를
  따른다.
- 쿠폰 발급·사용·취소는 현재 사용자 포인트를 변경하지 않는다.

## 보유 쿠폰

- 사용자 쿠폰은 한 발급 요청(`claim_id`)에 하나만 연결되고, 쿠폰 코드는 전역에서
  고유하다.
- 저장 상태는 `ISSUED`, `USED`, `EXPIRED`, `CANCELLED`다.
- 저장 상태가 `ISSUED`이고 기준 시각이 `expires_at` 이상이면, DB 행을 즉시 바꾸지
  않아도 조회·필터에서는 `EXPIRED`로 해석한다.
- 사용과 관리자 취소는 `ISSUED`이면서 만료 전일 때만 가능하다. 이미 사용·취소·만료된
  쿠폰은 다시 처리하지 않는다.
- 사용자 쿠폰의 발급 근거, 만료와 관리자 취소 규칙은 [`coupon.md`](coupon.md)를
  따른다.

## 관련 코드

- `com.clutch.wallet`
- `com.clutch.watch`
- `com.clutch.betting`
