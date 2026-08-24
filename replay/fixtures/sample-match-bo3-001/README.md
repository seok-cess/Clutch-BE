# 기본 실제 경기 replay fixture

이 fixture는 GEN–KT best-of-3 실제 API 호출 녹화에서 생성한 공유 기본 fixture다. `compose.yaml`의
replay 컨테이너는 `REPLAY_FIXTURE_DIR`을 지정하지 않으면 이 디렉터리를 사용한다. 승패·킬·오브젝트
흐름은 원본을 유지하되, 로컬 test 경기 시간축과 시작 골드는 아래처럼 재구성했다.

- 첫 세트 전 대기: 10분
- 세트당 진행 시간: 25분
- 세트 사이 공백: 1분
- 각 세트의 첫 프레임: 모든 선수 골드 500 (팀 골드 2,500)

## 포함된 흐름

- 1·2·3세트의 시작, 진행, livestats `finished` 프레임
- 1·2세트의 실제 공식 `gameWins` 증가와 배팅 정산
- 원본 녹화의 라이브 피드가 끊긴 뒤, 결과 조정 작업이 `eventDetails`를 다시 조회해 3세트 배팅을
  정산하는 흐름

원본 녹화는 3세트의 공식 최종 응답 전에 끝난다. 그래서 `eventDetails.jsonl`의 마지막 한 줄은
테스트용 보정 응답이다. 바로 전 실제 응답을 기준으로 120초 뒤에 3세트를 `completed`로, GEN의
`gameWins`를 2로 바꾼 것이다. 원본 응답인 것처럼 취급하지 않으며, 마지막 세트 결과 조회·정산
복구 경로를 공통으로 검증하기 위한 의도된 fixture 데이터다.

## 용량 축약 방식

실제 `window`·`details` API 응답은 최근 수십 초 프레임을 중복해 반복한다. 이 fixture는 같은 세트·
`rfc460Timestamp`의 중복 프레임을 제거하고, 남은 프레임을 초당 하나로 샘플링해 JSONL 한 줄로
분리했다. replay 서버가 재생 중 미전달 프레임을 합쳐 반환하므로, 백엔드의 프레임 적재·세트 종료·
배팅 정산 흐름은 유지된다.

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
