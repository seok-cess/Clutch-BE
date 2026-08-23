# 재생 스텁 서버

라이브 경기를 기다리지 않고 백엔드를 "진짜 라이브처럼" 테스트하기 위한 도구다.

## 한 줄 설명

백엔드는 실제로 lolesports 서버에 초 단위로 "지금 스코어 몇이야?" 라고 물어보고 답을 받아서 동작한다.
이 스텁 서버는 그 질문에 **녹화해둔 답을 대신 돌려주는 가짜 lolesports 서버**다. 백엔드 코드는 한 줄도
안 건드리고, 개인 설정 파일의 주소 두 줄만 이 서버로 돌리면, 폴링·DB 저장·배팅·시청 포인트까지
전체 파이프라인이 실제 라이브 때와 동일하게 동작한다.

## 빠르게 시작하기 (공유 실제 경기 fixture)

```bash
node replay/replay-server.js --dir replay/fixtures/sample-match-bo3-001 --speed 3 --compress-frame-time
./gradlew bootRun --args='--spring.profiles.active=replay'
```

`fixtures/sample-match-bo3-001/`는 실제 GEN–KT best-of-3 녹화에서 만든 Git 추적 기본 fixture다.
Docker Compose의 replay 컨테이너도 별도 환경 변수가 없으면 이 fixture를 사용하므로, 저장소를 받은
모든 개발자가 같은 세트 진행·정산 흐름을 재생한다. 데이터 축약 방법과 마지막 세트 결과 보정은
[fixture README](fixtures/sample-match-bo3-001/README.md)를 참고한다.

`fixtures/smoke-test/`는 손으로 만든 가짜 경기(세트 1개, bestOf 1) 픽스처다. 스텁 서버의 최소 동작만
빠르게 확인할 때 사용한다.

## 팀원에게 다른 형식의 파일을 받았다면 — convert-fixture.js

녹화/합성 파일이 이 문서의 "엔드포인트별 JSONL" 계약과 다른 모양(예: 폴링 틱 하나에 여러 API 호출이
묶인 형태)으로 올 수도 있다. `convert-fixture.js`가 그런 파일을 읽어 계약대로 변환해준다.

```bash
node replay/convert-fixture.js --in "받은파일.jsonl" [--out replay/fixtures/<matchId>]
```

입력 파일 한 줄이 `{"elapsedSecond":60,"scheduler":"...","calls":[{"request":{"path":...},"response":{"status":200,"body":"<JSON 문자열>"}}]}`
모양이면 그대로 처리된다. 첫 줄이 `{"type":"metadata","matchId":...}`면 `--out`을 안 줘도 그 matchId로
출력 폴더를 자동으로 만든다. 다른 모양의 파일을 받으면 이 스크립트를 그 형식에 맞춰 고쳐야 한다.

## 실제 API 호출 로그 변환 — convert-recorded-fixture.js

애플리케이션이 실제 LoL Esports API를 호출하며 남긴 JSONL은 한 줄에 호출 하나씩
`calledAt`, `api`, `request`, `status`, `response`를 담는다. 이 형식은 다음 스크립트로 변환한다.

```bash
node replay/convert-recorded-fixture.js \
  --in "/path/to/recorded.jsonl" \
  --match-id "115548147900619045" \
  --out ~/Desktop/clutch-replay-recordings/115548147900619045
```

변환 결과는 원본 보관·압축 입력으로만 사용한다. replay 컨테이너에 원본을 마운트하거나 직접
재생하지 않는다. 아래의 Git 공유용 압축 과정을 거쳐 `sample-match-bo3-001`을 갱신하면 된다.

Docker Compose는 `REPLAY_FIXTURE_DIR`을 별도로 지정하지 않으면 Git 추적 압축 fixture
(`sample-match-bo3-001`)를 사용한다.

- 대상 매치의 `getLive`, `getEventDetails`, `getSchedule` 응답과 그 매치에 속한
  `window`·`details` 세트 응답만 추출한다. `204`, `400`, `404` 같은 실패 응답은 재생 fixture에 넣지 않는다.
