# ADR-001: 운영자 제어형 외부 데이터 소스 전환

- Status: Accepted
- Date: 2026-08-20
- Decision Makers: CLUTCH 팀

## Context

실제 LoL Esports API에 라이브 경기가 없을 때도 운영자가 실제 서비스 서버에서
녹화 fixture 기반의 test 경기를 재생해야 한다. 기존 WebClient는 애플리케이션 기동 시
한 base URL로 고정되어 있어, 서버 재시작 없이 실제 API와 replay 스텁 서버를 전환할 수 없다.

test 경기도 실제 사용자 배팅, 포인트 지급, 정산과 데이터 적재를 포함한 기존 흐름을 그대로
사용한다. fixture가 끝났다고 자동으로 다음 경기를 시작할 필요는 없으며, 운영자가 필요할 때
새 test 경기를 시작한다.

## Decision

- 서버는 `REAL` 모드로 시작한다.
- 기본 Spring profile은 `operator-routing`이다. Compose는 `http://replay:4000`을 사용한다.
  Docker 밖에서 Spring을 실행할 때만 `REPLAY_SERVER_URL=http://localhost:4000`을 지정한다.
- `PUT /api/operator/external-source`가 전역 소스를 `REAL` 또는 `STUB`으로 전환한다.
- 실제 API와 스텁 API용 WebClient를 각각 만들고, 호출 시점의 모드로 선택한다.
- STUB 전환 전에는 replay 서버의 상태를 확인한다. 자동 전환이나 실제 API 장애 시 STUB 대체는 하지 않는다.
- 전환은 폴링 작업과 읽기/쓰기 잠금으로 직렬화하고, 이전 소스의 인메모리 캐시·폴링 상태를 비운다.
- STUB 전환만으로 fixture를 재시작하지 않는다. `POST /api/replay/start`는 STUB 모드에서만 새 run을 시작한다.
- replay 서버는 Compose 서비스 `replay`로 상시 실행하며, 앱 컨테이너는 `http://replay:4000`으로 호출한다.
- STUB으로 생성한 경기·배팅·포인트 데이터는 실제 서버 DB에 적재하고 자동으로 삭제하지 않는다.
- 운영자 API 인증은 이번 결정의 범위에서 구현하지 않는다. 운영자 화면은 전환 경고와 재확인을 제공한다.

## Alternatives Considered

- 실경기 감시자가 일정에 따라 자동 전환: 운영자의 명시적 제어 요구와 맞지 않고, 실제 API 장애를
  fixture로 가릴 위험이 있어 선택하지 않았다.
- fixture 완료 시 자동 반복 재생: 새 test 경기 시작 버튼으로 필요한 시점에 명확히 재생할 수 있어
  선택하지 않았다.
- 서버 재시작 또는 Spring profile 변경으로 전환: 전환 중 서비스 중단이 필요하고 운영 절차가 길어져
  선택하지 않았다.
- STUB 데이터를 별도 DB에 격리: test 경기도 기존 사용자 배팅·포인트 흐름을 사용하기로 했으므로
  선택하지 않았다.

## Consequences

- STUB 전환은 서버의 모든 사용자가 보는 라이브 데이터에 적용된다.
- fixture 데이터와 이에 따른 배팅·포인트 거래는 실제 서비스 DB에 남는다.
- 인증이 없는 운영자 API는 네트워크 노출 범위를 운영 환경에서 신중하게 관리해야 한다.
- replay 컨테이너가 중단되면 STUB 전환은 실패하지만 REAL 모드의 서비스에는 영향이 없다.

## Follow-up

- 운영자 화면에서 현재 모드 표시, 경고 문구, 재확인 절차를 제공한다.
- 필요 시 운영자 API 인증·권한 검증을 추가한다.
- 모드 전환과 재생 시작 이벤트를 운영 로그 또는 모니터링으로 수집한다.
