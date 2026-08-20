#!/usr/bin/env node
'use strict';

/**
 * lolesports 외부 API(esports-api.lolesports.com, feed.lolesports.com)를 흉내내는
 * 재생 스텁 서버. 녹화된 JSONL 픽스처를 시간순으로 읽어, 실제 API와 동일한 경로/응답
 * 형태로 돌려준다.
 *
 * 백엔드 코드는 전혀 수정하지 않는다 — 개인 application.yaml의
 * lolesports.esports-api-base-url / lolesports.live-stats-base-url 을
 * 이 서버 주소로 돌리기만 하면, PollingScheduler 이하 전체 파이프라인이
 * 이 서버를 진짜 소스로 착각하고 평소처럼 동작한다.
 *
 * 사용법:
 *   node replay-server.js --dir fixtures/<matchId> [--port 4000] [--speed 1]
 *
 * 픽스처 포맷(README.md 참고): <dir>/{getLive,eventDetails,getSchedule,getStandings,window,details}.jsonl
 * 한 줄 = { "capturedAt": "<ISO-8601>", "gameId"?: "<window/details만>", "body": <원본 응답 그대로> }
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { randomUUID } = require('crypto');

function parseArgs(argv) {
  const args = { dir: null, port: 4000, speed: 1 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--dir') args.dir = argv[++i];
    else if (a === '--port') args.port = Number(argv[++i]);
    else if (a === '--speed') args.speed = Number(argv[++i]);
    else if (a === '--help' || a === '-h') { printUsage(); process.exit(0); }
  }
  if (!args.dir || !Number.isFinite(args.port) || !Number.isFinite(args.speed) || args.speed <= 0) {
    printUsage();
    process.exit(1);
  }
  return args;
}

function printUsage() {
  console.error('사용법: node replay-server.js --dir <fixture-dir> [--port 4000] [--speed 1]');
}

/** JSONL 한 줄 = 응답 하나. capturedAt 오름차순으로 정렬해서 반환한다. */
function loadJsonl(filePath) {
  if (!fs.existsSync(filePath)) return null;
  const text = fs.readFileSync(filePath, 'utf8');
  const entries = text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((line) => JSON.parse(line))
    .map((entry) => ({ ...entry, capturedAtMs: Date.parse(entry.capturedAt) }));
  entries.sort((a, b) => a.capturedAtMs - b.capturedAtMs);
  return entries;
}

/** window.jsonl/details.jsonl 은 gameId 별로 각각 시간순 배열을 갖는다 (매치 하나에 세트 여러 개). */
function loadJsonlByGameId(filePath) {
  const flat = loadJsonl(filePath);
  if (!flat) return new Map();
  const byGame = new Map();
  for (const entry of flat) {
    const list = byGame.get(entry.gameId) || [];
    list.push(entry);
    byGame.set(entry.gameId, list);
  }
  for (const list of byGame.values()) {
    list.sort((a, b) => a.capturedAtMs - b.capturedAtMs);
  }
  return byGame;
}

/** mappedMs 이하 중 가장 늦은 항목. mappedMs 가 범위를 벗어나면 양 끝 항목으로 고정된다. */
function pickAtOrBefore(entries, mappedMs) {
  if (!entries || entries.length === 0) return null;
  let picked = entries[0];
  for (const entry of entries) {
    if (entry.capturedAtMs <= mappedMs) picked = entry;
    else break;
  }
  return picked;
}

/** mappedMs 시점에 해당하는 JSONL 항목의 배열 인덱스. */
function indexAtOrBefore(entries, mappedMs) {
  if (!entries || entries.length === 0) return -1;
  let index = 0;
  for (let i = 1; i < entries.length; i++) {
    if (entries[i].capturedAtMs <= mappedMs) index = i;
    else break;
  }
  return index;
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value));
}

/**
 * 마지막 전달 위치 이후부터 현재 재생 위치까지의 frame 배열을 하나로 합친다.
 *
 * 실제 livestats API의 window/details 응답은 한 번에 최근 여러 초의 프레임을 준다.
 * 반면 녹화 JSONL은 초마다 한 줄이므로, 배속 재생에서 한 폴링 사이에 건너뛴 줄을
 * 그대로 한 번에 돌려줘야 백엔드 캐시와 DB 타임라인이 빠지지 않는다.
 */
