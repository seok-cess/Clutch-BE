# CLUTCH 기술 문서

이 디렉터리는 CLUTCH 구현에 영향을 주는 확정된 요구사항, 시스템 설계, 도메인 규칙,
기술 결정과 검증 기록을 관리한다.

상위 폴더의 번호는 중요도가 아니라 프로젝트를 처음 이해할 때의 탐색 순서를 의미한다.
하위 분류가 필요하면 `01-01`, `01-02`처럼 상위 번호를 이어서 사용한다.

## 처음 읽는 순서

1. [과제 원문](00-project/assignment.md)과 [확정 요구사항](00-project/requirements.md)에서 프로젝트의 출발점과 필수 조건을 확인한다.
2. [시스템 전체 구성](01-architecture/system-overview.md)에서 주요 구성 요소와 데이터 흐름을 확인한다.
3. 담당 기능의 `02-domain/` 문서에서 비즈니스 규칙을 확인한다.
4. [패키지 구조](04-conventions/package-structure.md)와 관련 개발 규칙을 확인한다.
5. 설계 이유가 필요하면 `03-decisions/`, 검증 근거가 필요하면 `06-verification/`을 확인한다.

## 문서 구조

| 위치 | 관리하는 내용 |
|---|---|
| [`00-project/`](00-project/) | 과제 원문과 구현 기준으로 정리한 요구사항 |
| [`01-architecture/`](01-architecture/) | 전체 시스템 구성과 핵심 데이터 흐름 |
| [`02-domain/`](02-domain/) | 기능별 용어, 상태, 불변식과 비즈니스 규칙 |
| [`03-decisions/`](03-decisions/) | 중요한 기술 선택의 배경, 검토한 대안과 결과 |
| [`04-conventions/`](04-conventions/) | 패키지, 계층, 데이터베이스, 파일과 Git 작업 규칙 |
| [`05-operations/`](05-operations/) | 시스템 실행, 모니터링과 장애 복구 절차 |
| [`06-verification/`](06-verification/) | 검증 방법, 실행 스크립트와 날짜별 결과 |
| [`07-troubleshooting/`](07-troubleshooting/) | 실제 문제의 증상, 원인, 해결과 재검증 기록 |
| `assets/` | 문서에서 사용하는 아키텍처와 모니터링 이미지 |

## 상황별 문서 찾기

| 알고 싶은 내용 | 확인할 문서 |
|---|---|
| 어떤 과제를 전달받았는가? | [과제 원문](00-project/assignment.md) |
| 실제 구현 기준으로 확정된 요구사항은 무엇인가? | [확정 요구사항](00-project/requirements.md) |
| CLUTCH 시스템은 어떻게 연결되는가? | [시스템 전체 구성](01-architecture/system-overview.md) |
| 새로운 코드를 어느 패키지와 계층에 작성하는가? | [패키지 구조](04-conventions/package-structure.md) |
| 쿠폰 발급 규칙은 무엇인가? | [쿠폰 발급](02-domain/coupon.md) |
| 시청 포인트 지급 규칙은 무엇인가? | [시청 세션과 포인트 지급](02-domain/watch-session.md) |
| 배팅과 정산 규칙은 무엇인가? | [승패 배팅](02-domain/betting.md) |
| 경기와 세트 결과를 어떻게 판정하는가? | [매치·세트 상태와 결과](02-domain/match-set-result.md) |
| 특정 기술 방식을 왜 선택했는가? | [기술 결정 기록](03-decisions/README.md) |
| Redis 쿠폰 재고 장애에 어떻게 대응하는가? | [Redis 쿠폰 재고 장애 복구](05-operations/coupon-redis-recovery.md) |
| 쿠폰 데이터 정합성을 어떻게 검증하는가? | [쿠폰 대량 데이터 정합성 검증](06-verification/README.md) |
| 개발 중 발생한 쿠폰 문제와 해결 과정은 무엇인가? | [`07-troubleshooting/`](07-troubleshooting/) |

## 현재 문서 목록

### 프로젝트 기준

