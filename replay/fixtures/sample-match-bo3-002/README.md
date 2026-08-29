# 두 번째 경기 replay fixture

이 fixture는 `sample-match-bo3-001`의 경기 흐름을 바탕으로 팀·선수 표기를 T1–HLE로 바꾼 두 번째
테스트 경기다. `sample-two-live-matches/manifest.json`에서 공통 replay 시계 기준 10분 지연해 시작한다.
승패·킬·오브젝트 흐름은 원본을 유지하되, 화면에서 두 경기를 구분하고 자동 전환을 검증할 수 있다.

- 첫 세트 전 대기: 10분
- 세트당 진행 시간: 25분
- 세트 사이 공백: 1분
- 각 세트의 첫 프레임: 모든 선수 골드 500 (팀 골드 2,500)

`reshape-fixture.js`는 paused/loading 프레임이 아니라 각 세트의 첫 `in_game` 프레임을
골드 기준점으로 사용한다. 따라서 세트 시작 보정이 다음 프레임에 중복 적용되지 않는다.

## 포함된 흐름

- 1·2·3세트의 시작, 진행, livestats `finished` 프레임
- 원본 녹화에 포함된 1·2세트의 공식 `gameWins` 증가와 최종 2:1 결과
- 원본 라이브 피드의 최종 공식 응답이 늦게 도착하는 기록

원본 녹화는 3세트의 공식 최종 응답 전에 끝난다. 그래서 `eventDetails.jsonl`의 마지막 한 줄은
테스트용 보정 응답이다. 바로 전 실제 응답을 기준으로 120초 뒤에 3세트를 `completed`로, T1의
`gameWins`를 2로 바꾼 것이다. 이 지연된 기록은 유지하지만 replay 서버는 각 세트의 `finished`
window 시점에 해당 세트의 공식 승수를 적용한다. 따라서 화면과 배팅 정산은 3세트 종료 즉시 2:1로
확정된다.

## 용량 축약 방식

실제 `window`·`details` API 응답은 최근 수십 초 프레임을 중복해 반복한다. 이 fixture는 같은 세트·
`rfc460Timestamp`의 중복 프레임을 제거하고, 남은 프레임을 초당 하나로 샘플링해 JSONL 한 줄로
분리했다. replay 서버가 재생 중 미전달 프레임을 합쳐 반환하므로, 백엔드의 프레임 적재·세트 종료·
배팅 정산 흐름은 유지된다.

원본 관측에 긴 공백이 있는 경우 replay 서버는 그 공백에서 골드와 CS만 다음 실제 프레임까지
선형 보간한다. 킬·사망·오브젝트·승패는 원본 관측 시점보다 일찍 만들지 않으며, 세트 종료 시에는
해당 세트의 공식 `gameWins`를 즉시 반영해 정산과 매치 스코어가 지연되지 않게 한다.

원본 변환 fixture는 저장소 밖 `~/Desktop/clutch-replay-recordings/`에 두고, 재생 fixture를 다시
만들 때는 다음 명령을 사용한다.

```bash
node replay/compact-recorded-fixture.js \
  --in ~/Desktop/clutch-replay-recordings/115548147900619045 \
  --out replay/fixtures/sample-match-bo3-001 \
  --replace \
  --frame-interval-seconds 1 \
  --final-winner-team-id 100205573495116443 \
  --final-result-delay-seconds 120
```

압축을 다시 만들었다면 이어서 시간축·골드 규칙을 적용한다.

```bash
node replay/reshape-fixture.js \
  --dir replay/fixtures/sample-match-bo3-001 \
  --replace \
  --initial-wait-minutes 10 \
  --set-duration-minutes 25 \
  --between-set-minutes 1
```
