# ADR-007: Kafka 기반 관리자 쿠폰 일별 통계

- Status: Accepted
- Date: 2026-08-31
- Decision Makers: 관리자 쿠폰 대시보드 담당자

## Context

관리자 운영 홈은 최근 1~30일의 성공·실패 추이를 요청할 때마다
`coupon_claim_request`와 `coupon_claim_rejection_message` 원본을 날짜별로 다시 집계했다.
부하 테스트 데이터가 최근 7일에 약 63만 건 누적된 환경에서는 작은 JSON 응답 하나를
만들기 위해 같은 범위의 원본을 반복해서 읽어 조회 시간이 약 7초까지 증가했다.

기존 `coupon.issue.result`와 `coupon.claim.rejected` Consumer에는 `messageId` 기준 멱등
처리가 이미 적용돼 있다. 관리자 API 계약을 변경하지 않으면서 이 처리 결과를 조회용
일별 Projection으로 누적할 필요가 있다.

## Decision

- `coupon_issue_daily_statistics`에 KST 기준일과 쿠폰 이벤트별 성공, 처리 실패와 사전 거절
  누적값을 저장한다.
- 결과와 거절 메시지 원본의 `INSERT IGNORE`가 성공한 경우에만 같은 transaction에서 일별
  통계를 증가시킨다. 같은 Kafka 메시지가 재전달되면 일별 통계도 증가하지 않는다.
- 발급 성공과 처리 실패의 통계 날짜는 기존 운영 홈과 호환되도록 Claim 생성 시각을 KST로
  변환해 정한다. 사전 거절은 이벤트 발생 시각을 KST로 변환한다.
- `V22__coupon_issue_daily_statistics.sql`에서 기존 Claim과 거절 원본을 최초 한 번 선집계한다.
  Flyway 완료 후 새 메시지는 Consumer가 이어서 누적한다.
- 운영 홈의 오늘 요약과 발급 추이는 일별 통계를 읽는다. 아직 최종 결과가 없는 `PENDING`과
  `CANCELLED` Claim만 오늘 범위의 원본에서 확인한다.
- 기존 HTTP 경로, 요청 파라미터와 응답 DTO는 변경하지 않는다.

## 처리 흐름

```text
신규 발급 결과 또는 신청 거절
  -> 기존 Kafka Consumer
  -> messageId 원본 INSERT IGNORE
  -> 처음 처리한 메시지인 경우
       -> 기존 이벤트별 누적 통계 증가
       -> KST 일별 통계 증가

관리자 운영 홈 조회
  -> 최근 1~30일 일별 통계 행 조회
  -> 오늘 PENDING/CANCELLED만 Claim 원본 조회
  -> 기존 관리자 응답 형식으로 조립
```

## Consequences

- 관리자 조회 비용이 최근 원본 발급 건수에 비례하지 않고 조회 기간의 날짜·이벤트 통계
  행 수에 비례한다.
- Kafka가 지연되면 성공·실패·사전 거절 통계도 잠시 늦게 보인다. 실제 쿠폰 발급 결과와
  사용자 응답에는 영향이 없다.
- 배포 시 한 번 수행하는 백필은 기존 원본 수에 비례한다. 이후 관리자 요청에서는 같은
  대량 집계를 반복하지 않는다.
- 현재 단일 애플리케이션 배포는 기존 인스턴스를 중지한 뒤 신규 인스턴스에서
  Flyway와 Consumer를 순서대로 시작한다. 구버전 Consumer가 함께 동작하는 롤링 배포는
  일별 통계 호환 절차를 별도로 정하기 전까지 사용하지 않는다.
- 이벤트가 정리된 뒤 늦게 도착한 거절도 기록할 수 있도록 일별 통계의 이벤트 ID에는
  외래 키를 두지 않는다.

## Alternatives Considered

### 원본 테이블에 복합 인덱스만 추가

테이블 접근 비용은 줄지만 데이터가 계속 증가하면 관리자 요청마다 전체 기간을 집계하는
구조는 남는다.

### 짧은 TTL 캐시

반복 요청은 빨라지지만 캐시가 비었을 때의 최초 요청은 여전히 느리고, 데이터 증가에 따른
집계 비용을 해결하지 못한다.

## 관련 코드

- `com.clutch.coupon.statistics.repository.CouponIssueStatisticsRepository`
- `com.clutch.coupon.statistics.repository.CouponClaimRejectionStatisticsRepository`
- `com.clutch.coupon.admin.dashboard.repository.AdminCouponDashboardQueryRepository`
- `src/main/resources/db/migration/V22__coupon_issue_daily_statistics.sql`