function mergeUnservedFrameEntries(entries, mappedMs, lastServedIndex) {
  const latestIndex = indexAtOrBefore(entries, mappedMs);
  if (latestIndex < 0) return null;

  const latest = entries[latestIndex];
  if (latestIndex <= lastServedIndex) {
    // 새 프레임이 없을 때도 실제 API처럼 최신 스냅샷은 반환한다.
    return { entry: latest, body: latest.body, lastServedIndex };
  }

  const body = cloneJson(latest.body);
  const frames = [];
  const seenTimestamps = new Set();
  for (let i = Math.max(0, lastServedIndex + 1); i <= latestIndex; i++) {
    for (const frame of entries[i].body?.frames || []) {
      const timestamp = frame?.rfc460Timestamp;
      // 백엔드도 이 값으로 중복을 제거하므로, 서버 쪽에서도 같은 기준을 쓴다.
      const key = typeof timestamp === 'string' ? timestamp : `index:${i}:${frames.length}`;
      if (!seenTimestamps.has(key)) {
        seenTimestamps.add(key);
        frames.push(frame);
      }
    }
  }
  frames.sort((left, right) => Date.parse(left.rfc460Timestamp) - Date.parse(right.rfc460Timestamp));
  body.frames = frames;

  return { entry: latest, body, lastServedIndex: latestIndex };
}

/**
 * 녹화된 절대 시각(originMs 기준)을 "이번 재생이 지금 시작했다면 몇 시일지"로 옮긴다.
 *
 * getLive/getSchedule 의 매치 startTime 은 배팅 첫 세트 오픈(공식 시작 20분 전)과
 * PollingScheduler 의 배팅 후보 편입(30분 전) 판단에서 실제 지금(Instant.now())과
 * 직접 비교된다. 녹화 당시의 고정된 과거 시각을 그대로 돌려주면 이 비교가 항상
 * 어긋나므로, 배속을 반영해 재생 시작 시각 기준으로 다시 계산해야 한다.
 *
 * window/details 의 프레임 시각(rfc460Timestamp)은 여기 대상이 아니다 — 그건
 * 서로 상대적으로만 쓰이고 실제 시각과 비교되지 않는다(DataCacheService 참고).
 */
function shiftIsoTimestamp(iso, timelineMs, wallMs, speed) {
  const originalMs = Date.parse(iso);
  if (Number.isNaN(originalMs)) return iso;
  const shiftedMs = wallMs + (originalMs - timelineMs) / speed;
  return new Date(shiftedMs).toISOString();
}

/** getLive/getSchedule 응답(ScheduleResponse 모양) 안의 이벤트 startTime 을 전부 재계산한다. */
function shiftScheduleBody(body, timelineMs, wallMs, speed) {
  const events = body?.data?.schedule?.events;
  if (!Array.isArray(events)) return body;
  const shifted = JSON.parse(JSON.stringify(body));
  for (const event of shifted.data.schedule.events) {
    if (typeof event.startTime === 'string') {
      event.startTime = shiftIsoTimestamp(event.startTime, timelineMs, wallMs, speed);
    }
  }
  return shifted;
}

/**
 * window/details 프레임의 rfc460Timestamp 를 상수만큼 평행이동한다.
 *
 * 이 값은 게임 내 경과 시간 계산(frameTs - gameStartTs)에도 쓰이므로 프레임끼리의
 * 간격은 절대 바꾸면 안 된다 — 배속으로 나누면 게임 시계가 깨진다. 그래서 startTime 과
 * 달리 배속을 반영한 축소가 아니라 "통째로 밀기"만 한다.
 *
 * 부작용 1 — 다음 세트 배팅 오픈: DataCacheService.getFeedFinishedAt() 처럼 이 값을
 * "지금"과 직접 비교하는 곳은 배속이 1이 아니면 정확히 배속만큼 당겨지지 않는다 —
 * 재생 시작 후 경과 초만큼 실제로 기다려야 열린다. 게임 시계를 지키는 한 구조적으로
 * 피할 수 없는 절충이다. 배속 1이면 이 오차가 0이 된다.
 *
 * 부작용 2(중요, 실제로 걸렸던 버그) — 화면 표시 지연: ApiController 는 "지금보다
 * 45초 전" 프레임을 찾아서 보여준다. 평행이동 기준점을 세트별 첫 프레임이 아니라
 * 픽스처 전체의 시작(사전 대기 포함)으로 잡으면, 그 사전 대기 시간만큼(예: 20분)
 * "45초 전" 이 버퍼의 가장 오래된 프레임보다도 더 과거를 가리키게 되어 floorEntry 가
 * 매번 실패하고 항상 최초 프레임에 고정된다 — 배속과 무관하게, 그 대기시간이 실제로
 * 지날 때까지 화면이 멈춘 것처럼 보인다. 그래서 게임별로 "그 게임의 첫 프레임"을
 * 재생 시작 시각에 맞춰 따로 평행이동한다 — 게임이 몇 번째 세트든 캐시에 들어오는
 * 순간부터 곧바로 "45초 전" 조회가 성립한다.
 */
