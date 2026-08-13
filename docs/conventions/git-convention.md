# Git 규칙

## 브랜치 전략

프론트엔드와 백엔드 저장소는 분리해 관리하며 동일한 브랜치 전략을 사용한다.

```text
main              배포·시연 가능한 안정 브랜치
└── dev           개발 통합 브랜치
    ├── feat/*       기능 개발
    ├── fix/*        버그 수정
    ├── refactor/*   기능 변화 없는 코드 개선
    ├── chore/*      빌드·설정·환경 작업
    └── docs/*       문서 작업
```

### `main`

- 항상 실행하고 시연할 수 있는 안정 상태를 유지한다.
- 직접 push하지 않는다.
- `dev`에서 충분히 검증된 코드만 Pull Request를 통해 병합한다.

### `dev`

- 팀의 개발 결과물이 모이는 통합 브랜치다.
- 직접 push하지 않는다.
- 모든 개발 작업은 작업 브랜치에서 수행한 뒤 Pull Request로 병합한다.

### 작업 브랜치

- Jira 작업 하나당 브랜치 하나를 사용한다.
- 최신 `dev`를 기준으로 생성한다.
- 작업 완료 후 `dev`를 대상으로 Pull Request를 생성한다.

## 브랜치 이름

브랜치 이름은 Jira 이슈 키를 포함해 다음 형식을 사용한다.

```text
<type>/CLUTCH-<issue>
```

설명 suffix를 추가하지 않는다. 타입은 소문자, Jira 프로젝트 키는 대문자를 유지한다.

| 타입 | 용도 | 예시 |
|---|---|---|
| `feat` | 새로운 기능 개발 | `feat/CLUTCH-112` |
| `fix` | 버그 수정 | `fix/CLUTCH-64` |
| `refactor` | 기능 변화 없는 코드 개선 | `refactor/CLUTCH-49` |
| `chore` | 빌드, CI, Docker, 설정, 패키지 작업 | `chore/CLUTCH-62` |
| `docs` | README와 기술 문서 작업 | `docs/CLUTCH-120` |

Jira를 통한 자동 생성 방법은 `jira-github-workflow.md`를 따른다.

## 기본 개발 흐름

```text
Jira 이슈 생성
→ dev 기준 GitHub 작업 브랜치 자동 생성
→ 로컬 작업
→ Commit
→ Push
→ dev 대상 Pull Request
→ Backend CI
→ Code Review
→ Squash and merge
→ dev
```

## 커밋 메시지

Conventional Commits 형식을 사용한다.

```text
<type>: <제목>
```

백엔드는 변경 범위를 명확히 할 필요가 있을 때 도메인 scope를 사용할 수 있다.

```text
<type>(<domain>): <제목>
```

예시:

```text
feat: 회원가입 API 구현
fix: 쿠폰 수량이 null일 때 오류 수정
docs: API 명세서 업데이트
chore: Docker Compose 설정 추가
feat(auth): JWT 로그인 구현
feat(match): 경기 목록 조회 API 구현
feat(coupon): 쿠폰 발급 API 구현
fix(coupon): 중복 발급 검증 오류 수정
refactor(match): 경기 조회 로직 분리
chore(ci): GitHub Actions 설정 추가
```

| 타입 | 용도 |
|---|---|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변화 없는 코드 개선 |
| `style` | 포맷팅 등 동작 변화 없는 수정 |
| `docs` | 문서 변경 |
| `test` | 테스트 코드 변경 |
| `chore` | 빌드, CI, 설정과 패키지 변경 |

커밋 메시지는 다음 규칙을 따른다.

- 제목은 한글 명령형으로 작성한다.
- 제목은 50자 이내를 권장한다.
- 제목 끝에 마침표를 붙이지 않는다.
- 상세 설명이 필요하면 제목 다음에 빈 줄을 두고 본문을 작성한다.
- 하나의 커밋에는 가능한 한 하나의 목적만 포함한다.

## Pull Request

- 모든 작업 브랜치는 `dev`를 대상으로 Pull Request를 생성한다.
- `main`과 `dev`에 직접 push하지 않는다.
- 하나의 기능 또는 작업 단위로 작게 작성한다.
- 최소 1명 이상의 리뷰와 승인을 받아야 한다.
- Backend CI가 성공한 경우에만 병합한다.
- 기본 병합 방식은 Squash and merge다.

Pull Request 제목에는 Jira 이슈 키를 포함한다.

```text
[CLUTCH-13] 백엔드 CI 구축
[CLUTCH-21] 쿠폰 발급 API 구현
[CLUTCH-32] 경기 목록 조회 구현
```

## 검증과 보안

- 변경 범위에 맞는 테스트를 실행한다.
- 전체 빌드와 테스트 명령은 `./gradlew clean build`이다.
- GitHub Actions의 Backend CI는 `dev` 대상 Pull Request에서 실행된다.
- 비밀값, `.env`, 개인용 `application.yaml`을 커밋하지 않는다.
