# 사용자 API

현재 `user` 패키지가 제공하는 HTTP API는 로그인·회원 가입이 아니라 현재 사용자의
포인트 조회다. 요청자는 `X-User-Id` 헤더에 양의 정수 사용자 ID를 보낸다.

## 내 포인트

```http
GET /api/users/me/points
X-User-Id: 42
```

응답 `200 OK`:

```json
{"userId":42,"point":12500}
```

- 헤더가 없거나 양의 정수가 아니면 `400`과 `INVALID_REQUEST` 오류를 반환한다.
- 사용자가 없으면 `404`와 `USER_NOT_FOUND` 오류를 반환한다.
- 오류 형식은 `{"code":"...","message":"..."}`다.

## 내 포인트 내역

```http
GET /api/users/me/point-transactions
X-User-Id: 42
```

응답 `200 OK`:

```json
[
  {
    "transactionId":"watch-17",
    "type":"WATCH_REWARD",
    "pointDelta":100,
    "createdAt":"2026-08-31T12:00:00"
  },
  {
    "transactionId":"bet-43",
    "type":"BET_STAKE",
    "pointDelta":-3000,
    "createdAt":"2026-08-31T11:50:00"
  }
]
```

- 시청 보상과 배팅 포인트 원장을 합쳐 최신 순으로 반환한다.
- `type`은 `WATCH_REWARD`, `BET_STAKE`, `BET_PAYOUT`, `BET_REFUND` 중 하나다.
- `pointDelta`는 포인트 증감값이며, 배팅 참여는 음수이고 시청 보상·적중 지급·환불은 양수다.
