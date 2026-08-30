# 쿠폰 API

쿠폰 이벤트·회차·발급·재고의 도메인 규칙은 [`../02-domain/coupon.md`](../02-domain/coupon.md)를
따른다.

`/api/v1/admin` 경로 중 관리자 발급 이력은 `ADMIN` 역할을 검증하지만, 쿠폰 종류·이벤트
관리와 테스트 컨트롤러에는 현재 Controller 수준의 관리자 인가가 없다. 경로 이름과 실제
인가 수준을 혼동하지 않는다.

## 쿠폰 종류 관리

기본 경로는 `/api/v1/admin/coupon-types`다.

| 메서드 | 경로 | 설명 | 성공 상태 |
|---|---|---|---|
| `POST` | `/` | 쿠폰 종류 생성 | `201` |
| `GET` | `/` | 상태별 커서 목록 | `200` |
| `GET` | `/options` | 이벤트 생성 화면용 활성 종류 목록 | `200` |
| `GET` | `/{couponTypeId}` | 쿠폰 종류 상세 | `200` |
| `PATCH` | `/{couponTypeId}` | 미사용 종류의 혜택 정의 수정 | `200` |
| `PATCH` | `/{couponTypeId}/status` | 신규 이벤트 선택 가능 상태 변경 | `200` |
| `DELETE` | `/{couponTypeId}` | 미사용 종류 삭제 | `204` |

생성·수정 요청:

```json
{
  "couponName":"LCK 10% 할인",
  "discountType":"RATE",
  "discountValue":10.00
}
```

- `discountType`은 `RATE` 또는 `AMOUNT`다.
- 이름은 1~100자, 할인 값은 0 초과·정수 8자리/소수 2자리 이하다.
- `RATE`는 도메인 검증상 100 이하만 허용한다.
- 상태 변경 본문은 `{"status":"ACTIVE"}` 또는 `{"status":"INACTIVE"}`다.
- 목록 쿼리는 `status`, `cursor`, `size`를 사용하고 응답은
  `couponTypes` 또는 `options`, `nextCursor`, `hasNext`를 포함한다.
- 상세·생성 응답은 ID, 이름, 할인 방식·값, 상태, 이벤트 사용 여부(`used`), 생성·수정
  시각을 반환한다.

존재하지 않는 종류는 `404 COUPON_TYPE_NOT_FOUND`, 사용 이력이 있는 정의 수정·삭제는
각각 `409 COUPON_TYPE_NOT_EDITABLE`, `409 COUPON_TYPE_NOT_DELETABLE`이다.

## 쿠폰 이벤트 관리

기본 경로는 `/api/v1/admin/coupon-events`다.

| 메서드 | 경로 | 설명 | 성공 상태 |
|---|---|---|---|
| `POST` | `/` | 이벤트와 발급 항목 생성 | `201` |
| `GET` | `/` | 이벤트 목록 | `200` |
| `GET` | `/{couponEventId}` | 이벤트 설정·재고·최근 회차 상세 | `200` |
| `PATCH` | `/{couponEventId}` | `READY` 이벤트 설정과 항목 교체 | `200` |
| `DELETE` | `/{couponEventId}` | 발생·발급 이력이 없는 `READY` 이벤트 삭제 | `204` |

생성·수정 본문은 같은 구조다.

```json
{
  "esportsMatchId":100,
  "eventName":"첫 킬 쿠폰",
  "issueMode":"SINGLE_FIRST_COME",
  "triggerType":"FIRST_BLOOD",
  "claimWindowSeconds":60,
  "items":[
    {"couponTypeId":1,"quantity":100,"openOffsetSeconds":0}
  ]
}
```

| 필드 | 규칙 |
|---|---|
| `issueMode` | `SINGLE_FIRST_COME` 또는 `PHASED_FIRST_COME` |
| `eventName` | 1~200자 |
| `claimWindowSeconds` | 1 이상 |
| `items` | 하나 이상 |
| 항목의 `couponTypeId`·`quantity` | 양수 |
| `openOffsetSeconds` | 0 이상 |

