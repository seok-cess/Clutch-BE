# 사용자 도메인 규칙

## 사용자 모델

- 사용자는 `USER` 또는 `ADMIN` 역할을 가진다.
- 이메일은 필수이며 고유하다. 이름과 전화번호는 기존 데이터 호환을 위해 비어 있을 수
  있다.
- 이름은 앞뒤 공백을 제거한 뒤 최대 50자로 저장한다. 전화번호는 숫자만 남겨 정규화하고
  10~15자여야 한다.
- 새 사용자의 초기 포인트는 0이다. 포인트의 증감·원장 규칙은 [`wallet.md`](wallet.md)를
  따른다.

## 현재 요청 식별 방식

- 사용자 조회와 일반 사용자 기능은 `X-User-Id` 헤더를 양의 정수 사용자 ID로 해석한다.
- 관리자 쿠폰 취소와 관리자 발급 이력 조회는 동일 헤더의 사용자가 실제로 `ADMIN`
  역할인지 확인한다.
- 로그인·토큰 발급은 현재 `user` 패키지와 이 저장소의 HTTP API 범위에 없다.

## 개인정보

- 관리자 발급 이력은 사용자 이름·이메일·전화번호를 마스킹한 값으로 반환한다.
- `User.toString()`은 사용자 ID와 역할만 출력한다. 개인정보가 로그에 남지 않게 하기
  위한 규칙이다.

## 관련 코드

- `com.clutch.user`
- `com.clutch.wallet.web.CurrentUserIdArgumentResolver`
- `com.clutch.wallet.web.CurrentAdminIdArgumentResolver`
