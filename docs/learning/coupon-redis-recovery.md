# Redis 쿠폰 재고 장애 복구 학습 정리

## 한 문장 설명

Redis가 꺼지면 쿠폰 발급을 잠시 멈추고, Redis가 다시 연결되면 MySQL의 실제 발급
쿠폰을 기준으로 재고와 발급 사용자 목록을 다시 만든 뒤 발급을 재개한다.

## 정상 발급

```text
사용자 요청
→ Redis Lua 재고 차감과 중복 확인
→ MySQL user_coupon 생성
→ MySQL commit
→ 사용자 성공 응답
```

Redis는 빠른 동시성 제어를 담당하고, MySQL의 `user_coupon`은 실제 발급 결과를
보관한다.

## Redis가 꺼졌을 때

```text
Redis 연결 실패
→ 상태 UNAVAILABLE
→ 새 발급 요청 HTTP 503
→ MySQL 쿠폰 생성 안 함
```

재고 상태를 모르는 상황에서 발급을 계속하면 초과 발급될 수 있어 Fail Closed를
선택했다. 장애 중 요청 순서는 보존하지 않으며 사용자는 복구 후 다시 요청한다.

## Redis가 다시 연결됐을 때

```text
스케줄러가 Redis 연결 회복 확인
→ 상태 RECOVERING
→ 열린 쿠폰 회차 조회
→ MySQL 데이터 일치 검증
→ Redis 재고와 발급 사용자 Set 재구축
→ Redis 값 재검증
→ 상태 READY
```

재고 계산식은 다음과 같다.

```text
Redis 잔여 재고 = coupon_event_item.quantity - 실제 user_coupon 수
```

쿠폰을 사용하거나 취소했더라도 이벤트 재고로 돌려주지 않으므로 `user_coupon`의
현재 상태와 관계없이 실제 생성된 전체 행을 센다.

## 왜 숫자 세 개를 비교하는가

정상이라면 다음 값이 같다.

```text
coupon_event_item.success_count
= SUCCEEDED coupon_claim_request 수
= 실제 user_coupon 수
```

값이 다르면 어느 단계에서 데이터가 어긋났는지 알 수 없기 때문에 복구를 중단한다.
임의의 값을 선택해 Redis를 열면 초과 발급 위험이 생긴다.

## 왜 Redis 락을 사용하지 않았는가

Redis 자체가 장애 대상이므로 장애 감지와 복구 시작을 조정하는 유일한 락으로 Redis를
사용할 수 없다. 현재 프로젝트는 단일 애플리케이션 인스턴스를 기준으로 메모리 상태를
사용한다. 여러 서버로 확장할 때는 MySQL 잠금 또는 복구 상태 테이블이 필요하다.

## 발표 예상 질문

### Redis가 꺼지면 쿠폰은 계속 발급되나요?

아니다. 정확한 재고를 보장할 수 없으므로 503으로 중단한다. MySQL에는 성공 쿠폰을
만들지 않으며 Redis 복구 후 다시 참여할 수 있다.

### 장애 중 요청한 사용자의 순서는 보장하나요?

보장하지 않는다. 순서를 보존하려면 Kafka 등에 요청을 영구 저장해야 하지만 즉시 발급
결과를 제공하는 현재 구조와 충돌한다.

### Redis가 살아나면 무엇을 기준으로 복구하나요?

MySQL의 실제 `user_coupon`을 기준으로 전체 수량에서 실제 발급 수를 빼서 재고를
계산한다. 성공 집계와 발급 요청 수도 함께 비교한다.

### Redis AOF가 있는데 왜 재구축이 필요한가요?

AOF는 일반 재시작에서 데이터 유실을 줄여주지만 파일 손상, volume 유실, 서버 교체를
완전히 해결하지 못한다. MySQL 기준 재구축은 Redis 데이터가 전부 없어도 사용할 수
있는 최종 복구 수단이다.

### 왜 Redis 장애 중 MySQL로 발급하지 않나요?

대규모 요청이 MySQL에 몰리고 Redis 발급과 MySQL 발급 두 경로 사이의 동시성 문제를
새로 만들어야 한다. 현재는 초과 발급 방지와 일관된 단일 발급 경로를 우선한다.

## 로컬 장애 실습

1. 정상 발급 후 MySQL 실제 쿠폰 수와 Redis 재고 확인
2. `docker compose stop redis`로 Redis 중단
3. 발급이 503으로 차단되는지 확인
4. `docker compose start redis`로 Redis 재시작
5. 복구 상태가 `READY`로 돌아오는지 확인
6. Redis 재고가 `전체 수량 - 실제 쿠폰 수`인지 확인
7. 복구 후 남은 수량만큼만 추가 발급되는지 확인

장애 실습 후에는 반드시 Redis가 `healthy`인지 확인한다.
