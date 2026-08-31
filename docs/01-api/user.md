# 사용자 API

현재 `user` 패키지가 제공하는 HTTP API는 로그인·회원 가입이 아니라 사용자의 포인트
정보와 순위 조회다. `me` 경로를 사용하는 API는 `X-User-Id` 헤더에 양의 정수 사용자
ID를 보내며, 전체 포인트 순위 조회에는 해당 헤더가 필요하지 않다.

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

## 내 포인트·승부예측 요약

```http
GET /api/users/me/point-summary
X-User-Id: 42
```

응답 `200 OK`:

```json
{
  "point": 12450,
  "predictionCount": 26,
  "predictionSuccessCount": 15,
  "maxEarnedPoint": 3600
}
```

- `predictionCount`는 사용자의 전체 승부예측 참여 횟수다.
- `predictionSuccessCount`는 `WON` 상태로 확정된 승부예측 횟수다.
- `maxEarnedPoint`는 시청 보상과 승부예측 적중 지급 중 한 번에 받은 가장 큰 포인트다.

## 내 포인트 순위

```http
GET /api/users/me/point-ranking
X-User-Id: 42
```

응답 `200 OK`:

```json
{
  "point": 12450,
  "rank": 24
}
```

- 순위는 `USER` 권한 사용자만 대상으로 계산한다.
- 현재 사용자보다 보유 포인트가 많은 사용자 수에 1을 더해 순위를 계산하므로,
  보유 포인트가 같은 사용자는 같은 순위를 받는다.

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

## 전체 포인트 순위

```http
GET /api/users/point-rankings
```

응답 `200 OK`:

```json
[
  {
    "rank": 1,
    "displayName": "김*정",
    "point": 48200
  },
  {
    "rank": 2,
    "displayName": "이*",
    "point": 41500
  }
]
```

- `USER` 권한 사용자 중 보유 포인트가 높은 상위 10명을 반환한다.
- 포인트가 같으면 사용자 ID 오름차순으로 표시 순서를 고정한다.
- 이름은 마스킹하며, 이름이 없으면 `익명 사용자`로 표시한다.