- `getLive`가 대상을 처음 반환하기 전에는 빈 라이브 응답 하나를 넣어, 재생 시작부터 경기가
  이미 진행 중으로 보이지 않게 한다.
- 실제 로그를 두 번 스트리밍 순회하므로 입력 전체를 메모리에 올리지 않는다. 출력은 원본과 비슷한
  크기가 될 수 있으므로 기본 출력 위치는 저장소 밖 `~/Desktop/clutch-replay-recordings/`다.
- replay 서버는 64MB를 넘는 JSONL에 대해 줄 위치·시각·세트 ID만 먼저 인덱싱하고, 요청된 응답
  본문만 디스크에서 읽는다. 기동 시 파일을 한 번 순회하지만 이후에는 fixture 전체 크기만큼의
  힙 메모리를 사용하지 않는다.
- 원본에 마지막 세트 또는 `gameWins` 확정 구간이 없다면 변환 결과도 해당 시점에서 멈춘다.
  세트별 정산은 확정된 구간까지만 검증할 수 있다.

### Git 공유용 실제 fixture 압축

실제 변환 fixture의 `window`·`details` 응답에는 최근 수십 초의 프레임이 매번 중복되어 들어간다.
`compact-recorded-fixture.js`는 같은 세트·시각 프레임을 한 번만 남기고, 남은 프레임을 기본 초당
하나로 샘플링해 JSONL 한 줄로 기록한다. replay 서버는 재생 시 전달하지 않은 JSONL 프레임을 다시
합치므로 백엔드는 기존과 같은 순서로 프레임을 받는다.

```bash
node replay/compact-recorded-fixture.js \
  --in ~/Desktop/clutch-replay-recordings/115548147900619045 \
  --out replay/fixtures/sample-match-bo3-001 \
  --replace \
  --frame-interval-seconds 1 \
  --final-winner-team-id 100205573495116443 \
  --final-result-delay-seconds 120
```

- `--replace`는 대상 fixture 디렉터리만 교체한다. 대상의 `README.md`가 있으면 유지하며, 원본 fixture는
  수정하지 않는다.
- `--frame-interval-seconds`는 `window`·`details`의 프레임 간격이다. 기본값은 1초이며, 더 큰
  fixture가 필요하면 값을 줄이고 GitHub 단일 파일 100MB 제한을 넘지 않는지 확인한다.
- `--final-winner-team-id`는 원본 녹화가 마지막 세트 공식 응답 전에 끝난 경우에만 사용한다. 마지막
  `eventDetails` 응답을 복제해 미완료 세트를 `completed`로 바꾸고 해당 팀의 `gameWins`를 하나 올린
  **테스트용 보정 응답**을 추가한다.
- 이 작업은 대용량 원본을 스트리밍 처리하므로, 원본 전체를 메모리에 올리지 않는다.

## 백엔드와 연결하기

개인용 `src/main/resources/application.yaml`(Git에 안 올라감)에서 아래 두 값만 스텁 서버 주소로 바꾼다.

```yaml
lolesports:
  esports-api-base-url: http://localhost:4000
  live-stats-base-url: http://localhost:4000
```

`./gradlew bootRun`으로 백엔드를 켜면 끝이다. 확인 끝나면 두 값을 원래 실제 주소
(`https://esports-api.lolesports.com/persisted/gw`, `https://feed.lolesports.com/livestats/v1`)로 되돌린다.

`replay` profile을 쓰면 주소를 직접 바꾸지 않아도 된다. 스텁 서버를 먼저 실행한 뒤 아래처럼
백엔드를 켠다.

```bash
./gradlew bootRun --args='--spring.profiles.active=replay'
```

