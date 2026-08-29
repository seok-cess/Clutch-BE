# 두 경기 동시 replay fixture

첫 번째 경기는 `sample-match-bo3-001`의 GEN–KT 녹화본을 즉시 재생한다. 두 번째 경기는
`sample-match-bo3-002`의 T1–HLE 테스트 fixture를 공통 replay 시계 기준 10분 뒤에 시작한다.
따라서 첫 경기가 끝난 뒤에도 두 번째 경기는 약 10분 동안 계속 진행된다. replay 서버는 실행할 때
각 fixture에 서로 다른 외부 경기·세트 ID를 부여하므로, 백엔드는 두 개의 독립된 라이브 경기로 처리한다.

두 번째 fixture는 UI·선택 전환을 검증하기 위한 복제본이며, 팀·선수 표기만 T1–HLE로 바꿨다.
나중에 실제 녹화본을 추가하면 `manifest.json`의 각 `fixture` 경로만 새 fixture 디렉터리로 교체한다.
`offsetSeconds`를 지정하면 해당 경기의 시작을 공통 replay 시계에서 지연시킬 수 있다.
