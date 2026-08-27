# 노트북 두 대 쿠폰 분산 테스트

## 준비

두 노트북은 동일한 Git 커밋을 사용하고 Docker Desktop을 실행한다. 두 노트북의 Windows 시간을 동기화하고 백엔드, Prometheus 주소에 접근할 수 있어야 한다.

한 노트북에서 테스트 이벤트를 한 번만 생성하고 연다.

```powershell
.\k6\prepare-distributed-coupon.ps1 `
  -BaseUrl "http://100.101.76.93:8080" `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -ClaimWindowSeconds 900
```

출력된 `EventId`와 `OccurrenceId`를 두 노트북에서 동일하게 사용한다. `TestId`와 `StartAt`도 양쪽에서 완전히 같아야 한다. 시작 시각은 명령 실행 시점보다 여유 있게 지정한다.

## 노트북 A

```powershell
.\k6\run-distributed-coupon.ps1 `
  -Node A `
  -EventId 25 `
  -OccurrenceId 41 `
  -StartAt "2026-08-25 21:00:00" `
  -TestId "two-laptop-20000-01" `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -RampUpSeconds 60
```

## 노트북 B

```powershell
.\k6\run-distributed-coupon.ps1 `
  -Node B `
  -EventId 25 `
  -OccurrenceId 41 `
  -StartAt "2026-08-25 21:00:00" `
  -TestId "two-laptop-20000-01" `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -RampUpSeconds 60
```

각 스크립트는 `coupon-burst-distributed.js`의 `load` 단계를 k6 `--paused` 상태로 먼저 실행한다. k6 REST API가 준비되면 지정한 시각까지 기다렸다가 자동으로 실행을 재개한다. A는 전체 실행의 앞 절반, B는 뒤 절반을 담당한다. 두 실행기는 같은 시각부터 각각 10,000 VU까지 60초 동안 증가시키며, 각 VU는 쿠폰 신청을 정확히 한 번만 전송한다.

각 실행기는 자신이 맡은 요청 수, 비정상 응답, 전송 실패율과 응답시간 threshold를 독립적으로 검증한다. 전체 성공 수량은 두 실행이 끝난 후 별도 `verify` 단계에서 확인한다. 두 실행기에는 동일한 `testid`와 서로 다른 `loadgen` 태그가 기록된다.

## 최종 검증

두 노트북이 모두 완료된 후 한쪽에서 최종 발급 수량을 검증한다.

```powershell
.\k6\verify-distributed-coupon.ps1 `
  -BaseUrl "http://100.101.76.93:8080" `
  -EventId 25 `
  -ExpectedQuantity 10000
```

Grafana에서는 같은 `testid`에 속한 두 `loadgen`의 성공, 품절, 비정상 결과를 합산해서 확인한다.