이 profile에서만 `POST /api/replay/start`와 `GET /api/replay/status`가 열린다. 프론트는 상태 API가
정상 응답할 때만 테스트 시작 버튼을 표시하며, 별도 `VITE_REPLAY_MODE` 환경 변수는 필요 없다. 버튼은
시작 API를 호출하면
된다. 요청할 때마다 스텁 서버가 새 `matchId`/`gameId`를 만들고 타임라인을 처음부터 재생한 뒤,
백엔드도 즉시 한 번 라이브 폴링을 수행하므로,
기존 테스트 경기 데이터와 충돌하지 않는다. 스텁 서버 프로세스 자체는 이 API가 실행하지 않으므로
먼저 `node replay/replay-server.js ...`로 켜져 있어야 한다.

`GET /api/replay/status`는 JSONL 전체의 첫·마지막 `capturedAt`을 기준으로 현재 재생 위치를 반환한다.
예를 들어 `elapsedSeconds: 1350`, `totalSeconds: 8400`이면 fixture 140분 중 22분 30초 지점이다.

재생 중 배속을 바꾸려면 `POST /api/replay/speed?value=5`처럼 1~20배속을 요청한다. 타임라인은
변경 순간의 JSONL 시각을 유지하므로 배속 변경으로 재생 위치가 점프하지 않는다.

replay profile은 `getLive`도 실제 1초마다 확인한다. 따라서 20배속에서도 세트 시작·종료 상태를
기본 운영 주기(60초) 때문에 건너뛰지 않는다. 화면은 리플레이 중에는 가장 최신 프레임을 표시하고,
세트 종료 시에는 캐시에 받은 전체 프레임을 DB 타임라인·오브젝트 데이터로 적재한다.

## 녹화 계약 — 다른 팀원이 만드는 녹화 기능이 지켜야 할 형식

매치 하나당 디렉터리를 하나 만든다: `replay/fixtures/<matchId>/`. 안에는 백엔드가 실제로 폴링하는
엔드포인트별로 JSONL 파일(한 줄 = 응답 하나)을 둔다.

| 파일 | 필수 | 내용 |
|---|---|---|
| `getLive.jsonl` | 필수 | 매치 상태·팀 gameWins (60초 간격 폴링) |
| `eventDetails.jsonl` | 필수 | 세트 목록·상태·bestOf |
| `window.jsonl` | 필수 | 초단위 팀/선수 스탯 (1초 간격 폴링, 세트별로 `gameId` 필요) |
| `details.jsonl` | 필수 | 초단위 선수 상세(딜지분·아이템 등) |
| `getSchedule.jsonl` | 선택 | 없으면 스텁이 빈 일정으로 응답 |
| `getStandings.jsonl` | 선택 | 없으면 스텁이 빈 순위로 응답 |

한 줄의 형식:

```json
{"capturedAt": "2026-08-19T17:03:21Z", "gameId": "<window/details만>", "body": <응답 원본, 가공 없이 그대로>}
```

- `capturedAt`: 우리 백엔드가 그 응답을 **받은** 시각 (ISO-8601). 재생 시 이 값 기준으로 순서/속도를 계산한다.
- `body`: lolesports 서버가 실제로 준 JSON 응답을 가공 없이 그대로 담는다. 우리 쪽 DTO로 변환하지 않는다 —
  그래야 재생할 때 백엔드가 실제 응답을 파싱하는 것과 완전히 동일하게 동작한다.
- `window.jsonl`/`details.jsonl`은 한 매치에 세트가 여러 개일 수 있어 줄마다 `gameId`를 같이 적는다.

### window/details 프레임 시각은 "그 게임의 첫 프레임 = 재생 시작 시각"으로 맞춘다

프레임 시각(`rfc460Timestamp`)은 게임 내 경과 시간 계산에도 쓰여서 프레임끼리의 간격을 배속으로
나눌 수 없다(나누면 게임 시계가 깨진다). 그래서 스텁 서버는 **게임(세트)별로** 그 게임의 가장 이른
프레임이 재생 시작 시각에 오도록 통째로 평행이동한다 — 몇 세트든 캐시에 들어오는 순간 바로 화면
표시 지연(45초 전 프레임 조회) 조건이 성립한다.

