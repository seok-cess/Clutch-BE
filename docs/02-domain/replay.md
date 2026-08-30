# 리플레이 도메인 규칙

## 범위

리플레이는 운영 경기 데이터의 대체 도메인이 아니라, 고정된 JSONL fixture를 재생해
라이브 화면·배팅·쿠폰 트리거를 검증하는 개발·시연 도구다. API 노출 조건과 요청 계약은
[`../01-api/replay.md`](../01-api/replay.md)를 따른다.

## 재생 경계

- 재생 제어 API는 `replay.enabled=true`일 때만 생성된다.
- 새 재생은 외부 소스가 `STUB`일 때만 시작할 수 있다. `REAL` 소스에서 재생을
  시작하는 요청은 거절한다.
- 새 재생을 시작하거나 배속을 바꾸면 이전 경기의 캐시·백오프·세트 상태를 비운 뒤
  즉시 메타·라이브 폴링을 수행한다. 이전 fixture의 시각과 새 fixture의 시각이 섞여
  배팅 타이머나 화면이 되감기는 것을 막기 위함이다.
- 재생 상태는 run ID, 외부 매치/세트 ID, 현재 경과 시간, 전체 길이, fixture 시각,
  배속을 제공한다. DB에 매치가 적재된 경우 내부 매치 ID도 함께 제공한다.

## 관련 코드

- `com.clutch.replay`
- `com.clutch.lolesports.source.ExternalSourceState`
- `com.clutch.lolesports.service.PollingScheduler`
