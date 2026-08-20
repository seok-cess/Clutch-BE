#!/usr/bin/env node
'use strict';

/**
 * "폴링 틱" 단위로 묶인 녹화/합성 파일을 replay-server.js 가 읽는
 * 엔드포인트별 JSONL(README.md 의 녹화 계약)로 변환한다.
 *
 * 입력 한 줄 모양(예시):
 *   {"elapsedSecond":60,"scheduler":"pollLiveMatches","calls":[
 *     {"request":{"method":"GET","path":"/persisted/gw/getLive","query":{...}},
 *      "response":{"status":200,"body":"<JSON 문자열>"}},
 *     ...
 *   ]}
 * 첫 줄은 {"type":"metadata", "matchId":..., ...} 일 수 있다 — matchId 추출에만 쓰고 건너뛴다.
 *
 * 사용법:
 *   node convert-fixture.js --in <입력 파일> [--out replay/fixtures/<matchId>]
 */

const fs = require('fs');
const path = require('path');

function parseArgs(argv) {
  const args = { in: null, out: null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--in') args.in = argv[++i];
    else if (a === '--out') args.out = argv[++i];
  }
  if (!args.in) {
    console.error('사용법: node convert-fixture.js --in <입력 파일> [--out replay/fixtures/<matchId>]');
    process.exit(1);
  }
  return args;
}

/** elapsedSecond 를 정렬 가능한 ISO 시각으로. 절대값 자체는 의미 없고 간격만 유지하면 된다. */
const EPOCH = Date.parse('2026-01-01T00:00:00Z');
function isoOf(elapsedSecond) {
  return new Date(EPOCH + elapsedSecond * 1000).toISOString();
}

/** request.path 를 보고 어느 파일로 갈지, gameId 가 있으면 뭔지 정한다. */
function routeOf(reqPath) {
  if (reqPath === '/persisted/gw/getLive') return { file: 'getLive' };
  if (reqPath === '/persisted/gw/getEventDetails') return { file: 'eventDetails' };
  if (reqPath === '/persisted/gw/getSchedule') return { file: 'getSchedule' };
  if (reqPath === '/persisted/gw/getStandings') return { file: 'getStandings' };
  let m = reqPath.match(/\/livestats\/v1\/window\/([^/?]+)$/);
  if (m) return { file: 'window', gameId: m[1] };
  m = reqPath.match(/\/livestats\/v1\/details\/([^/?]+)$/);
  if (m) return { file: 'details', gameId: m[1] };
  return null;
}

/**
 * 팀 totalKills 와 선수별 kills 합이 안 맞는 원본 데이터를 보정한다.
 *
 * 원본 생성기가 팀 킬은 한 공식으로, 선수별 킬은 선수별 지분(killShare)에 소수점
 * 버림을 적용해서 따로 계산해 — 지분을 다 더해도 팀 총합보다 적게 나온다(특히
 * 초반). 화면의 K/D/A 합과 팀 킬 스코어가 서로 다르게 보이는 원인이다.
 *
 * targetTotal(팀 totalKills, 신뢰값)에 맞춰 부족분을 채워 넣는다. 킬은 절대
 * 줄이지 않는다(단조 증가 유지). 원본 개인 킬이 실제로 더 많은 선수를 우선하고,
 * 그마저 다 0이라 구분이 안 되면(원본 데이터가 자주 이렇다) 지금까지 보정으로
 * 덜 받은 선수부터 채운다 — 그래야 매번 같은 선수(가장 낮은 id)한테만 몰리지 않는다.
 */