**과거 버그(고침)**: 이걸 게임별이 아니라 픽스처 전체 기준 하나로 잡았더니, 경기 시작 20분 대기처럼
사전 대기 구간이 있는 픽스처에서는 그 대기시간만큼 화면이 첫 프레임에 고정돼 있었다(배속 무관). 지금은
게임별로 따로 맞춰서 이 문제가 없다.

`--compress-frame-time`을 지정하면 화면용 프레임 시각도 재생 배속에 맞춰 압축한다. Compose의
replay는 이 옵션을 기본으로 사용한다. 대신 화면의 게임 경과 시간도 압축된다. 실제 게임 시간 표시를
우선하는 수동 실행에서는 이 옵션을 생략하고 `--speed 1`을 사용한다.

배팅의 다음 세트 마감은 화면용 프레임 시각을 쓰지 않는다. `LiveStatsClient`가 게임 시작 시각을
찾는 전용 요청에는 스텁이 픽스처 전체의 압축된 재생 시간축을 돌려준다. 따라서 다음 세트는 이전
세트 종료 뒤 열리고, 실제 게임 시작을 감지하면 1분 뒤에 닫히며, 게임별 화면 프레임을 재생 시작에
맞춰 평행이동해도 이 마감 근거가 섞이지 않는다.

### 배팅 첫 세트 창(경기 시작 전 오픈)도 재생된다

`getLive`/`getSchedule` 의 매치 `startTime` 은 배팅 첫 세트 오픈(공식 시작 20분 전)·마감(시작 1분 후)과
`PollingScheduler` 의 배팅 후보 편입(시작 30분 전)에서 실제 지금 시각과 직접 비교된다. 녹화 당시의
고정된 과거 시각을 그대로 돌려주면 이 비교가 항상 어긋나므로, 스텁 서버가 `getLive`/`getSchedule`
응답의 `startTime` 만 "재생 시작 시각 기준"으로 자동으로 다시 계산해서 돌려준다(배속 반영). 녹화
파일 자체는 원본 그대로 두면 되고, 다른 손질은 필요 없다. (window/details 의 프레임 시각은 대상이
아니다 — 그건 서로 상대적으로만 쓰인다.)

### 꼭 지켜야 하는 것 — 경기가 "끝난 뒤"에도 몇 분 더 녹화 계속하기

`getLive`의 `gameWins`와 `eventDetails`의 세트 `state`는 실제 세트 종료보다 **약 5분 늦게** 갱신된다
(라이브 소스 자체의 특성 — `docs/domain/match-set-result.md` 참고). 이 두 값이 실제로 바뀌는 순간까지
녹화를 안 멈추면, 재생했을 때 매치가 "진행 중" 상태에서 영원히 안 끝난다. 화면상 경기가 끝난 것처럼
보여도 최소 5~10분은 `getLive`/`eventDetails` 폴링을 계속 녹화해 달라.

## 이 도구가 일부러 안 하는 것

- `startingTime` 쿼리 파라미터의 실제 10초 윈도우 규칙을 그대로 재현하지는 않는다. 대신 `window`와
  `details`는 endpoint·game별 마지막 전달 위치를 기억했다가, 다음 폴링 때 그 뒤부터 현재 재생 시점까지의
  녹화 프레임을 한 응답으로 합쳐 돌려준다. 따라서 20배속처럼 한 번의 백엔드 폴링 사이에 여러 초가 지나도
  그 구간의 프레임은 누락되지 않고 백엔드 캐시·DB 적재 대상으로 들어간다. 프레임 중복 제거는 백엔드가
  `rfc460Timestamp` 기준으로 수행한다.
- 재생 제어 UI는 replay profile의 프론트 라이브 화면에서만 제공한다. mock 서버 프로세스 자체를
  실행·종료하는 기능은 제공하지 않는다.
- 한 번에 매치 하나만 재생한다. 여러 매치를 동시에 재생하려면 서버를 여러 포트로 여러 개 띄우면 된다.