목록은 `status`, `cursor`, `size`를 받고 `couponEvents`, `nextCursor`, `hasNext`를
반환한다. 상세에는 전체·발급·잔여 수량, 항목별 `successCount`·단계 정보와 가장 최근
회차(`latestOccurrence`, 없으면 `null`)가 포함된다. 이벤트 상태는 `READY`, `OPEN`,
`CLOSED`, `CANCELLED`이고 회차 상태는 `OPEN`, `CLOSED`, `CANCELLED`다.

중복 이벤트는 `409 COUPON_EVENT_DUPLICATED`, 수정 불가 상태는
`409 COUPON_EVENT_NOT_EDITABLE`, 삭제 불가 상태는 `409 COUPON_EVENT_NOT_DELETABLE`이다.

## 사용자 발급과 활성 회차

```http
GET /api/v1/coupon-events/active
```

현재 열려 있는 최신 테스트/수동 회차가 없으면 `204 No Content`다. 있으면 이벤트·회차
ID, 이름, 시작·만료 시각, 회차 상태, `remainingQuantity`, `claimable`을 반환한다.

```http
POST /api/v1/coupon-events/{couponEventId}/occurrences/{couponEventOccurrenceId}/claims
X-User-Id: 42
```

성공 시 `201 Created`와 아래 필드를 반환한다.

```json
{
  "claimId":500,
  "couponId":1000,
  "couponEventId":12,
  "couponEventOccurrenceId":35,
  "couponEventItemId":89,
  "requestStatus":"SUCCEEDED",
  "createdAt":"2026-08-28T10:00:00"
}
```

대표 오류는 `404`의 `COUPON_EVENT_NOT_FOUND`, `COUPON_EVENT_OCCURRENCE_NOT_FOUND`,
`409`의 `COUPON_EVENT_NOT_OPEN`, `COUPON_ALREADY_CLAIMED`, `COUPON_STOCK_EXHAUSTED`,
`503`의 Redis·재고 복구 오류다. 모두 `{"code":"...","message":"..."}` 형식이다.

## 관리자 발급 이력

```http
GET /api/v1/admin/coupon-claims?eventKeyword=12&requestStatus=SUCCEEDED&size=20
X-User-Id: 1
```

이 API만 현재 `ADMIN` 역할을 검사한다. 지원 쿼리는 `eventKeyword`, `triggerKeyword`,
`userId`, `requestStatus`, `couponStatus`, `couponTypeId`, `from`, `to`, `cursor`, `size`다.

- 숫자로만 된 `eventKeyword`는 이벤트 ID 정확 일치, 그 외는 이벤트 이름 부분 일치다.
- `from`, `to`는 ISO date-time 형식의 `LocalDateTime`이다.
- 응답은 `claims`, `nextCursor`, `hasNext`다. 각 행은 요청·완료 시각, 이벤트·회차,
  마스킹된 사용자 정보, 쿠폰 혜택, 요청·쿠폰 상태와 실패 사유를 포함한다.

## 재고 조회·SSE·복구

잔여 재고 조회와 스트림 갱신은 MySQL을 조회하지 않고 Redis의
`coupon:event-item:{couponEventItemId}:stock` 값을 기준으로 한다.

### 현재 재고

```http
GET /api/v1/coupon-event-items/{couponEventItemId}/stock
```

성공 응답 `200 OK`:

```json
{
  "couponEventItemId":20,
  "remainingStock":7,
  "exhausted":false
}
```

재고 `0`은 장애가 아니라 `remainingStock: 0`, `exhausted: true`인 정상 응답이다.
Redis 키가 아직 준비되지 않았으면 `503 COUPON_STOCK_NOT_INITIALIZED`, 연결 실패 또는
유효하지 않은 재고 값이면 `503 COUPON_STOCK_READ_FAILED`다.

