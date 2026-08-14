# 패키지 구조와 계층 책임

## 기본 방향

애플리케이션 코드는 `com.clutch` 아래에서 도메인을 기준으로 나누고, 각 도메인 내부에서 역할별 패키지를 사용한다. 공통 설정과 예외, 외부 기술 연동은 도메인 코드와 분리한다.

아래 구조는 점진적으로 맞춰갈 목표 구조다. 기존 패키지를 문서에 맞추기 위해 현재 작업과 관계없는 일괄 이동을 수행하지 않는다.

```text
com.clutch
├── global
│   ├── config
│   └── exception
├── user
├── match
├── coupon
│   ├── event
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   ├── claim
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   └── issued
├── admin
│   ├── monitoring
│   └── statistics
└── infrastructure
    ├── kafka
    ├── redis
    └── metrics
```

현재 일부 JPA Entity는 `domain` 패키지에 있다. `domain`을 `entity`로 변경하는 작업은 별도 리팩터링으로 진행하며, 다른 기능 작업에 끼워서 이동하지 않는다.

공통 API 응답과 전역 예외의 구체적인 형식 및 패키지는 아직 확정하지 않는다.

## 배팅 패키지 구조

배팅 도메인은 계층 패키지를 유지하되, DTO와 스케줄 실행 책임을 Service에서 분리한다.

```text
com.clutch.betting
├── api                  # HTTP 요청 처리와 예외 응답 변환
├── config
├── domain
├── dto
│   ├── request          # HTTP 요청 DTO
│   └── response         # HTTP 응답 DTO
├── exception
├── live                 # 최신 매치·세트 상태 조회 계약과 lolesports 구현
├── repository
├── scheduler            # 주기 실행과 대상별 실패 격리
└── service              # 등록·조회, 환불, 정산, 라이브 상태 동기화 유스케이스
```

`live`는 외부 시스템 연동 방식보다 배팅 도메인에 제공하는 역할을 기준으로 이름을 정한다. 배팅 Service는 `LiveBettingDataProvider` 계약에 의존하고, lolesports 캐시를 사용하는 구현 세부사항은 `LolesportsLiveBettingDataProvider`가 담당한다.

사용자 요청에서 함께 사용되는 등록과 조회는 `BettingService`가 담당한다. 환불·정산·라이브 상태 동기화는 각각 독립된 트랜잭션 경계를 유지하는 Service가 담당하고, Scheduler는 처리 대상 탐색과 개별 실패 격리만 조율한다. 계층 간 전달 모델과 처리 결과는 `dto`에 모아 Service 패키지에 데이터 전용 클래스를 두지 않는다.

## 계층별 책임

### Controller

- HTTP 요청과 경로, 헤더, 요청 본문을 처리한다.
- 입력 검증 결과와 서비스 결과를 HTTP 응답으로 변환한다.
- Entity를 직접 반환하지 않고 응답 DTO를 사용한다.
- 비즈니스 판단, 데이터 조회 전략과 집계 로직을 새로 작성하지 않는다.

### Service

- 하나의 유스케이스에 필요한 처리 흐름을 조정한다.
- 트랜잭션 경계를 관리한다.
- 도메인 객체와 Repository를 사용해 비즈니스 규칙을 실행한다.

### Entity

- 영속화할 도메인 상태와 해당 상태에 적용되는 규칙을 표현한다.
- 상태 변경은 의미 있는 메서드를 통해 수행한다.
- API 표현 형식에 의존하지 않는다.

### DTO

- API 요청과 응답 또는 계층 간 전달에 필요한 데이터를 표현한다.
- Entity를 외부 API에 직접 노출하지 않도록 경계를 만든다.

### Repository

- 데이터 저장과 조회를 담당한다.
- 유스케이스 흐름이나 HTTP 응답 처리를 담당하지 않는다.

### Infrastructure

- Kafka, Redis와 metrics 같은 외부 기술 연동을 담당한다.
- 특정 도메인의 비즈니스 규칙을 직접 소유하지 않는다.

## 기존 코드 적용 원칙

`lolesports/api/ApiController`에는 응답 변환과 집계 등 여러 책임이 남아 있다. 이 파일은 기존 코드의 개선 대상으로 취급한다.

- 문서 도입만을 이유로 전체 파일을 리팩터링하지 않는다.
- 관련 엔드포인트를 수정할 때 Controller에 새 비즈니스 로직을 추가하지 않는다.
- 작업 범위 안에서 필요한 로직은 Service 또는 별도 Mapper/Assembler로 분리한다.
- 기존 `api`, `domain`, `lolesports` 등 패키지 이동은 별도 작업과 검증 없이 수행하지 않는다.
