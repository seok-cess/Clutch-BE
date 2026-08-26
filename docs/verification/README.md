# 쿠폰 대량 데이터 정합성 검증

## 목적

가상 사용자 100만 명과 쿠폰 발급 요청 이력 300만 건 이상이 적재된 MySQL에서
발급 요청, 실제 사용자 쿠폰, 재고와 상태 전이가 서로 일치하는지 검증한다.
검증은 데이터를 수정하지 않는 읽기 전용 쿼리로 수행한다.

이 프로젝트에서 과제의 `발급 이력`은 관리자 발급 내역의 기준 테이블인
`coupon_claim_request`로 해석한다. `user_coupon`은 이 요청 중 실제 발급에 성공한
건만 저장하므로 300만 건과 정확히 같을 필요가 없다.

검증 SQL은 [`coupon-integrity.sql`](coupon-integrity.sql)이다.

## 실행 전 준비

- k6, 더미데이터 적재와 쿠폰 상태 변경 작업을 중단한다.
- 가능하면 조회 전용 MySQL 계정을 사용한다.
- 운영 중인 DB에서 실행한다면 DB 부하가 낮은 시간에 실행한다.
- MySQL Workbench에서 원격 Tailscale MySQL에 접속하거나 MySQL CLI를 사용한다.
- 비밀번호는 명령어나 Git 파일에 기록하지 않고 실행 시 입력한다.

Windows에서 MySQL CLI를 실행하는 예시는 다음과 같다. PowerShell의 `<` 입력
리디렉션 제약을 피하기 위해 `cmd.exe /c`로 실행한다.

```powershell
cmd.exe /c "mysql --host=100.101.76.93 --port=3306 --user=clutch --password --database=clutch --default-character-set=utf8mb4 < docs\verification\coupon-integrity.sql"
```

MySQL Workbench에서는 원격 DB 연결을 선택하고 SQL 파일 전체를 실행한다.

## 결과 판정

결과는 `PASS`, `WARN`, `FAIL`로 구분한다.

- `PASS`: 위반 데이터가 없다.
- `WARN`: 데이터 손상으로 단정할 수 없지만 설계 또는 운영 확인이 필요하다.
- `FAIL`: 참조, 상태, 중복 또는 재고 정합성 위반이 존재한다.

발표 전 합격 기준은 다음과 같다.

- 모든 `FAIL` 항목의 `violation_count`가 0이다.
- `WARN` 항목의 발생 원인을 설명하고, 허용 여부를 문서에 기록한다.
- 사용자 수가 100만 명 이상이다.
- 쿠폰 발급 요청 이력이 300만 건 이상이다.
- 같은 데이터로 재실행한 `row_count`, `min_id`, `max_id`,
  `data_fingerprint`가 동일하다.

## 주요 검증 항목

- 사용자·이벤트·회차·이벤트 항목·발급 요청의 고아 참조
- 발급 요청과 실제 쿠폰의 사용자·이벤트·회차·항목 불일치
- 성공한 요청에 실제 쿠폰이 없거나 성공하지 않은 요청에 쿠폰이 있는 경우
- 동일 사용자·회차의 중복 요청과 중복 쿠폰
- 이벤트 항목 수량 초과 발급과 `success_count` 불일치
- `ISSUED`, `USED`, `EXPIRED`, `CANCELLED` 상태와 처리 시각 불일치
- 오래 남은 `PENDING` 요청과 만료 후에도 `OPEN`인 회차
- Java enum과 데이터베이스 상태 계약 불일치
- 300만 건 집계에 필요한 이벤트 항목 선두 인덱스 존재 여부

## 재실행 가능성

SQL은 첫 실행 시 `@as_of_utc`에 UTC 기준 시각을 저장한다. 같은 Workbench 세션에서
다시 실행하면 동일한 기준 시각을 사용하므로, 데이터가 바뀌지 않았다면 동일한 결과가
나와야 한다.

새 연결에서도 같은 기준 시각을 사용하려면 SQL 실행 전에 아래 값을 첫 실행 결과의
`as_of_utc`로 지정한다.

```sql
SET @as_of_utc = '2026-08-26 04:30:00.000000';
```

`data_fingerprint`는 개인정보를 출력하지 않고 데이터 변경 여부를 비교하기 위한 보조
지표다. CRC32 기반이라 정합성 검사를 대체하지 않는다.

## 현재 확인이 필요한 계약 차이

Flyway 스키마는 사용자 쿠폰의 `EXPIRED` 상태와 발급 요청의 `CANCELLED` 상태를
허용한다. 현재 Java enum은 각각 해당 상태를 포함하지 않으므로 DB에 값이 존재하면
JPA 조회가 실패할 수 있다. 검증 SQL은 이를 `WARN`으로 표시한다.

만료를 `expires_at`으로 계산할지, 만료 배치로 `EXPIRED` 상태를 저장할지는 별도
도메인 결정이 필요하다. 결정 전에는 검증 결과를 자동으로 수정하지 않는다.

## 검증 결과 기록

검증할 때마다 `results/YYYY-MM-DD-coupon-integrity.md`에 기준 시각, 전체 건수,
PASS/WARN/FAIL 요약, fingerprint와 후속 조치를 남긴다. 개인정보와 접속 비밀번호는
기록하지 않는다.

## 주의사항

- 이 SQL에는 `UPDATE`, `DELETE`, `INSERT`와 DDL이 없다.
- 오류가 발견되어도 검증 SQL은 데이터를 자동 보정하지 않는다.
- 읽기 전용 일관 스냅샷을 오래 유지하면 MySQL purge에 영향을 줄 수 있으므로 실행
  시간을 기록한다.
- 실행 결과에는 개인 식별 정보가 포함되지 않지만 DB 호스트와 전체 건수는 포함된다.