function shiftFramesBody(body, frameOffsetMs) {
  if (!Array.isArray(body?.frames)) return body;
  const shifted = JSON.parse(JSON.stringify(body));
  for (const frame of shifted.frames) {
    if (typeof frame.rfc460Timestamp === 'string') {
      const ms = Date.parse(frame.rfc460Timestamp);
      if (!Number.isNaN(ms)) frame.rfc460Timestamp = new Date(ms + frameOffsetMs).toISOString();
    }
  }
  return shifted;
}

/** gameId 별 평행이동량 — 그 게임의 (시간순) 첫 프레임이 재생 시작 시각에 오도록 맞춘다. */
function computeFrameOffsets(fixtures, startWallMs) {
  const offsets = new Map();
  for (const gameId of new Set([...fixtures.window.keys(), ...fixtures.details.keys()])) {
    const windowSeries = fixtures.window.get(gameId);
    const first = windowSeries && windowSeries[0];
    const frameTs = first?.body?.frames?.[0]?.rfc460Timestamp;
    const frameMs = typeof frameTs === 'string' ? Date.parse(frameTs) : NaN;
    offsets.set(gameId, Number.isNaN(frameMs) ? 0 : startWallMs - frameMs);
  }
  return offsets;
}

/** fixture 안에 등장하는 매치·세트 ID를 모은다. 원본 fixture 파일은 절대 수정하지 않는다. */
function collectFixtureIds(fixtures) {
  const matchIds = new Set();
  const gameIds = new Set([...fixtures.window.keys(), ...fixtures.details.keys()]);

  const collectBody = (body) => {
    const events = body?.data?.schedule?.events;
    if (Array.isArray(events)) {
      for (const event of events) {
        if (typeof event.id === 'string') matchIds.add(event.id);
        if (typeof event.match?.id === 'string') matchIds.add(event.match.id);
        for (const game of event.match?.games || event.games || []) {
          if (typeof game.id === 'string') gameIds.add(game.id);
        }
      }
    }

    const event = body?.data?.event;
    if (event) {
      if (typeof event.id === 'string') matchIds.add(event.id);
      if (typeof event.match?.id === 'string') matchIds.add(event.match.id);
      for (const game of event.match?.games || []) {
        if (typeof game.id === 'string') gameIds.add(game.id);
      }
    }

    if (typeof body?.esportsMatchId === 'string') matchIds.add(body.esportsMatchId);
    if (typeof body?.esportsGameId === 'string') gameIds.add(body.esportsGameId);
  };

  for (const entry of fixtures.getLive) collectBody(entry.body);
  for (const entry of fixtures.eventDetails) collectBody(entry.body);
  for (const entries of fixtures.window.values()) {
    for (const entry of entries) collectBody(entry.body);
  }
  for (const entries of fixtures.details.values()) {
    for (const entry of entries) collectBody(entry.body);
  }

  return { matchIds: [...matchIds], gameIds: [...gameIds] };
}

/**
 * 재생 한 번을 뜻하는 세션. 새 세션마다 외부 ID를 바꿔 백엔드가 새 경기로 적재하게 한다.
 * DB 컬럼 길이(32자) 안에 들어가도록 짧은 무작위 접미사를 쓴다.
 */
