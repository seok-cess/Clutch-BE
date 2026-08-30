# 쿠폰 20,000 VU 부하 테스트 트러블슈팅

## 목표 조건

- 재고 10,000장
- 중복 없는 가상 사용자 20,000명
- 사용자별 신청 1회
- 60초 ramp-up으로 최대 20,000 VU 도달
- 성공 10,000건, 품절 10,000건
- 전송 실패, 예상하지 못한 응답과 초과 발급 0건

기준 실행 방법은 [`k6/ramp/README.md`](../../k6/ramp/README.md)를 따른다.

## 증상

Docker 컨테이너에서 k6를 실행한 20,000 VU 테스트는 애플리케이션 발급 수량 10,000건은
완료했지만, 나머지 요청 중 6,216건이 서버 응답을 받지 못했다.

```text
coupon_claim_attempt_total           20,000
coupon_claim_success_total           10,000
coupon_claim_sold_out_total           3,784
coupon_claim_transport_failure_total  6,216
http_req_failed                       31.07%
claim p95                              1.84s
```

실패 요청은 애플리케이션의 품절 응답이나 로그 전송 실패가 아니라 `dial: i/o timeout`이
발생한 HTTP 전송 실패였다. 따라서 `10,000 성공 + 3,784 품절`만으로는 20,000건을 모두
처리했다고 볼 수 없었다.

낮은 VU에서는 통과하거나 실패 수가 줄어들었지만, 이는 쿠폰 로직이 달라진 것이 아니라
부하 발생기 네트워크 경로가 감당하는 동시 연결 규모 아래에 머문 결과로 해석했다.

## 원인 분석

Docker 실행 시 요청은 다음 경로를 통과했다.

```text
k6 컨테이너
→ Docker Desktop/WSL 네트워크와 NAT
→ Windows TCP 스택
→ Tailscale
→ 백엔드 서버
```

시나리오는 사용자마다 연결을 새로 만들도록 `noConnectionReuse: true`를 사용한다. 20,000 VU가
60초 동안 증가하면서 짧은 시간에 새 TCP 연결이 누적됐고, Docker NAT와 Windows 동적 포트
사용이 겹친 구간에서 연결 생성이 지연됐다.

다음 관측값이 백엔드 쿠폰 발급 로직보다 부하 발생기 네트워크 경로를 주요 병목으로 판단한
근거다.

- 성공 수량은 재고와 같은 10,000건으로 끝났다.
- 예상하지 못한 애플리케이션 응답은 0건이었다.
- 누락된 품절 수량과 전송 실패 수량이 정확히 일치했다.
- 같은 조건에서 Docker만 제거하자 전송 실패가 0건이 됐다.

이 결과는 Docker NAT가 주요 병목이었다는 강한 근거지만, Windows TCP와 Tailscale의 한계까지
없어졌다는 뜻은 아니다. 더 높은 부하나 더 짧은 ramp-up에서는 다시 네트워크 한계가 나타날
수 있다.

## 해결

백엔드 인프라는 그대로 두고 부하 발생기의 k6만 Windows에 직접 설치했다. 실행 진입점인
`run-coupon-ramp.ps1`도 Docker Compose 대신 네이티브 `k6.exe`를 호출하도록 변경했다.

```text
k6.exe
→ Windows TCP 스택
→ Tailscale
→ 백엔드 서버
```

이 변경으로 Docker Desktop/WSL NAT 구간을 제거했다. 비교를 위해 백엔드 주소, 재고, 사용자
수와 ramp-up은 동일하게 유지했다.

## 재검증 결과

2026-08-30 Windows 네이티브 k6로 같은 조건을 실행한 결과다.

```text
coupon_claim_attempt_total           20,000
coupon_claim_success_total           10,000
coupon_claim_sold_out_total          10,000
coupon_claim_transport_failure_total      0
coupon_claim_unexpected_total             0
coupon_final_verification_success_total   1
claim average                         336.95ms
claim p95                             951.19ms
claim max                               2.88s
vus max                                20,000
```

모든 Threshold가 통과했고 최종 관리자 API의 발급 수량도 10,000건이었다. 이 결과는 60초 동안
VU를 20,000명까지 증가시키고 각 VU가 한 번씩 보낸 20,000건의 신청이 모두 애플리케이션
응답으로 종료됐음을 의미한다.

