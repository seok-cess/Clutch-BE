# 쿠폰 20,000 VU 부하 테스트 결과 보고서

## 1. 보고서 개요

| 항목 | 내용 |
|---|---|
| 테스트 일자 | 2026-08-30 |
| 테스트 대상 | 선착순 쿠폰 발급 API |
| 테스트 목적 | 재고 10,000장에 고유 사용자 20,000명이 신청하는 상황에서 초과·중복 발급 없이 모든 요청을 처리하는지 확인 |
| 부하 조건 | 최대 VU 20,000, ramp-up 60초, 사용자별 신청 1회 |
| 부하 발생기 | Windows 네이티브 k6 v2.2.0 |
| 네트워크 경로 | Windows TCP → Tailscale → 백엔드 서버 |
| 결과 | **PASS** |
| Test ID | 공유된 성공 결과에 기록되지 않아 미확인 |
| 원본 로그 | 보고서 작성 시 별도 로그 파일이 제공되지 않음 |
| Grafana 증적 | [공유 Snapshot](https://snapshots.raintank.io/dashboard/snapshot/2MIwOJ8W0waN3et76PI0OQvDKVZWVHuT) |

이 보고서는 테스트 직후 공유된 k6 콘솔 출력과 Grafana Snapshot을 기준으로 작성했다.
Test ID와 원본 로그 파일이 없는 항목은 임의로 추정하지 않았다. 이후 실행부터는
`run-coupon-ramp.ps1`이 Test ID와 전체 콘솔 로그를 자동 저장한다.

## 2. 요구사항과 판정 기준

과제의 쿠폰 발급 부하 조건은 재고 10,000장에 20,000명이 요청해도 발급 수량이 재고를
초과하지 않고, 한 사람에게 최대 한 장만 발급하는 것이다. 팀의 부하 조건은 중복 없는
20,000 VU를 60초 동안 점진적으로 증가시키는 방식으로 구체화했다.

이번 테스트의 합격 기준은 다음과 같다.

| 검증 항목 | 합격 기준 |
|---|---:|
| 총 신청 시도 | 20,000건 |
| 발급 성공 | 10,000건 |
| 품절 응답 | 10,000건 |
| 전송 실패 | 0건 |
| 예상하지 못한 응답 | 0건 |
| 최종 발급 수량 검증 | 1회 성공 |
| 신청 API p95 | 5초 미만 |
| 최대 활성 VU | 20,000 |

## 3. 테스트 구성

### 3.1 기준 파일

- 실행 진입점: `k6/ramp/run-coupon-ramp.ps1`
- Ramp 시나리오: `k6/ramp/coupon-ramp.js`
- 공통 쿠폰 처리: `k6/common/coupon-claim.js`

### 3.2 시나리오

1. 활성 상태인 `[K6] 10%` 정률 쿠폰 종류를 조회한다.
2. `SINGLE_FIRST_COME`, `MANUAL_TEST` 방식의 재고 10,000장 이벤트를 생성한다.
3. 이벤트가 `READY` 상태인지 확인하고 관리자가 회차를 한 번 수동 오픈한다.
4. VU를 60초 동안 0명에서 20,000명까지 선형적으로 증가시킨다.
5. 각 VU에 `900001`부터 서로 다른 사용자 ID를 부여하고 쿠폰을 한 번만 신청한다.
6. 신청 종료 후 관리자 이벤트 API에서 최종 발급 수량이 10,000건인지 확인한다.

각 VU는 첫 번째 iteration에서만 신청한다. 다음 iteration에서는 테스트가 종료될 때까지
대기하므로 동일 사용자의 반복 신청이 발생하지 않는다. `noConnectionReuse: true`를 적용하여
사용자별 신청 연결을 재사용하지 않는다.

### 3.3 표준 재현 명령

PowerShell 실행 정책은 현재 프로세스에만 허용한다.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force

.\k6\ramp\run-coupon-ramp.ps1 `
  -TotalVus 20000 `
  -CouponQuantity 10000 `
  -RampUpSeconds 60 `
  -TestId "coupon-native-20000-ramp60-run01"
```

결과 로그는 `k6/logs/<TestId>.log`에 저장되고, 같은 Test ID가 Prometheus
`testid` 태그에 기록된다.

## 4. 테스트 결과

### 4.1 최종 판정

| 지표 | 측정값 | 기준 | 판정 |
|---|---:|---:|---|
| `coupon_claim_attempt_total` | 20,000 | 20,000 | PASS |
| `coupon_claim_success_total` | 10,000 | 10,000 | PASS |
| `coupon_claim_sold_out_total` | 10,000 | 10,000 | PASS |
| `coupon_claim_transport_failure_total` | 0 | 0 | PASS |
| `coupon_claim_unexpected_total` | 0 | 0 | PASS |
| `coupon_final_verification_success_total` | 1 | 1 | PASS |
| Claim p95 | 951.19ms | 5초 미만 | PASS |
| 최대 VU | 20,000 | 20,000 | PASS |

모든 k6 Threshold가 통과했다.

### 4.2 응답 성능

| 항목 | 측정값 |
|---|---:|
| Claim 평균 | 336.95ms |
| Claim 중앙값 | 216.36ms |
| Claim p90 | 694.43ms |
| Claim p95 | 951.19ms |
| Claim 최대 | 2.88초 |
| 전체 HTTP 실패율 | 0.00% |
| 전체 HTTP 요청 | 20,004건 |

전체 HTTP 요청 20,004건에는 사용자 신청 20,000건 외에 이벤트 생성·오픈과 최종 검증을 위한
관리자 요청이 포함된다.

### 4.3 실행 지표

| 항목 | 측정값 |
|---|---:|
| 신청 요청 | 20,000건 |
| 전체 iteration | 29,953회 |
| 최대 활성 VU | 20,000 |
| 설정된 최대 VU | 20,000 |

`iterations=29,953`은 신청이 29,953건 발생했다는 뜻이 아니다. 신청을 끝낸 VU가 다음
iteration에서 대기한 횟수가 포함된 값이다. 실제 신청 수는
`coupon_claim_attempt_total=20,000`을 기준으로 판단한다.

## 5. Docker 실행 실패와 개선 결과

동일한 20,000 VU 조건을 Docker 컨테이너의 k6에서 실행했을 때는 6,216건의
`dial: i/o timeout`이 발생했다.

| 항목 | Docker k6 | Windows 네이티브 k6 |
|---|---:|---:|
| 신청 시도 | 20,000 | 20,000 |
| 발급 성공 | 10,000 | 10,000 |
| 품절 응답 | 3,784 | 10,000 |
| 전송 실패 | 6,216 | 0 |
| 예상하지 못한 응답 | 0 | 0 |
| Claim 평균 | 746.52ms | 336.95ms |
| Claim p95 | 1.84초 | 951.19ms |
| 최종 결과 | FAIL | PASS |

Docker 실행 경로는 다음과 같았다.

```text
k6 컨테이너
→ Docker Desktop/WSL 네트워크와 NAT
→ Windows TCP 스택
→ Tailscale
→ 백엔드 서버
```

네이티브 전환 후에는 Docker Desktop/WSL NAT 구간이 제거됐다.

```text
k6.exe
→ Windows TCP 스택
→ Tailscale
→ 백엔드 서버
```

백엔드, 사용자 수, 재고와 ramp-up 조건을 유지한 상태에서 전송 실패가 6,216건에서 0건으로
감소했다. Claim 평균은 약 55%, p95는 약 48% 감소했다. 따라서 Docker NAT를 포함한 부하
발생기 네트워크 경로가 기존 실패의 주요 병목이었다고 판단한다.

다만 이 결과가 Windows TCP와 Tailscale의 모든 연결 한계를 제거했다는 뜻은 아니다. 더 높은
VU 또는 더 짧은 ramp-up에서는 다른 네트워크 한계가 나타날 수 있다.

## 6. 결과 해석

### 6.1 충족한 조건

- VU가 최대 20,000명에 도달했다.
- 중복 없는 사용자 20,000명이 각각 한 번 신청했다.
- 성공 10,000건과 품절 10,000건으로 모든 신청이 애플리케이션 응답을 받았다.
- 전송 실패, 예상하지 못한 응답과 초과 발급이 없었다.
- 신청 API p95가 기준 5초보다 낮았다.
- 관리자 API의 최종 발급 수량이 재고와 같은 10,000건이었다.

### 6.2 동시성 표현의 범위

이 테스트는 `20,000 VU / ramp-up 60초` 조건이다. VU가 60초 동안 점진적으로 증가하여
최대 20,000명에 도달한 것이며, HTTP 요청 20,000건이 완전히 같은 시각에 시작됐거나 TCP
연결 20,000개가 계속 유지됐다는 뜻은 아니다.

발표와 문서에서는 다음과 같이 표현한다.

> 60초 ramp-up으로 중복 없는 20,000 동시 VU에 도달했고, 각 VU가 한 번씩 보낸 신청
> 20,000건을 성공 10,000건과 정상 품절 10,000건으로 처리했다.

## 7. 검증 한계와 후속 확인

이번 k6 teardown은 관리자 이벤트 API의 `issuedQuantity == 10000`을 확인한다. 다음 DB와
비동기 처리 정합성은 이번 결과만으로 확정하지 않는다.

- `user_coupon` 정확히 10,000건
- `coupon_claim_request`의 `SUCCEEDED` 정확히 10,000건
- 오래 남은 `PENDING` 요청 0건
- 동일 사용자의 동시 중복 신청 방지
- 발급 결과 Outbox와 Kafka backlog 해소
- 100만 사용자와 300만 발급 이력 전체 정합성

위 항목은 [`쿠폰 대량 데이터 정합성 검증`](../README.md)을 별도로 수행하여 확인한다.

## 8. 최종 결론

Windows 네이티브 k6 환경에서 재고 10,000장, 고유 사용자 20,000명, ramp-up 60초 조건의
부하 테스트를 수행한 결과 모든 합격 기준을 충족했다.

```text
최종 판정: PASS
발급 성공: 10,000건
정상 품절: 10,000건
전송 실패: 0건
예상하지 못한 응답: 0건
최대 VU: 20,000
Claim p95: 951.19ms
```

Docker 실행에서 발생했던 대량 TCP 전송 실패는 네이티브 k6 전환 후 재현되지 않았다.
따라서 현재 구성은 과제에서 구체화한 `20,000 VU / ramp-up 60초` 쿠폰 발급 시연에 사용할
수 있다. 전체 데이터 정합성의 최종 통과 여부는 별도 DB 검증 결과와 함께 제시해야 한다.

## 9. 관련 문서

- [쿠폰 Ramp 테스트 실행 가이드](../../../k6/ramp/README.md)
- [쿠폰 20,000 VU 부하 테스트 트러블슈팅](../../08-troubleshooting/coupon-20000-vu-load-test-troubleshooting.md)
- [쿠폰 발급 도메인 규칙](../../02-domain/coupon.md)
- [쿠폰 대량 데이터 정합성 검증](../README.md)
