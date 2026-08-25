# CLUTCH Development Guide

## 적용 범위

이 문서는 저장소 전체에 적용되는 AI 개발 규칙이다. Claude Code와 Codex는 코드 작업 전에 이 문서와 작업에 관련된 `docs/` 문서를 확인한다.

## 기준 문서

- `AGENTS.md`: AI가 반드시 따를 공통 개발 규칙
- `README.md`: 사람을 위한 프로젝트 소개와 로컬 실행 방법
- `docs/`: 실제 구현에 영향을 주는 확정된 기술 규칙과 결정
- Notion: 논의 중이거나 변경이 잦은 유저 플로우와 기능 명세
- Jira: 담당자, 작업 단위, 일정과 진행 상태
- GitHub: 소스 코드와 확정된 개발 규칙의 이력

Notion의 논의가 구현 결정으로 확정되면 관련 코드와 함께 `docs/`에 반영한다. Git에 기록되지 않은 논의를 확정된 기술 규칙으로 추정하지 않는다.

## 프로젝트 기술

- Java 21
- Spring Boot 4.1
- Gradle
- MySQL 8
- Spring Data JPA와 Flyway
- Redis
- Kafka
- Docker Compose

세부 실행 방법은 `README.md`를 따른다.

## 문서 안내

- 패키지와 계층 책임: `docs/architecture/package-structure.md`
- 쿠폰 발급 도메인 규칙: `docs/domain/coupon.md`
- 시청 포인트 지급 규칙: `docs/domain/viewing-point.md`
- 승패 배팅 규칙: `docs/domain/betting.md`
- 매치·세트 상태와 결과 데이터 계약: `docs/domain/match-set-result.md`
- 데이터베이스 규칙: `docs/conventions/database-convention.md`
- 파일 인코딩과 줄바꿈 규칙: `docs/conventions/file-convention.md`
- Git과 브랜치 규칙: `docs/conventions/git-convention.md`
- Jira 이슈와 브랜치 자동화: `docs/conventions/jira-github-workflow.md`
- 기술 결정 기록 방법: `docs/adr/README.md`

## 아키텍처 규칙

- Controller는 요청 수신과 검증, 서비스 호출, HTTP 응답 생성을 담당한다.
- Controller에 비즈니스 판단, 데이터 조회 전략, 집계 로직을 새로 작성하지 않는다.
- Entity를 API 응답으로 직접 반환하지 않고 DTO를 사용한다.
- Service는 유스케이스 흐름과 트랜잭션 경계를 담당한다.
- Repository는 데이터 접근을 담당한다.
- 기존 코드가 이 규칙을 완전히 따르지 않더라도 현재 작업과 관계없는 전체 리팩터링을 수행하지 않는다.
- 기존 코드를 수정할 때는 작업 범위 안에서 새 비즈니스 로직이 Controller에 추가되지 않도록 한다.

## 데이터베이스 규칙

- 데이터베이스 스키마는 `src/main/resources/db/migration/`의 Flyway migration으로 관리한다.
- 애플리케이션은 Hibernate `ddl-auto: validate`를 사용한다.
- 날짜와 시각은 UTC를 기준으로 저장하고 처리한다.
- 공통 설정 변경 시 `application.example.yaml`을 함께 확인한다.
- 개인용 `application.yaml`과 `.env`는 커밋하지 않는다.

## 포인트 및 배팅 규칙

- 시청 시간 5분마다 수령 버튼을 통해 100포인트를 지급한다.
- 수령 버튼이 표시된 동안에는 시청 시간을 추가로 누적하지 않고, 수령 후 누적 시간을 초기화한다.
- 여러 동시 경기에서 포인트를 함께 적립하는 기능은 구현하지 않는다.
- 현재 배팅 범위는 매치의 세트 승패이며 배팅 금액은 1,000포인트 이상 100,000포인트 이하다.
- 총 풀에서 운영 수수료 10%를 제외한 배당 풀을 적중자의 배팅 금액 비율로 정산하고,
  실패 시 배팅 포인트를 몰수한다.

상세 규칙은 `docs/domain/viewing-point.md`와 `docs/domain/betting.md`를 따른다.

## 파일 형식 규칙

- 텍스트 파일은 UTF-8로 작성한다.
- 기본 줄바꿈 형식은 CRLF이며 `.gitattributes`와 `.editorconfig`를 기준으로 한다.
- Linux와 macOS에서 직접 실행되는 `gradlew` 및 셸 스크립트는 LF를 유지한다.
- 줄바꿈 형식만 바꾸는 불필요한 전체 파일 수정은 만들지 않는다.

세부 규칙은 `docs/conventions/file-convention.md`를 따른다.

## Git 규칙

- 통합 브랜치는 `dev`이다.
- 작업 브랜치는 `dev`를 기준으로 생성한다.
- Pull Request 대상 브랜치는 `dev`이다.
- `main`과 `dev`에 직접 push하지 않는다.
- 브랜치 이름은 `<type>/CLUTCH-<issue>` 형식을 사용하며 설명 suffix를 붙이지 않는다.
- Jira 작업 하나당 작업 브랜치 하나를 사용한다.
- 커밋 메시지는 Conventional Commits 형식을 사용하고 한글로 작성한다.
- Pull Request 제목은 `<type>:[CLUTCH-<issue>] <작업 내용>` 형식을 사용한다.
- Backend CI 성공과 1명 이상의 승인을 받은 후 Squash and merge한다.

세부 규칙은 `docs/conventions/git-convention.md`와 `docs/conventions/jira-github-workflow.md`를 따른다.

## AI 작업 규칙

코드를 수정하기 전에 다음 순서를 따른다.

1. 작업과 관련된 기존 코드를 먼저 확인한다.
2. 관련 `docs/` 문서를 확인한다.
3. 확정된 규칙 안에서 기존 구현 패턴을 우선한다.
4. 요구사항에 없는 기능을 임의로 추가하지 않는다.
5. 작업과 관계없는 코드를 리팩터링하지 않는다.
6. 작업 범위를 벗어난 파일을 수정하지 않는다.
7. 핵심 로직 변경 시 관련 테스트를 확인하고 필요한 테스트를 함께 수정하거나 추가한다.
8. 구현에 영향을 주는 확정된 기술 결정이 변경되면 관련 `docs/`도 같은 작업에서 갱신한다.
9. 문서와 코드가 충돌하거나 결정이 명확하지 않으면 임의로 규칙을 만들지 않고 충돌을 알린다.

## 검증

- 변경 범위에 맞는 테스트를 우선 실행한다.
- 전체 검증 명령은 `./gradlew clean build`이다.
- 통합 테스트에 MySQL, Redis 또는 Kafka가 필요하면 `docker compose up -d --wait`로 인프라를 준비한다.
- 테스트를 실행하지 못한 경우 실행하지 못한 항목과 이유를 결과에 명시한다.
