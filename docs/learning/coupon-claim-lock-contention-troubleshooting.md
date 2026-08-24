# 쿠폰 대량 발급 행 잠금 트러블슈팅

## 증상

10,000 VU, 재고 5,000장 부하에서 실제 쿠폰 5,000건 저장은 완료됐지만 615건은 정상
결과를 받지 못했다. 관측값은 Hikari 20개 중 19개 사용·대기 178개, MySQL 행 잠금 최대
2,914회/분, 잠금 대기 시간 최대 1,115초/분이었다.

## 원인

Redis Lua는 재고와 중복을 빠르게 확정했지만, 성공한 모든 요청이 MySQL transaction 안에서
같은 `coupon_event_item.success_count` 행을 증가시켰다.

```text
5,000건 Redis 성공
→ 5,000개 DB transaction
→ 동일 success_count 행 X-lock 경쟁
→ Hikari 커넥션 대기
→ Tomcat 요청 적체와 HTTP timeout
```

낙관적 락은 충돌 뒤 재시도를 늘리고, 비관적 락은 대기열을 명시적으로 만들 뿐이므로 이
핫 행의 해결책이 아니다.

## 해결

재고 허용 권한은 Redis Lua에만 두고, 동기 DB 발급 transaction에서 `success_count` 갱신을
제거했다. 실제 쿠폰 생성, 발급 요청 성공 전이, 결과 Outbox는 계속 한 transaction으로
처리하므로 사용자는 이전처럼 즉시 쿠폰 ID를 받는다.

`success_count`는 5초 주기의 단일 집계 작업이 실제 `user_coupon` 수로 보정한다. 따라서
관리자 집계는 잠시 지연될 수 있지만, 재고 차감과 장애 복구는 실제 쿠폰 수를 기준으로
동작한다.

## 재검증 방법

1. `VERIFY_INDIVIDUAL_PERSISTENCE=false`로 발급 API 부하와 개별 조회 부하를 분리한다.
2. 수정 전과 동일한 재고 비율로 1,000, 3,000, 5,000, 10,000 VU를 실행한다.
3. `coupon_claim_success_total == 재고`, `sold_out == VU - 재고`, 비정상 실패 0건을 확인한다.
4. Claim P95, Hikari active/pending, MySQL `data_lock_waits`, `Innodb_row_lock_*`를 함께 비교한다.
5. 테스트 종료 뒤 관리자 이벤트 조회와 MySQL `user_coupon` 수가 재고와 같은지 확인한다.

## 남은 한계

이 변경은 공통 행 잠금을 제거하지만, 5,000개의 실제 `user_coupon` insert를 동기로 처리하는
한계까지 없애지는 않는다. 그 단계가 다음 병목으로 확인될 때만 Redis Stream 기반 비동기
발급을 별도 설계한다.