### 실시간 재고 스트림

```http
GET /api/v1/coupon-event-items/{couponEventItemId}/stock/stream
Accept: text/event-stream
Last-Event-ID: 15
```

- 이벤트 이름은 `coupon-stock`, `retry`는 1초다.
- 연결 직후 최신 Redis 재고 스냅샷을 보내고, 쿠폰 발급 transaction이 commit된 뒤 변경
  스냅샷을 보낸다.
- `Last-Event-ID` 유무와 관계없이 재연결 시 최신 스냅샷부터 전송한다. 개별 이벤트
  재생이 아니라 최종 상태 복구를 위한 동작이다.
- 재고가 0인 이벤트를 보낸 뒤 연결을 정상 종료한다.
- 구독 중 Redis 조회가 실패하면 `coupon-stock-error` 이벤트에
  `COUPON_STOCK_READ_FAILED` 또는 `COUPON_STOCK_NOT_INITIALIZED` 오류를 담은 뒤
  연결을 종료한다.
- 구독자 목록은 애플리케이션 인스턴스 메모리에 있으므로 현재 스트림은 단일 인스턴스
  기준이다.

```text
id:16
event:coupon-stock
retry:1000
data:{"couponEventItemId":20,"remainingStock":6,"exhausted":false}
```

### 관리자 재고 복구

```http
GET  /api/v1/admin/coupon-stock-recovery
POST /api/v1/admin/coupon-stock-recovery
```

상태 조회와 수동 복구 모두 다음 형식을 반환한다.

```json
{
  "state":"READY",
  "recoveredOccurrences":1,
  "recoveredItems":2,
  "recoveredUsers":3427
}
```

상태 조회에서는 복구 건수가 모두 0이다. 상태는 `READY`, `UNAVAILABLE`, `RECOVERING`,
`FAILED` 중 하나다. 성공 요청 수와 실제 쿠폰 수가 일치하지 않으면
`503 COUPON_STOCK_INCONSISTENT`와 `FAILED` 상태를 유지한다. 발급 중 Redis 연결 장애는
`503 COUPON_REDIS_UNAVAILABLE`, 복구 중인 경우는 `503 COUPON_STOCK_RECOVERING`이다.

## 테스트·시연 전용 API

아래 API는 `coupon.test.event` 패키지에 있으며, 일반 사용자 기능과 분리해 사용한다.

| 메서드 | 경로 | 성공 상태 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/admin/coupon-events/{id}/occurrences/manual-open` | `201` | 경기 트리거 없이 회차 수동 오픈 |
| `POST` | `/api/v1/admin/coupon-events/{id}/test-reset` | `200` | 해당 이벤트의 시연 회차·발급 이력을 지우고 `READY`로 복귀 |
| `GET` | `/api/v1/admin/coupon-events/triggers` | `200` | 트리거 선택값과 표시명 |
| `POST` | `/api/v1/admin/coupon-events/occurrences/trigger` | `201` 또는 `204` | 트리거 시뮬레이션 |
| `POST` | `/api/v1/test/sample-frames` | `204` | 시연 프레임을 감지기에 입력 |
| `DELETE` | `/api/v1/test/sample-frames?gameId={gameId}` | `204` | 프레임 감지 상태 초기화 |

트리거 시뮬레이션은 `trigger`, `esportsMatchId`가 필수이고 `gameId`,
`gameTimeSeconds`는 선택이다. 조건에 맞는 이벤트가 없거나 이미 열려 있으면 본문 없이
`204`다.

프레임 입력 본문은 `gameId`, 0 이상 `gameTimeSeconds`, 블루·레드 참가자 배열을 받는다.
참가자는 `participantId`와 0 이상 누적 `kills`를 갖는다. 클라이언트가 트리거를 지정하지
않고 감지기가 프레임에서 판정한다.