function redistributeKills(rawKillsByParticipant, targetTotal, corrected) {
  const ids = [...rawKillsByParticipant.keys()];
  let currentSum = 0;
  for (const id of ids) currentSum += corrected.get(id) || 0;
  let deficit = targetTotal - currentSum;
  if (deficit <= 0) return; // 팀 총합이 줄어드는 이상 케이스는 그대로 둔다(단조성 우선)

  const order = [...ids].sort((a, b) =>
    (rawKillsByParticipant.get(b) - rawKillsByParticipant.get(a))
    || ((corrected.get(a) || 0) - (corrected.get(b) || 0))
    || (a - b));
  let i = 0;
  while (deficit > 0) {
    const id = order[i % order.length];
    corrected.set(id, (corrected.get(id) || 0) + 1);
    deficit--;
    i++;
  }
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const lines = fs.readFileSync(args.in, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean);
  if (lines.length === 0) {
    console.error('입력 파일이 비어 있다.');
    process.exit(1);
  }

  let matchId = null;
  let startIndex = 0;
  let officialStartElapsedSecond = 0;
  const first = JSON.parse(lines[0]);
  if (first.type === 'metadata') {
    matchId = first.matchId || null;
    startIndex = 1;
    if (Array.isArray(first.sets) && first.sets[0] && typeof first.sets[0].start === 'number') {
      officialStartElapsedSecond = first.sets[0].start;
    }
    console.log(`[convert] 메타데이터 발견 — matchId=${matchId}, synthetic=${first.synthetic === true}`);
    if (first.description) console.log(`[convert]   ${first.description}`);
    console.log(`[convert]   공식 경기 시작 = elapsedSecond ${officialStartElapsedSecond} (1세트 시작 시각)`);
  }

  const outDir = args.out || (matchId ? path.join('replay', 'fixtures', matchId) : null);
  if (!outDir) {
    console.error('출력 디렉터리를 정할 수 없다 — --out 을 지정하거나 입력 파일에 metadata.matchId 가 있어야 한다.');
    process.exit(1);
  }
  fs.mkdirSync(outDir, { recursive: true });

  // ---- 1단계: 원본을 그대로 파싱해서 종류별로 모은다 (킬 보정은 아직 안 함) ----
  const parsed = { getLive: [], eventDetails: [], getSchedule: [], getStandings: [], window: [], details: [] };
  let skipped = 0;

  for (let i = startIndex; i < lines.length; i++) {
    const tick = JSON.parse(lines[i]);
    const elapsedSecond = tick.elapsedSecond;
    if (!Array.isArray(tick.calls)) continue;

    for (const call of tick.calls) {
      const route = call?.request?.path ? routeOf(call.request.path) : null;
      if (!route) {
        skipped++;
        continue;
      }
      if (call.response?.status !== 200 || typeof call.response.body !== 'string') {
        skipped++;
        continue;
      }
      let body;
      try {
        body = JSON.parse(call.response.body);
      } catch {
        skipped++;
        continue;
      }
      // 합성 데이터의 startTime 은 이 변환기가 새로 만든 capturedAt 시간축과 기준이 다른
      // 임의의 날짜라서, replay-server.js 의 시프트 계산이 어긋난다. 여기서 미리
      // capturedAt 과 같은 기준(elapsedSecond)으로 맞춰 써 둔다.
      if (route.file === 'getLive' || route.file === 'getSchedule') {
        const events = body?.data?.schedule?.events;
        if (Array.isArray(events)) {
          for (const event of events) {
            if (typeof event.startTime === 'string') {
              event.startTime = isoOf(officialStartElapsedSecond);
            }
          }
        }
      }
      parsed[route.file].push({
        capturedAtMs: EPOCH + elapsedSecond * 1000,
        capturedAt: isoOf(elapsedSecond),
        gameId: route.gameId,
        body,
      });
    }
  }

  // 시간순 보장 (원본이 이미 순서대로지만, 보정이 순서에 의존하므로 명시적으로 정렬)
  parsed.window.sort((a, b) => a.capturedAtMs - b.capturedAtMs);
  parsed.details.sort((a, b) => a.capturedAtMs - b.capturedAtMs);

  // ---- 2단계: window 를 순서대로 훑으며 킬 보정하고, gameId 별 스냅샷 타임라인을 만든다 ----
  const killState = new Map(); // gameId -> Map<participantId, 누적 보정 킬>
  const snapshots = new Map(); // gameId -> [{capturedAtMs, corrected: Map}]

  for (const entry of parsed.window) {
    const gameId = entry.gameId;
    if (!gameId || !Array.isArray(entry.body.frames)) continue;
    const state = killState.get(gameId) || new Map();
    killState.set(gameId, state);

    for (const frame of entry.body.frames) {
      for (const teamKey of ['blueTeam', 'redTeam']) {
        const team = frame[teamKey];
        if (!team || !Array.isArray(team.participants)) continue;
        const raw = new Map(team.participants.map((p) => [p.participantId, p.kills || 0]));
        redistributeKills(raw, team.totalKills || 0, state);
        for (const p of team.participants) p.kills = state.get(p.participantId) || 0;
      }
    }
    const list = snapshots.get(gameId) || [];
    snapshots.set(gameId, list);
    list.push({ capturedAtMs: entry.capturedAtMs, corrected: new Map(state) });
  }

  // ---- 3단계: details 는 자신과 같은 gameId 의 "그 시점 이전 중 가장 늦은" window 스냅샷을 따른다 ----
  for (const entry of parsed.details) {
    const list = snapshots.get(entry.gameId);
    if (!list || !Array.isArray(entry.body.frames)) continue;
    let picked = null;
    for (const snap of list) {
      if (snap.capturedAtMs <= entry.capturedAtMs) picked = snap;
      else break;
    }
    if (!picked) continue;
    for (const frame of entry.body.frames) {
      if (!Array.isArray(frame.participants)) continue;
      // has() 로 조건부 덮어쓰기하면 아직 배정 안 된(0인) 선수는 원본 값이 남아
      // window 와 details 가 어긋난다 — window 와 똑같이 항상 덮어써야 한다.
      for (const p of frame.participants) {
        p.kills = picked.corrected.get(p.participantId) || 0;
      }
    }
  }

  // ---- 4단계: 파일로 쓴다 ----
  let written = 0;
  for (const [name, entries] of Object.entries(parsed)) {
    if (entries.length === 0) continue;
    const lines2 = entries.map((e) => {
      const record = { capturedAt: e.capturedAt };
      if (e.gameId) record.gameId = e.gameId;
      record.body = e.body;
      return JSON.stringify(record);
    });
    fs.writeFileSync(path.join(outDir, `${name}.jsonl`), lines2.join('\n') + '\n', 'utf8');
    console.log(`[convert] ${name}.jsonl — ${entries.length}줄`);
    written += entries.length;
  }

  console.log(`[convert] 완료 — 총 ${written}줄 기록, ${skipped}줄 건너뜀 (인식 못한 경로/실패 응답)`);
  console.log(`[convert] 출력 위치: ${outDir}`);
}

main();
