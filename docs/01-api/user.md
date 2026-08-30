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
