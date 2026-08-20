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
function shiftIsoTimestamp(iso, originMs, startWallMs, speed) {
  const originalMs = Date.parse(iso);
  if (Number.isNaN(originalMs)) return iso;
  const shiftedMs = startWallMs + (originalMs - originMs) / speed;
  return new Date(shiftedMs).toISOString();
}

/** getLive/getSchedule 응답(ScheduleResponse 모양) 안의 이벤트 startTime 을 전부 재계산한다. */
function shiftScheduleBody(body, originMs, startWallMs, speed) {
  const events = body?.data?.schedule?.events;
  if (!Array.isArray(events)) return body;
  const shifted = JSON.parse(JSON.stringify(body));
  for (const event of shifted.data.schedule.events) {
    if (typeof event.startTime === 'string') {
      event.startTime = shiftIsoTimestamp(event.startTime, originMs, startWallMs, speed);
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

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(payload);
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const fixtures = loadFixtures(args.dir);
  const originMs = earliestCapturedAtMs(fixtures);
  const startWallMs = Date.now();
  const frameOffsets = computeFrameOffsets(fixtures, startWallMs);

  console.log(`[replay] 픽스처 로드 완료: ${args.dir}`);
  console.log(`[replay]   getLive ${fixtures.getLive.length}건 · eventDetails ${fixtures.eventDetails.length}건 · ` +
    `schedule ${fixtures.getSchedule ? fixtures.getSchedule.length : '없음(빈 응답으로 대체)'} · ` +
    `standings ${fixtures.getStandings ? fixtures.getStandings.length : '없음(빈 응답으로 대체)'}`);
  console.log(`[replay]   window/details gameId: ${[...fixtures.window.keys()].join(', ') || '없음'} ` +
    `(각 게임 첫 프레임을 재생 시작 시각에 맞춤 — 세트 진입 즉시 화면에 반영됨)`);
  console.log(`[replay] 배속 ${args.speed}x 로 재생 시작 (기준 시각 ${new Date(originMs).toISOString()})`);
  console.log('[replay]   배속 1이 아니면 세트 종료~다음 세트 배팅 오픈 텀이 실제 배속만큼 줄지 않음 — README 참고');

  /** 지금(wall clock)이 녹화 타임라인상 몇 시일지 계산한다. */
  function mappedNowMs() {
    return originMs + (Date.now() - startWallMs) * args.speed;
  }

  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const mappedMs = mappedNowMs();
    const pathname = url.pathname;

    // getLive/getSchedule 의 startTime 은 실제 지금과 비교되므로 재생 시작 시각 기준으로 옮겨서 돌려준다.
    const shiftSchedule = (body) => shiftScheduleBody(body, originMs, startWallMs, args.speed);
    const shiftFrames = (body, gameId) => shiftFramesBody(body, frameOffsets.get(gameId) ?? 0);

    let match;
    if (pathname === '/getLive') {
      return respondPicked(res, pathname, pickAtOrBefore(fixtures.getLive, mappedMs), shiftSchedule);
    }
    if (pathname === '/getEventDetails') {
      return respondPicked(res, pathname, pickAtOrBefore(fixtures.eventDetails, mappedMs));
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
      const gameId = decodeURIComponent(match[1]);
      const series = fixtures.window.get(gameId);
      if (!series) return sendJson(res, 404, { error: `녹화된 window 데이터 없음: ${gameId}` });
      // startingTime 파라미터가 없는 호출은 LiveStatsClient.getGameStartTimestamp() 전용 —
      // "게임 시작 첫 프레임"을 기대하므로 재생 시각과 무관하게 최초 항목을 돌려줘야 한다.
      const entry = url.searchParams.has('startingTime')
        ? pickAtOrBefore(series, mappedMs)
        : series[0];
      return respondPicked(res, pathname, entry, (body) => shiftFrames(body, gameId));
    }
    if ((match = pathname.match(/^\/details\/(.+)$/))) {
      const gameId = decodeURIComponent(match[1]);
      const series = fixtures.details.get(gameId);
      if (!series) return sendJson(res, 404, { error: `녹화된 details 데이터 없음: ${gameId}` });
      return respondPicked(res, pathname, pickAtOrBefore(series, mappedMs), (body) => shiftFrames(body, gameId));
    }

    sendJson(res, 404, { error: `알 수 없는 경로: ${pathname}` });
  });

  function respondPicked(res, pathname, entry, transformBody) {
    if (!entry) return sendJson(res, 404, { error: `${pathname} 재생 데이터 없음` });
    const body = transformBody ? transformBody(entry.body) : entry.body;
    console.log(`[replay] ${pathname} -> capturedAt=${entry.capturedAt}`);
    sendJson(res, 200, body);
  }

  server.listen(args.port, () => {
    console.log(`[replay] 스텁 서버 대기 중: http://localhost:${args.port}`);
    console.log('[replay] 개인 application.yaml 에서 lolesports.esports-api-base-url / ' +
      'lolesports.live-stats-base-url 을 이 주소로 돌리면 연결된다.');
  });
}

main();
