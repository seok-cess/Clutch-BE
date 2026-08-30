# 쿠폰 20,000 VU Ramp 테스트

## 목적

재고 10,000장에 중복 없는 가상 사용자 20,000명이 한 번씩 신청하는 상황을 재현한다.
VU는 60초 동안 0명에서 20,000명까지 선형적으로 증가한다.

이 시나리오는 `20,000 VU / ramp-up 60초` 조건을 검증한다. 20,000개의 HTTP 요청을
완전히 같은 시각에 발사하거나 TCP 연결 20,000개를 계속 유지하는 burst 테스트는 아니다.

## 기준 파일

- `run-coupon-ramp.ps1`: 실행 진입점, 환경변수, Test ID, 로그와 Prometheus 출력을 관리한다.
- `coupon-ramp.js`: VU 증가 단계와 사용자별 1회 신청을 정의한다.
- `../common/coupon-claim.js`: 이벤트 생성·오픈, 신청 요청, 지표와 최종 수량 검증을 담당한다.

일반 실행에서는 위 세 파일만 사용한다. `burst`, `distributed`, `smoke`는 목적이 다른 별도
시나리오이며 이 Ramp 테스트의 실행 경로에 포함되지 않는다.

## 사전 준비

- 백엔드, MySQL, Redis와 Kafka가 정상 동작해야 한다.
- Windows 부하 발생기에 네이티브 k6가 설치되어 있어야 한다.
- 백엔드와 Prometheus 주소에 접근할 수 있어야 한다.
- 사용자 ID `900001`부터 20,000명이 테스트 DB에 존재해야 한다.
- 활성 상태인 이름 `[K6] 10%`, 정률 10% 쿠폰 종류가 존재해야 한다.

k6 설치와 확인은 관리자 PowerShell에서 수행한다.

```powershell
choco install k6 -y
k6 version
```

스크립트 실행이 제한된 PowerShell에서는 현재 프로세스에만 실행을 허용한다.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
```

## 실행

저장소 루트에서 다음 명령을 실행한다.

```powershell
.\k6\ramp\run-coupon-ramp.ps1 `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -RampUpSeconds 60
```

20,000 VU 도달 상태를 더 오래 관찰하려면 `HoldSeconds`를 지정한다. 각 VU는 첫 번째
iteration에서만 신청하므로 유지 시간을 늘려도 신청 수는 20,000건으로 유지된다.

```powershell
.\k6\ramp\run-coupon-ramp.ps1 `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -RampUpSeconds 60 `
  -HoldSeconds 10
```

기본 주소를 변경할 때는 매개변수로 명시한다.

```powershell
.\k6\ramp\run-coupon-ramp.ps1 `
  -BaseUrl "http://100.101.76.93:8080" `
  -PrometheusUrl "http://100.105.168.7:9090/api/v1/write" `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -RampUpSeconds 60
```

## Test ID와 로그

Test ID를 생략하면 다음 형식으로 자동 생성한다.

```text
yyyyMMdd-HHmmss-coupon-20000vus-ramp60s
```

재실행 결과를 구분하려면 직접 지정한다.

```powershell
.\k6\ramp\run-coupon-ramp.ps1 `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -RampUpSeconds 60 `
  -TestId "coupon-native-20000-ramp60-run01"
```

Test ID는 로그 파일명과 Prometheus의 `testid` 태그에 함께 사용한다. 부하 발생기 태그는
`loadgen=native-windows`다. 로그는 아래 위치에 생성되며 Git에는 포함하지 않는다.

```text
k6/logs/<TestId>.log
```

로그에는 테스트 설정, 시작·종료 시각, k6 전체 출력, Threshold, PASS/FAIL과 종료 코드가
포함된다.

## 합격 기준

- `coupon_claim_attempt_total == 20000`
- `coupon_claim_success_total == 10000`
- `coupon_claim_sold_out_total == 10000`
- `coupon_claim_transport_failure_total == 0`
- `coupon_claim_unexpected_total == 0`
- `coupon_final_verification_success_total == 1`
- 신청 API 정상 응답의 p95가 5초 미만
- `vus`의 최대값이 20,000

`iterations`는 신청 건수가 아니다. 요청을 마친 VU가 테스트 종료까지 대기하는 iteration도
포함하므로 실제 신청 수는 `coupon_claim_attempt_total`을 기준으로 판정한다.

최종 검증은 관리자 이벤트 API의 `issuedQuantity`를 확인한다. `user_coupon`, 발급 요청 상태,
Outbox와 Kafka까지 포함한 DB 정합성은
[`docs/07-verification/README.md`](../../docs/07-verification/README.md)의 별도 검증을 수행한다.

## 문제 해결

Docker 실행기의 TCP 전송 실패, Windows 네이티브 전환과 설치·실행 오류는
[`쿠폰 20,000 VU 부하 테스트 트러블슈팅`](../../docs/08-troubleshooting/coupon-20000-vu-load-test-troubleshooting.md)을
참고한다.