단, 20,000개의 HTTP 요청이 완전히 같은 순간에 실행됐거나 TCP 연결 20,000개가 계속
유지됐다는 뜻은 아니다. `ramping-vus` 조건에서 활성 VU가 최대 20,000명에 도달한 결과다.

## Chocolatey 설치 권한 오류

### 증상

일반 PowerShell에서 `choco install k6 -y`를 실행하면 다음 오류가 발생할 수 있다.

```text
Unable to obtain lock file access
Access to C:\ProgramData\chocolatey\lib-bad is denied
```

### 해결

관리자 권한으로 PowerShell을 열고 설치한다.

```powershell
choco install k6 -y
k6 version
```

이전 설치가 비정상 종료되어 잠금 파일이 남았다고 표시되면 먼저 실행 중인 Chocolatey
프로세스가 없는지 확인한다.

```powershell
Get-Process choco* -ErrorAction SilentlyContinue
```

프로세스가 없을 때만 오류 메시지에 표시된 정확한 잠금 파일을 관리자 PowerShell에서
제거한 뒤 다시 설치한다. `C:\ProgramData\chocolatey\lib` 전체를 삭제하지 않는다.

## PowerShell 실행 정책 오류

### 증상

`run-coupon-ramp.ps1` 실행 시 다음 오류가 발생한다.

```text
PSSecurityException
이 시스템에서 스크립트를 실행할 수 없으므로 파일을 로드할 수 없습니다.
```

이 오류가 발생한 실행은 k6가 시작되기 전 차단된 것이므로 부하 테스트를 수행한 것으로
간주하지 않는다.

### 해결

현재 PowerShell 프로세스에만 실행을 허용한 뒤 다시 실행한다.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\k6\ramp\run-coupon-ramp.ps1 -TotalVus 20000 -CouponQuantity 10000 -RampUpSeconds 60
```

PowerShell 창을 닫으면 실행 정책은 원래 상태로 돌아간다. 이 테스트를 위해
`LocalMachine` 실행 정책을 변경할 필요는 없다.

## 지표 해석 오류

### `transport_failure`는 로그 전송 실패가 아니다

`coupon_claim_transport_failure_total`은 신청 HTTP 요청이 상태 코드를 받지 못한 경우다.
Prometheus 또는 로그 전송 실패를 의미하지 않는다. 이 값이 1건이라도 있으면 모든 사용자의
신청 결과를 확인한 테스트가 아니다.

### `iterations`는 신청 건수가 아니다

요청을 끝낸 VU는 반복 신청을 막기 위해 다음 iteration에서 대기한다. 따라서 전체
`iterations`는 20,000보다 커질 수 있다. 신청 건수는 반드시
`coupon_claim_attempt_total`로 판정한다.

### `vus max=20000`은 같은 순간의 요청 20,000건이 아니다

60초 ramp-up은 VU를 점진적으로 증가시킨다. `vus max=20000`은 활성 VU가 최대 20,000명에
도달했다는 뜻이고, 20,000개의 요청이 한 시각에 시작됐다는 뜻은 아니다. 과제 시연에서는
`20,000 동시 VU에 도달`이라고 표현한다.

## 로그와 Test ID

직접 `k6 run`을 실행하면 콘솔 출력은 자동으로 파일에 남지 않는다. 공식 실행 스크립트는
Test ID를 생성하고 모든 출력을 다음 위치에 기록한다.

```text
k6/logs/<TestId>.log
```

Test ID는 Prometheus의 `testid` 태그에도 기록된다. 동일 조건을 비교할 때도 실행마다 다른
Test ID를 사용한다.

## 남은 검증 범위

Ramp 테스트의 최종 검증은 관리자 API의 `issuedQuantity == 10000`까지다. 다음 항목은 이
결과만으로 확정하지 않는다.

- `user_coupon` 정확히 10,000건
- `coupon_claim_request`의 `SUCCEEDED` 정확히 10,000건
- 오래 남은 `PENDING` 0건
- 동일 사용자의 동시 중복 신청 방지
- Outbox와 Kafka backlog 해소
- 100만 사용자와 300만 발급 이력 전체 정합성

DB와 대량 데이터 정합성은 [`쿠폰 대량 데이터 정합성 검증`](../07-verification/README.md)을
별도로 실행한다.
