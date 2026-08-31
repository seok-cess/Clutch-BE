# 관리자 쿠폰 일별 통계 조회 개선 검증 결과

- 검증일: 2026-08-31
- 대상: 관리자 운영 홈의 최근 7일 성공·실패 추이 조회
- 환경: 로컬 Docker Compose MySQL 8.4.7

## 변경 전 -> 변경 후

```text
관리자 요청마다 최근 7일 원본 634,917건 GROUP BY
-> Kafka Consumer가 미리 누적한 KST 일별 통계 7행 조회

성공·실패 Claim과 사전 거절 원본을 각각 조회한 뒤 합산
-> coupon_issue_daily_statistics 한 테이블에서 합산

새로고침 횟수만큼 대량 집계 반복
-> 대량 백필은 Flyway 배포 시 한 번만 수행하고 신규 값은 Kafka로 증분 반영
```

## 기능 검증

실행 명령:

```text
gradlew.bat test
  --tests com.clutch.coupon.admin.dashboard.*
  --tests com.clutch.coupon.statistics.*
```

결과:

- BUILD SUCCESSFUL
- `gradlew.bat clean build` 전체 558개 테스트 통과
- 관리자 Dashboard Controller·Service·Repository 테스트 통과
- 결과 및 거절 Kafka Consumer·Service·Repository 테스트 통과
- 같은 `messageId`의 재처리에서 일별 통계가 중복 증가하지 않음을 확인
- KST 기준 일별 성공과 사전 거절이 기존 Dashboard 응답에 포함됨을 확인

## 조회 성능 비교

운영 화면에서 관찰된 7일 통계 총량과 같은 `634,917건`을 임시 InnoDB 테이블에 생성했다.
변경 전 날짜별 원본 집계와 변경 후 일별 통계 7행 조회를 같은 MySQL 세션에서
`EXPLAIN ANALYZE`로 측정했다. 임시 테이블은 세션 종료와 함께 삭제됐다.

| 항목 | 변경 전 | 변경 후 |
|---|---:|---:|
| 조회 대상 행 | 634,917행 | 7행 |
| 실제 실행 시간 | 447ms | 0.0296ms |
| 쿼리 구간 속도 | 기준 | 약 15,100배 빠름 |
| 실행 시간 감소율 | 기준 | 약 99.993% 감소 |

변경 전 실행 계획은 원본 전체 테이블 스캔과 임시 테이블 GROUP BY를 수행했다. 변경 후에는
`coupon_issue_daily_statistics` 기본 키의 날짜 범위 인덱스 스캔으로 7행만 읽었다.

## 원격 환경 기준과 남은 검증

변경 전 원격 관리자 API는 90회 누적 평균 약 `7.13초`, 직접 호출 약 `6.8~8.5초`였다.
이 값에는 운영 서버의 CPU·디스크, 오늘 요약, 이벤트 목록과 Redis 재고 조회가 모두 포함된다.

이번 `447ms -> 0.0296ms`는 동일 로컬 DB에서 병목 쿼리만 분리한 비교다. 아직 변경 코드가
원격 서버에 배포되지 않았으므로 개선 후 원격 API 전체 응답 시간은 배포 후 같은 URL로 다시
측정해야 한다. 로컬 쿼리 결과를 원격 API 결과인 것처럼 합쳐서 해석하지 않는다.