function createReplayRun(fixtures, speed) {
  const runId = randomUUID().replace(/-/g, '').slice(0, 10);
  const ids = collectFixtureIds(fixtures);
  const matchIdMap = new Map(ids.matchIds.map((id, index) => [id, `replay-${runId}-m${index + 1}`]));
  const gameIdMap = new Map(ids.gameIds.map((id, index) => [id, `replay-${runId}-g${index + 1}`]));
  const originalGameIdByReplayId = new Map([...gameIdMap].map(([originalId, replayId]) => [replayId, originalId]));
  const originMs = earliestCapturedAtMs(fixtures);
  const startWallMs = Date.now();

  return {
    runId,
    originMs,
    durationMs: latestCapturedAtMs(fixtures) - originMs,
    startWallMs,
    timelineAnchorMs: originMs,
    timelineAnchorWallMs: startWallMs,
    speed,
    frameOffsets: computeFrameOffsets(fixtures, startWallMs),
    matchIdMap,
    gameIdMap,
    originalGameIdByReplayId,
    // endpoint/game 별 마지막으로 백엔드에 전달한 JSONL 인덱스.
    // 새 테스트 시작 시 run 자체가 새로 만들어지므로 커서도 항상 초기화된다.
    lastServedFrameIndex: new Map(),
  };
}

/** 현재 실제 시각을 JSONL 타임라인 시각으로 바꾼다. 배속 변경 뒤에도 연속성을 유지한다. */
function timelineNowMs(run, wallMs = Date.now()) {
  return run.timelineAnchorMs + (wallMs - run.timelineAnchorWallMs) * run.speed;
}

function changeSpeed(run, speed) {
  const nowMs = Date.now();
  run.timelineAnchorMs = timelineNowMs(run, nowMs);
  run.timelineAnchorWallMs = nowMs;
  run.speed = speed;
}

/** 응답 본문 중 fixture의 매치·세트 ID와 정확히 일치하는 값만 현재 재생 세션 ID로 바꾼다. */
function replaceReplayIds(value, run) {
  if (typeof value === 'string') {
    return run.matchIdMap.get(value) || run.gameIdMap.get(value) || value;
  }
  if (Array.isArray(value)) {
    return value.map((item) => replaceReplayIds(item, run));
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, replaceReplayIds(item, run)]));
  }
  return value;
}

function runSummary(run) {
  return {
    runId: run.runId,
    matchId: run.matchIdMap.values().next().value || null,
    gameIds: [...run.gameIdMap.values()],
  };
}

/** 현재 재생 위치. 경과 시간은 실제 시간이 아니라 JSONL 타임라인 기준이다. */
function runStatus(run) {
  const totalMs = Math.max(run.durationMs, 0);
  const elapsedMs = Math.min(Math.max(timelineNowMs(run) - run.originMs, 0), totalMs);
  const progressPercent = totalMs === 0 ? 100 : Math.round((elapsedMs / totalMs) * 1000) / 10;
  return {
    ...runSummary(run),
    elapsedSeconds: Math.floor(elapsedMs / 1000),
    totalSeconds: Math.ceil(totalMs / 1000),
    progressPercent,
    fixtureTime: new Date(run.originMs + elapsedMs).toISOString(),
    speed: run.speed,
  };
}

const EMPTY_SCHEDULE_BODY = { data: { schedule: { pages: { older: null, newer: null }, events: [] } } };
const EMPTY_STANDINGS_BODY = { data: { standings: [] } };

function loadFixtures(dir) {
  const schedule = loadJsonl(path.join(dir, 'getSchedule.jsonl'));
  const standings = loadJsonl(path.join(dir, 'getStandings.jsonl'));
  return {
    getLive: loadJsonl(path.join(dir, 'getLive.jsonl')) || [],
    eventDetails: loadJsonl(path.join(dir, 'eventDetails.jsonl')) || [],
    // 없으면 pollMeta() 가 에러/백오프를 반복하지 않도록 빈 응답으로 대체한다.
    getSchedule: schedule,
    getStandings: standings,
    window: loadJsonlByGameId(path.join(dir, 'window.jsonl')),
    details: loadJsonlByGameId(path.join(dir, 'details.jsonl')),
  };
}