- [과제 원문](00-project/assignment.md)
- [확정 요구사항](00-project/requirements.md)

### 시스템 설계

- [아키텍처 문서 안내](01-architecture/README.md)
- [시스템 전체 구성](01-architecture/system-overview.md)

### 도메인과 외부 계약

- [쿠폰 발급](02-domain/coupon.md)
- [시청 세션과 포인트 지급](02-domain/watch-session.md)
- [승패 배팅](02-domain/betting.md)
- [매치·세트 상태와 결과](02-domain/match-set-result.md)
- [쿠폰 실시간 잔여 재고 HTTP·SSE 계약](02-domain/api/coupon-stock.md)

### 기술 결정과 개발 규칙

- [ADR 작성 방법과 목록](03-decisions/README.md)
- [패키지 구조](04-conventions/package-structure.md)
- [데이터베이스 규칙](04-conventions/database-convention.md)
- [파일 인코딩과 줄바꿈 규칙](04-conventions/file-convention.md)
- [Git 규칙](04-conventions/git-convention.md)
- [Jira–GitHub 자동화](04-conventions/jira-github-workflow.md)

### 운영, 검증과 문제 해결

- [Redis 쿠폰 재고 장애 복구](05-operations/coupon-redis-recovery.md)
- [쿠폰 대량 데이터 정합성 검증](06-verification/README.md)
- [쿠폰 대량 발급 행 잠금 문제](07-troubleshooting/coupon-claim-lock-contention-troubleshooting.md)
- [새 쿠폰 회차 첫 발급 실패 문제](07-troubleshooting/coupon-first-claim-initialization-troubleshooting.md)

## 문서별 역할

- 요구사항은 시스템이 반드시 만족해야 하는 조건을 정의한다.
- 아키텍처는 시스템 구성 요소와 연결 관계를 설명한다.
- 도메인 문서는 기능이 지켜야 하는 비즈니스 규칙을 정의한다.
- ADR은 중요한 기술 결정을 내린 이유와 영향을 기록한다.
- 컨벤션은 개발 과정에서 반복해서 적용할 작성 규칙을 정의한다.
- 운영 문서는 시스템을 실행하고 장애에서 복구하는 절차를 설명한다.
- 검증 문서는 요구사항을 만족했음을 확인하는 방법과 결과를 기록한다.
- 트러블슈팅 문서는 실제 문제의 원인과 해결 경험을 보존한다.

같은 내용을 여러 문서에 반복하지 않고 기준 문서 하나에 작성한 뒤 다른 문서에서는 링크한다.
API 요청과 응답 형식은 가능한 한 Swagger/OpenAPI를 기준으로 하며, SSE처럼 별도 설명이 필요한
계약만 문서로 관리한다.

## 다른 협업 도구와의 관계

- Notion은 논의 중이거나 변경이 잦은 유저 플로우와 기능 명세를 관리한다.
- Jira는 담당자, 작업 단위, 일정과 진행 상태를 관리한다.
- GitHub는 소스 코드와 확정된 개발 규칙의 변경 이력을 관리한다.
- 저장소 루트의 `AGENTS.md`는 AI가 반드시 지킬 규칙과 상세 문서의 위치를 안내한다.

Notion의 논의가 구현을 제약하는 규칙이나 기술 결정으로 확정되면 관련 코드와 함께 `docs/`에
반영한다. 진행 상태는 문서에 중복해서 기록하지 않고 Jira를 기준으로 한다.

## 문서 갱신 원칙

- 코드와 문서가 같은 내용을 설명하도록 같은 Pull Request에서 함께 변경한다.
- 과거 결정과 검증 결과는 삭제하지 않고 당시 기록으로 보존한다.
- 중요한 기술 선택은 `03-decisions/`에 선택 이유, 대안과 영향을 남긴다.
- 새 폴더나 기준 문서가 추가되면 이 README와 `AGENTS.md`의 문서 경로를 함께 확인한다.
- ADR에는 결정 순서 번호를, 검증 결과에는 실행 날짜를 사용하며 일반 문서에는 번호를 붙이지 않는다.
