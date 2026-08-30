# 지갑 API

사용자 기능은 `X-User-Id` 헤더에 양의 정수 사용자 ID를 보낸다. 관리자 쿠폰 취소는
해당 사용자가 실제 `ADMIN` 역할이어야 한다. 사용자 모델과 식별 방식은
[`../02-domain/user.md`](../02-domain/user.md)를 따른다.

## 내 쿠폰 목록

```http
GET /api/users/me/coupons?status=ISSUED&size=20&cursor=1760000000000_100
X-User-Id: 42
```

| 쿼리 | 설명 |
|---|---|
| `status` | 선택값. `ISSUED`, `USED`, `EXPIRED`, `CANCELLED` 중 하나다. |
| `cursor` | 선택값. 이전 응답의 `nextCursor`다. |
| `size` | 선택값, 기본 20. 1~100만 허용한다. |

응답 `200 OK`:

```json
{
  "items":[{
    "id":100,
    "couponEventId":12,
    "couponCode":"CPN-...",
    "status":"ISSUED",
    "discountType":"RATE",
    "discountValue":10.00,
    "expiresAt":"2026-09-01T00:00:00Z",
    "usedAt":null,
    "cancelledAt":null
  }],
  "nextCursor":"1760000000000_100",
  "hasNext":true
}
```

저장 상태가 `ISSUED`여도 조회 기준 시각에 만료됐다면 응답 `status`는 `EXPIRED`다.

## 내 쿠폰 단건 조회와 사용

```http
GET  /api/users/me/coupons/{couponId}
POST /api/users/me/coupons/{couponId}/use
X-User-Id: 42
```

둘 다 `CouponResponse` 형식으로 응답한다. 사용 요청은 본문이 없으며 성공하면 `200 OK`와
`status: "USED"`를 반환한다.

| 상태 | 결과 |
|---|---|
| 존재하지 않거나 다른 사용자의 쿠폰 | `404` |
| 이미 사용·취소·만료 | `409` |
| 잘못된 `status`, `cursor`, `size` | `400` |

## 관리자 쿠폰 취소

```http
POST /api/admin/coupons/{couponId}/cancel
X-User-Id: 1
Content-Type: application/json

{"reason":"관리자 취소 사유"}
```

- 요청자는 실제 `ADMIN` 역할이어야 하며, 아니면 `403`이다.
- `reason`은 빈 값일 수 없다.
- 성공 시 `200 OK`와 취소된 `CouponResponse`를 반환한다.
- 이미 사용·취소·만료됐거나 취소할 수 없는 상태면 `409`, 쿠폰이 없으면 `404`다.
