# 노트북 두 대 쿠폰 분산 Ramp 테스트

두 노트북이 같은 쿠폰 회차를 공유하고 같은 시각에 각각 10,000명씩 요청한다.
전체 조건은 중복 없는 사용자 20,000명, 재고 10,000개, ramp-up 60초다.

## 사전 준비

- 두 노트북은 동일한 Git 커밋을 사용한다.
- Docker Desktop을 실행하고 `grafana/k6:2.0.0` 이미지를 준비한다.
- 두 Windows 시스템 시간을 동기화한다.
- 백엔드와 Prometheus 주소에 두 노트북 모두 접근할 수 있어야 한다.
- 두 PowerShell에서 실행 정책을 현재 프로세스에만 허용한다.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

## 1. 노트북 A에서 공유 이벤트 준비

테스트 이벤트와 회차는 한 번만 생성한다.

```powershell
.\k6\distributed\prepare-distributed-coupon.ps1 `
  -BaseUrl "http://100.101.76.93:8080" `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -ClaimWindowSeconds 900
```

출력된 `EventId`와 `OccurrenceId`를 기록한다. 아래 명령의 숫자는 실제 출력값으로
교체한다. `TestId`와 `StartAt`은 두 노트북에서 완전히 동일해야 하며, 시작 시각은
현재보다 최소 2~3분 뒤로 지정한다.

## 2. 노트북 A 실행

```powershell
$eventId = 25
$occurrenceId = 41
$startAt = [datetime]"2026-08-30 18:30:00"
$testId = "two-laptop-20000-01"

.\k6\distributed\run-distributed-coupon.ps1 `
  -Node A `
  -EventId $eventId `
  -OccurrenceId $occurrenceId `
  -StartAt $startAt `
  -TestId $testId `
  -BaseUrl "http://100.101.76.93:8080" `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -RampUpSeconds 60 `
  -HoldSeconds 1
```

## 3. 노트북 B 실행

노트북 A와 같은 `EventId`, `OccurrenceId`, `StartAt`, `TestId`를 입력한다.

```powershell
$eventId = 25
$occurrenceId = 41
$startAt = [datetime]"2026-08-30 18:30:00"
$testId = "two-laptop-20000-01"

.\k6\distributed\run-distributed-coupon.ps1 `
  -Node B `
  -EventId $eventId `
  -OccurrenceId $occurrenceId `
  -StartAt $startAt `
  -TestId $testId `
  -BaseUrl "http://100.101.76.93:8080" `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -RampUpSeconds 60 `
  -HoldSeconds 1
```

각 스크립트는 k6를 paused 상태로 준비한 뒤 `StartAt`에 자동으로 재개한다. A는
전체 execution segment의 앞 절반, B는 뒤 절반을 담당하므로 사용자 ID가 겹치지
않는다. 각 노트북은 0명에서 10,000명까지 60초 동안 증가하며 사용자별로 쿠폰을
정확히 한 번 요청한다.

각 노트북은 다음 조건을 독립적으로 검증한다.

- 쿠폰 요청 시도 정확히 10,000건
- 전송 실패 0건
- HTTP 비정상 응답 0건
- 정상 응답 비율 99% 초과
- 정상 응답 p95 5초 미만

응답을 받은 연결은 `noConnectionReuse` 설정으로 닫고, VU는 피크 사용자 수 측정을
위해 sleep 상태로 유지한다. 결과는 다음 파일에 저장된다.

```text
k6/logs/<TestId>-laptop-a.log
k6/logs/<TestId>-laptop-a.stderr.log
k6/logs/<TestId>-laptop-b.log
k6/logs/<TestId>-laptop-b.stderr.log
```

## 4. 최종 발급 수량 검증

두 노트북의 실행이 모두 끝난 뒤 한쪽에서 한 번만 실행한다.

```powershell
.\k6\distributed\verify-distributed-coupon.ps1 `
  -BaseUrl "http://100.101.76.93:8080" `
  -EventId $eventId `
  -ExpectedQuantity 10000
```

최종 판정 조건은 A 요청 10,000건 + B 요청 10,000건, 발급 성공 합계 10,000건,
품절 합계 10,000건, 전송 실패 합계 0건, 최종 발급량 10,000개다. Grafana에서는
동일한 `testid`와 `loadgen=laptop-a`, `loadgen=laptop-b` 태그로 두 실행기를
합산하거나 분리해 확인한다.