/** 로드된 모든 픽스처를 통틀어 가장 이른 시각 — 재생 시계의 기준점(t=0)이다. */
function earliestCapturedAtMs(fixtures) {
  const all = [
    ...fixtures.getLive,
    ...fixtures.eventDetails,
    ...(fixtures.getSchedule || []),
    ...(fixtures.getStandings || []),
    ...[...fixtures.window.values()].flat(),
    ...[...fixtures.details.values()].flat(),
  ];
  if (all.length === 0) {
    throw new Error('픽스처가 비어 있다 — getLive.jsonl / eventDetails.jsonl / window.jsonl / details.jsonl 중 최소 하나는 있어야 한다');
  }
  return Math.min(...all.map((e) => e.capturedAtMs));
}

/** JSONL 전체에서 가장 늦은 응답 시각 — 재생 타임라인의 끝이다. */
function latestCapturedAtMs(fixtures) {
  const all = [
    ...fixtures.getLive,
    ...fixtures.eventDetails,
    ...(fixtures.getSchedule || []),
    ...(fixtures.getStandings || []),
    ...[...fixtures.window.values()].flat(),
    ...[...fixtures.details.values()].flat(),
  ];
  return Math.max(...all.map((e) => e.capturedAtMs));
}

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(payload);
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const fixtures = loadFixtures(args.dir);
  let run = createReplayRun(fixtures, args.speed);

  console.log(`[replay] 픽스처 로드 완료: ${args.dir}`);
  console.log(`[replay]   getLive ${fixtures.getLive.length}건 · eventDetails ${fixtures.eventDetails.length}건 · ` +
    `schedule ${fixtures.getSchedule ? fixtures.getSchedule.length : '없음(빈 응답으로 대체)'} · ` +
    `standings ${fixtures.getStandings ? fixtures.getStandings.length : '없음(빈 응답으로 대체)'}`);
  console.log(`[replay]   window/details gameId: ${[...fixtures.window.keys()].join(', ') || '없음'} ` +
    `(각 게임 첫 프레임을 재생 시작 시각에 맞춤 — 세트 진입 즉시 화면에 반영됨)`);
  console.log(`[replay] 배속 ${run.speed}x 로 재생 시작 (기준 시각 ${new Date(run.originMs).toISOString()})`);
  console.log(`[replay] 실행 ID: ${run.runId} · matchId: ${runSummary(run).matchId}`);
  console.log('[replay]   배속 1이 아니면 세트 종료~다음 세트 배팅 오픈 텀이 실제 배속만큼 줄지 않음 — README 참고');

  /** 지금(wall clock)이 녹화 타임라인상 몇 시일지 계산한다. */
  function currentMappedNowMs() {
    return timelineNowMs(run);
  }

  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const mappedMs = currentMappedNowMs();
    const pathname = url.pathname;

    if (pathname === '/__replay/start') {
      if (req.method !== 'POST') return sendJson(res, 405, { error: 'POST 요청만 허용한다' });
      run = createReplayRun(fixtures, args.speed);
      const summary = runSummary(run);
      console.log(`[replay] 새 재생 시작: runId=${summary.runId} · matchId=${summary.matchId}`);
      return sendJson(res, 200, summary);
    }
    if (pathname === '/__replay/status') {
      if (req.method !== 'GET') return sendJson(res, 405, { error: 'GET 요청만 허용한다' });
      return sendJson(res, 200, runStatus(run));
    }
    if (pathname === '/__replay/speed') {
      if (req.method !== 'POST') return sendJson(res, 405, { error: 'POST 요청만 허용한다' });
      const speed = Number(url.searchParams.get('value'));
      if (!Number.isFinite(speed) || speed < 1 || speed > 20) {
        return sendJson(res, 400, { error: '배속은 1 이상 20 이하여야 한다' });
      }
      changeSpeed(run, speed);
      console.log(`[replay] 배속 변경: ${speed}x`);
      return sendJson(res, 200, runStatus(run));
    }

    // getLive/getSchedule 의 startTime 은 실제 지금과 비교되므로 재생 시작 시각 기준으로 옮겨서 돌려준다.
    const transform = (body) => replaceReplayIds(body, run);
    const shiftSchedule = (body) => transform(shiftScheduleBody(body, mappedMs, Date.now(), run.speed));
    const shiftFrames = (body, originalGameId) => transform(shiftFramesBody(body, run.frameOffsets.get(originalGameId) ?? 0));

    let match;
    if (pathname === '/getLive') {
      return respondPicked(res, pathname, pickAtOrBefore(fixtures.getLive, mappedMs), shiftSchedule);
    }
    if (pathname === '/getEventDetails') {
      return respondPicked(res, pathname, pickAtOrBefore(fixtures.eventDetails, mappedMs), transform);
    }
    if (pathname === '/getSchedule') {
      if (!fixtures.getSchedule) return sendJson(res, 200, EMPTY_SCHEDULE_BODY);
      return respondPicked(res, pathname, pickAtOrBefore(fixtures.getSchedule, mappedMs), shiftSchedule);
    }
    if (pathname === '/getStandings') {
      if (!fixtures.getStandings) return sendJson(res, 200, EMPTY_STANDINGS_BODY);
      return respondPicked(res, pathname, pickAtOrBefore(fixtures.getStandings, mappedMs));
    }
    if ((match = pathname.match(/^\/window\/(.+)$/))) {
      const replayGameId = decodeURIComponent(match[1]);
      const originalGameId = run.originalGameIdByReplayId.get(replayGameId);
      const series = fixtures.window.get(originalGameId);
      if (!series) return sendJson(res, 404, { error: `녹화된 window 데이터 없음: ${replayGameId}` });
      // startingTime 파라미터가 없는 호출은 LiveStatsClient.getGameStartTimestamp() 전용 —
      // "게임 시작 첫 프레임"을 기대하므로 재생 시각과 무관하게 최초 항목을 돌려줘야 한다.
      if (!url.searchParams.has('startingTime')) {
        return respondPicked(res, pathname, series[0], (body) => shiftFrames(body, originalGameId));
      }
      return respondMergedFrames(res, pathname, 'window', originalGameId, series, mappedMs,
        (body) => shiftFrames(body, originalGameId));
    }
    if ((match = pathname.match(/^\/details\/(.+)$/))) {
      const replayGameId = decodeURIComponent(match[1]);
      const originalGameId = run.originalGameIdByReplayId.get(replayGameId);
      const series = fixtures.details.get(originalGameId);
      if (!series) return sendJson(res, 404, { error: `녹화된 details 데이터 없음: ${replayGameId}` });
      return respondMergedFrames(res, pathname, 'details', originalGameId, series, mappedMs,
        (body) => shiftFrames(body, originalGameId));
    }

    sendJson(res, 404, { error: `알 수 없는 경로: ${pathname}` });
  });

  function respondPicked(res, pathname, entry, transformBody) {
    if (!entry) return sendJson(res, 404, { error: `${pathname} 재생 데이터 없음` });
    const body = transformBody ? transformBody(entry.body) : entry.body;
    console.log(`[replay] ${pathname} -> capturedAt=${entry.capturedAt}`);
    sendJson(res, 200, body);
  }

  function respondMergedFrames(res, pathname, endpoint, originalGameId, series, mappedMs, transformBody) {
    const cursorKey = `${endpoint}:${originalGameId}`;
    const lastServedIndex = run.lastServedFrameIndex.get(cursorKey) ?? -1;
    const merged = mergeUnservedFrameEntries(series, mappedMs, lastServedIndex);
    if (!merged) return sendJson(res, 404, { error: `${pathname} 재생 데이터 없음` });

    run.lastServedFrameIndex.set(cursorKey, merged.lastServedIndex);
    const body = transformBody ? transformBody(merged.body) : merged.body;
    const frames = Array.isArray(merged.body?.frames) ? merged.body.frames.length : 0;
    console.log(`[replay] ${pathname} -> capturedAt=${merged.entry.capturedAt} · frames=${frames}`);
    sendJson(res, 200, body);
  }

  server.listen(args.port, () => {
    console.log(`[replay] 스텁 서버 대기 중: http://localhost:${args.port}`);
    console.log('[replay] 개인 application.yaml 에서 lolesports.esports-api-base-url / ' +
      'lolesports.live-stats-base-url 을 이 주소로 돌리면 연결된다.');
  });
}

main();
