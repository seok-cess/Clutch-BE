# Jira–GitHub 브랜치 자동화 사용 가이드

## 목적

Clutch 백엔드에서는 Jira 업무 항목을 생성할 때 업무 유형과 라벨에 따라 GitHub 작업 브랜치를 자동으로 생성한다.

- 저장소: `seok-cess/Clutch-BE`
- 기준 브랜치: `dev`
- 브랜치 형식: `<type>/<Jira-key>`
- 예시: `feat/CLUTCH-123`

## Jira 업무 유형

| 업무 유형 | 사용 목적 | 브랜치 생성 |
|---|---|---|
| Story | 사용자 시나리오와 상위 기능 관리 | 생성하지 않음 |
| Task | 기능 구현, 리팩터링, 문서와 환경 작업 등 PR 단위 작업 | 라벨에 따라 생성 |
| Bug | 버그 수정 | `fix` 브랜치 생성 |
| Subtask | 세부 일정과 보조 작업 | 생성하지 않음 |

## Task 생성 방법

Jira 상단의 검색 옆에서 **만들기**를 선택하고 다음 값을 생성 화면에서 지정한다.

1. 업무 유형을 `Task`로 선택한다.
2. 필요한 경우 상위 항목에서 관련 백엔드 Epic을 선택한다.
3. 작업 영역 라벨 `backend`를 선택한다.
4. 작업 타입 라벨을 정확히 하나만 선택한다.
5. 업무 항목을 생성한다.

| 작업 타입 라벨 | 용도 | 생성 브랜치 |
|---|---|---|
| `feat` | 새로운 기능 개발 | `feat/CLUTCH-123` |
| `refactor` | 기능 변화 없는 코드 개선 | `refactor/CLUTCH-123` |
| `chore` | 빌드, 설정, 패키지와 환경 작업 | `chore/CLUTCH-123` |
| `docs` | README와 기술 문서 작업 | `docs/CLUTCH-123` |

예시:

```text
업무 유형: Task
상위 항목: 관련 Backend Epic
라벨: backend, feat
Jira 키: CLUTCH-123

생성 브랜치: feat/CLUTCH-123
```

## Bug 생성 방법

백엔드 버그는 다음과 같이 생성한다.

```text
업무 유형: Bug
라벨: backend
```

`fix` 라벨은 별도로 지정하지 않는다. `Bug` 업무 유형을 기준으로 다음 브랜치가 자동 생성된다.

```text
fix/CLUTCH-124
```

## 자동화 주의사항

### 라벨은 생성 화면에서 지정

자동화는 Jira 업무 항목이 생성되는 순간 실행된다. 업무 항목을 만든 뒤 라벨을 추가하면 브랜치가 자동 생성되지 않으므로 `backend`와 타입 라벨을 생성 전에 지정한다.

### Task 타입 라벨은 하나만 선택

Task에는 `feat`, `refactor`, `chore`, `docs` 중 하나만 선택한다.

```text
# 잘못된 예
backend, feat, docs

# 올바른 예
backend, feat
```

### `branch-created` 라벨

브랜치가 정상 생성되면 Jira 자동화가 `branch-created` 라벨을 추가한다. 이 라벨은 생성 완료 표시이자 중복 생성 방지용이다.

- 직접 추가하지 않는다.
- 특별한 재처리 상황이 아니면 삭제하지 않는다.

## 정상 처리 확인

1. Jira에 `branch-created` 라벨이 추가됐는지 확인한다.
2. GitHub `Clutch-BE` 저장소에서 브랜치를 확인한다.
3. 브랜치가 `dev`를 기준으로 생성됐는지 확인한다.
4. 브랜치 이름이 `<type>/CLUTCH-<issue>` 형식인지 확인한다.

## 전체 작업 흐름

```text
Jira 업무 항목 생성
→ 업무 유형과 생성 시점의 라벨 확인
→ dev 기준 GitHub 브랜치 자동 생성
→ branch-created 라벨 자동 추가
→ 개발자가 작업 브랜치에서 작업
→ dev 대상 Pull Request 생성
→ CI 검사와 코드 리뷰
→ Squash and merge
```

## 브랜치가 생성되지 않을 때

다음 항목을 확인한다.

- 업무 유형이 `Task` 또는 `Bug`인지
- `backend` 라벨이 있는지
- Task에 작업 타입 라벨이 정확히 하나만 있는지
- 라벨을 업무 항목 생성 화면에서 지정했는지
- 이미 `branch-created` 라벨이 있는지

조건이 모두 맞는데도 생성되지 않으면 자동화 담당자에게 문의한다.
