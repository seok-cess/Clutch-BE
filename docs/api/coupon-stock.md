# 쿠폰 실시간 잔여 재고 API

## 범위

사용자가 현재 쿠폰 이벤트 항목의 잔여 재고를 조회하고, 발급에 따른 변경을 SSE로
받는 API 계약을 설명한다. 잔여 재고 조회와 스트림 갱신은 MySQL을 조회하지 않고
Redis의 `coupon:event-item:{couponEventItemId}:stock` 값을 기준으로 한다.

## 현재 재고 조회

```http
GET /api/v1/coupon-event-items/{couponEventItemId}/stock
```

정상 재고 응답:

```json
{
  "couponEventItemId": 20,
  "remainingStock": 7,
  "exhausted": false
}
```

재고 소진은 장애가 아니므로 HTTP 200과 재고 `0`을 반환한다.

```json
{
  "couponEventItemId": 20,
  "remainingStock": 0,
  "exhausted": true
}
```

## SSE 실시간 스트림

```http
GET /api/v1/coupon-event-items/{couponEventItemId}/stock/stream
Accept: text/event-stream
Last-Event-ID: 15
```

- 이벤트 이름은 `coupon-stock`이다.
- 연결 직후 Redis의 최신 재고 스냅샷을 전송한다.
- 쿠폰 발급 DB transaction이 commit된 뒤 변경 스냅샷을 전송한다.
- `retry` 값은 1초다.
- 재연결 시 `Last-Event-ID` 유무와 관계없이 최신 Redis 스냅샷부터 다시 보내므로,
  연결이 끊긴 동안의 개별 이벤트가 아니라 최종 상태를 복구한다.
- 재고가 0인 이벤트를 전송한 뒤 서버가 해당 SSE 연결을 정상 종료한다.
- 연결 이후 Redis 조회가 실패하면 `coupon-stock-error` 이벤트에
  `COUPON_STOCK_READ_FAILED` 또는 `COUPON_STOCK_NOT_INITIALIZED` 오류를 담아 전송한 뒤
  연결을 종료한다. 클라이언트는 1초 후 재연결하여 최신 스냅샷을 다시 요청할 수 있다.
- 현재 SSE 구독자 목록은 애플리케이션 인스턴스 메모리에 있으므로 단일 인스턴스를
  기준으로 한다. 다중 인스턴스로 확장할 때는 Redis Pub/Sub 등 인스턴스 간 알림
  전달 계층이 필요하다.

SSE 예시:

```text
id:16
event:coupon-stock
retry:1000
data:{"couponEventItemId":20,"remainingStock":6,"exhausted":false}
```

## 오류 구분

Redis 키가 아직 초기화되지 않은 경우:

```http
HTTP/1.1 503 Service Unavailable
```

```json
{
  "code": "COUPON_STOCK_NOT_INITIALIZED",
  "message": "쿠폰 재고가 준비되지 않았습니다."
}
```

Redis 연결 실패 또는 유효하지 않은 재고 값인 경우:

```json
{
  "code": "COUPON_STOCK_READ_FAILED",
  "message": "쿠폰 재고를 조회할 수 없습니다."
}
```

두 경우 모두 HTTP 503이다. 정상적인 재고 소진은
`remainingStock: 0`, `exhausted: true`인 HTTP 200 응답이므로 Redis 장애와 구분된다.
