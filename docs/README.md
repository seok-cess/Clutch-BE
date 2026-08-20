# CLUTCH 기술 문서

이 디렉터리는 실제 코드 구현에 영향을 주는 확정된 기술 규칙과 결정을 관리한다.

## 문서별 역할

| 위치 | 역할 |
|---|---|
| `architecture/` | 시스템 구조, 패키지 구조와 계층 책임 |
| `domain/` | 도메인 용어, 불변식과 핵심 처리 규칙 |
| `conventions/` | Git, 데이터베이스 등 반복 적용되는 개발 규칙 |
| `adr/` | 중요한 기술 선택의 배경, 대안과 결과 |
| `learning/` | 구현 내용을 학습하고 발표하기 위한 쉬운 설명 |

## 다른 협업 도구와의 관계

- Notion은 논의 중이거나 변경이 잦은 유저 플로우와 기능 명세를 관리한다.
- Jira는 담당자, 작업 단위, 일정과 진행 상태를 관리한다.
- GitHub는 소스 코드와 확정된 개발 규칙의 변경 이력을 관리한다.
- `AGENTS.md`는 AI가 반드시 지킬 규칙과 상세 문서의 위치를 안내한다.

Notion에서 논의한 내용이 실제 구현을 제약하는 기술 결정으로 확정되면 관련 코드와 함께 이 디렉터리에 반영한다. 논의 중인 내용을 확정된 규칙처럼 기록하지 않는다.

## 문서 갱신 원칙

- 코드와 문서가 같은 결정을 설명하도록 같은 Pull Request에서 함께 변경한다.
- 상세 규칙을 `AGENTS.md`에 중복하지 않고 이 디렉터리에 기록한다.
- 문서와 코드가 충돌하면 충돌을 먼저 확인하고 임의로 한쪽을 기준으로 추정하지 않는다.
- 중요한 기술 선택은 `adr/`에 결정 이유와 영향을 남긴다.

## 주요 문서

- 패키지 구조: `architecture/package-structure.md`
- 쿠폰 발급 규칙: `domain/coupon.md`
- 쿠폰 실시간 잔여 재고 API: `api/coupon-stock.md`
- Redis 쿠폰 재고 복구 결정: `adr/002-redis-coupon-stock-recovery.md`
- Redis 쿠폰 재고 복구 학습: `learning/coupon-redis-recovery.md`
- 시청 포인트 지급 규칙: `domain/viewing-point.md`
- 승패 배팅 규칙: `domain/betting.md`
- Git, 커밋과 Pull Request: `conventions/git-convention.md`
- Jira–GitHub 자동화: `conventions/jira-github-workflow.md`
- 데이터베이스: `conventions/database-convention.md`
- 파일 인코딩과 줄바꿈: `conventions/file-convention.md`
