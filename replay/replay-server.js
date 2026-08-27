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
    // 이전 Compose 명령과의 호환성. 프레임 시계는 이제 항상 재생 배속을 따른다.
    else if (a === '--compress-frame-time') continue;
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

/** 이 크기 이상은 본문을 메모리에 적재하지 않고 JSONL 줄 위치만 인덱싱한다. */
const LAZY_LOAD_THRESHOLD_BYTES = 64 * 1024 * 1024;
const JSONL_INDEX_PREFIX_BYTES = 1024;
const JSONL_INDEX_BUFFER_BYTES = 1024 * 1024;
const RESOURCE_INTERPOLATION_MIN_GAP_MS = 10 * 1000;

/** eager/lazy fixture를 같은 방식으로 순회·조회하는 시계열 래퍼. */
class FixtureSeries {
  constructor(entries, filePath = null) {
    this.entries = entries;
    this.filePath = filePath;
  }

  get length() {
    return this.entries.length;
  }

  capturedAtMsAt(index) {
    return this.entries[index]?.capturedAtMs;
  }

  /** lazy 인덱스 항목이면 필요한 한 줄만 읽어 JSON으로 복원한다. */
  entryAt(index) {
    const entry = this.entries[index];
    if (!entry || Object.hasOwn(entry, 'body')) return entry || null;

    const buffer = Buffer.allocUnsafe(entry.length);
    const descriptor = fs.openSync(this.filePath, 'r');
    try {
      const bytesRead = fs.readSync(descriptor, buffer, 0, entry.length, entry.offset);
      if (bytesRead !== entry.length) {
        throw new Error(`fixture 줄을 끝까지 읽지 못했습니다: ${this.filePath} @ ${entry.offset}`);
      }
      const parsed = JSON.parse(buffer.toString('utf8', 0, bytesRead));
      return { ...parsed, capturedAtMs: entry.capturedAtMs };
    } finally {
      fs.closeSync(descriptor);
    }
  }
}

/**
 * 큰 JSONL의 줄 시작 위치·길이·시간·gameId만 읽는다. 본문은 요청이 올 때까지 파싱하지 않는다.
 * 표준 fixture는 gameId를 줄 앞부분에 기록한다. 이전 변환 결과도 읽을 수 있도록 줄 끝 1KB도 함께 확인한다.
 */
function buildJsonlIndex(filePath) {
  const descriptor = fs.openSync(filePath, 'r');
  const size = fs.fstatSync(descriptor).size;
  const buffer = Buffer.allocUnsafe(JSONL_INDEX_BUFFER_BYTES);
  const prefix = Buffer.allocUnsafe(JSONL_INDEX_PREFIX_BYTES);
  const suffix = Buffer.allocUnsafe(JSONL_INDEX_PREFIX_BYTES);
  const entries = [];
  let fileOffset = 0;
  let lineStart = 0;
  let prefixLength = 0;
  let suffixLength = 0;

  const appendPrefix = (source, start, end) => {
    if (prefixLength >= JSONL_INDEX_PREFIX_BYTES || start >= end) return;
    const available = JSONL_INDEX_PREFIX_BYTES - prefixLength;
    const copied = Math.min(available, end - start);
    source.copy(prefix, prefixLength, start, start + copied);
    prefixLength += copied;
  };

  const appendSuffix = (source, start, end) => {
    const length = end - start;
    if (length <= 0) return;
    if (length >= JSONL_INDEX_PREFIX_BYTES) {
      source.copy(suffix, 0, end - JSONL_INDEX_PREFIX_BYTES, end);
      suffixLength = JSONL_INDEX_PREFIX_BYTES;
      return;
    }

    const overflow = Math.max(0, suffixLength + length - JSONL_INDEX_PREFIX_BYTES);
    if (overflow > 0) {
      suffix.copy(suffix, 0, overflow, suffixLength);
      suffixLength -= overflow;
    }
    source.copy(suffix, suffixLength, start, end);
    suffixLength += length;
  };

  const registerLine = (lineEnd) => {
    if (prefixLength === 0) return;
    const header = prefix.toString('utf8', 0, prefixLength);
    if (header.trim().length === 0) return;
    const capturedAt = header.match(/"capturedAt"\s*:\s*"([^"]+)"/)?.[1];
    if (!capturedAt) {
      throw new Error(`fixture capturedAt을 찾지 못했습니다: ${filePath} @ ${lineStart}`);
    }
    const capturedAtMs = Date.parse(capturedAt);
    if (Number.isNaN(capturedAtMs)) {
      throw new Error(`fixture capturedAt 형식이 올바르지 않습니다: ${capturedAt}`);
    }
    const trailing = suffix.toString('utf8', 0, suffixLength);
    const gameId = header.match(/"gameId"\s*:\s*"([^"]+)"/)?.[1]
      || trailing.match(/"gameId"\s*:\s*"([^"]+)"\s*}\s*$/)?.[1];
    entries.push({
      capturedAt,
      capturedAtMs,
      gameId,
      offset: lineStart,
      length: lineEnd - lineStart,
    });
  };

  try {
    while (fileOffset < size) {
      const bytesRead = fs.readSync(descriptor, buffer, 0, buffer.length, fileOffset);
      if (bytesRead === 0) break;

      let cursor = 0;
      while (cursor < bytesRead) {
        const newline = buffer.indexOf(0x0A, cursor);
        if (newline < 0 || newline >= bytesRead) {
          appendPrefix(buffer, cursor, bytesRead);
          appendSuffix(buffer, cursor, bytesRead);
          break;
        }
        appendPrefix(buffer, cursor, newline);
        appendSuffix(buffer, cursor, newline);
        registerLine(fileOffset + newline);
        lineStart = fileOffset + newline + 1;
        prefixLength = 0;
        suffixLength = 0;
        cursor = newline + 1;
      }
      fileOffset += bytesRead;
    }

    if (lineStart < size) {
      registerLine(size);
    }
  } finally {
    fs.closeSync(descriptor);
  }

  entries.sort((left, right) => left.capturedAtMs - right.capturedAtMs);
  return entries;
}

/** JSONL 한 줄 = 응답 하나. 작은 파일은 즉시 파싱하고, 큰 파일은 줄 위치만 인덱싱한다. */
function loadJsonl(filePath) {
  if (!fs.existsSync(filePath)) return null;
  const size = fs.statSync(filePath).size;
  if (size > LAZY_LOAD_THRESHOLD_BYTES) {
    return new FixtureSeries(buildJsonlIndex(filePath), filePath);
  }

  const text = fs.readFileSync(filePath, 'utf8');
  const entries = text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((line) => JSON.parse(line))
    .map((entry) => ({ ...entry, capturedAtMs: Date.parse(entry.capturedAt) }));
  entries.sort((left, right) => left.capturedAtMs - right.capturedAtMs);
  return new FixtureSeries(entries);
}

/** window.jsonl/details.jsonl 은 gameId 별로 각각 시간순 시계열을 갖는다 (매치 하나에 세트 여러 개). */
function loadJsonlByGameId(filePath) {
  const flat = loadJsonl(filePath);
  if (!flat) return new Map();
  const byGame = new Map();
  for (const entry of flat.entries) {
    if (!entry.gameId) continue;
    const list = byGame.get(entry.gameId) || [];
    list.push(entry);
    byGame.set(entry.gameId, list);
  }
  return new Map([...byGame].map(([gameId, entries]) => [
    gameId,
    new FixtureSeries(entries, flat.filePath),
  ]));
}

/**
 * mappedMs 이하 중 가장 늦은 항목. 마지막 항목 뒤에서는 마지막 스냅샷을 유지한다.
 * 첫 항목 전에는 아직 녹화된 응답이 없으므로 null을 반환한다.
 */
function pickAtOrBefore(series, mappedMs) {
  const index = indexAtOrBefore(series, mappedMs);
  return index < 0 ? null : series.entryAt(index);
}

/** mappedMs 시점에 해당하는 JSONL 항목의 배열 인덱스. */
function indexAtOrBefore(series, mappedMs) {
  if (!series || series.length === 0 || series.capturedAtMsAt(0) > mappedMs) return -1;

  let left = 0;
  let right = series.length - 1;
  while (left < right) {
    const middle = Math.ceil((left + right) / 2);
    if (series.capturedAtMsAt(middle) <= mappedMs) left = middle;
    else right = middle - 1;
  }
  return left;
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
function mergeUnservedFrameEntries(series, mappedMs, lastServedIndex) {
  // window/details가 아직 첫 성공 응답을 받지 못한 구간이다. 여기서 첫 프레임을
  // 미리 반환하면 같은 미래 프레임이 캐시에 고정되어 화면이 멈춘 것처럼 보인다.
  if (!series || series.length === 0 || series.capturedAtMsAt(0) > mappedMs) return null;
  const latestIndex = indexAtOrBefore(series, mappedMs);
  if (latestIndex < 0) return null;

  const latest = series.entryAt(latestIndex);
  if (latestIndex <= lastServedIndex) {
    // 새 프레임이 없을 때도 실제 API처럼 최신 스냅샷은 반환한다.
    return { entry: latest, body: latest.body, lastServedIndex };
  }

  const body = cloneJson(latest.body);
  const frames = [];
  const seenTimestamps = new Set();
  for (let i = Math.max(0, lastServedIndex + 1); i <= latestIndex; i++) {
    for (const frame of series.entryAt(i).body?.frames || []) {
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

/** mappedMs 뒤의 가장 이른 실제 window 프레임을 찾는다. */
function findNextWindowFrame(series, mappedMs) {
  // 한 JSONL 응답에 과거·미래 프레임이 함께 담길 수 있다. 현재 응답도 다시 살펴야
  // 같은 응답 안의 다음 실제 프레임을 놓치지 않는다.
  const firstIndex = Math.max(0, indexAtOrBefore(series, mappedMs));
  for (let index = firstIndex; index < series.length; index++) {
    let candidate = null;
    for (const frame of series.entryAt(index).body?.frames || []) {
      const timestamp = Date.parse(frame?.rfc460Timestamp);
      if (!Number.isNaN(timestamp) && timestamp > mappedMs
        && (candidate == null || timestamp < Date.parse(candidate.rfc460Timestamp))) {
        candidate = frame;
      }
    }
    if (candidate != null) return candidate;
  }
  return null;
}

function interpolateInteger(previous, next, ratio, round = Math.round) {
  if (!Number.isFinite(previous) || !Number.isFinite(next)) return previous;
  return round(previous + (next - previous) * ratio);
}

/** 숫자로 관측되는 자원만 보간한다. 킬·오브젝트 같은 사건 데이터는 미래 값을 앞당기지 않는다. */
function interpolateTeamResources(previousTeam, nextTeam, ratio) {
  if (!Array.isArray(previousTeam?.participants) || !Array.isArray(nextTeam?.participants)) return;
  const nextByParticipantId = new Map(nextTeam.participants.map((participant) => [participant.participantId, participant]));
  for (const participant of previousTeam.participants) {
    const nextParticipant = nextByParticipantId.get(participant.participantId);
    if (nextParticipant == null) continue;
    participant.totalGold = interpolateInteger(participant.totalGold, nextParticipant.totalGold, ratio);
    participant.creepScore = interpolateInteger(
      participant.creepScore,
      nextParticipant.creepScore,
      ratio,
      Math.floor,
    );
  }
  const totalGold = previousTeam.participants.reduce((total, participant) => total + (participant.totalGold ?? 0), 0);
  if (Number.isFinite(totalGold)) previousTeam.totalGold = totalGold;
}

/**
 * 녹화에 긴 시작 구간이 비어 있으면 다음 실제 관측값까지 gold/CS를 선형 보간한다.
 * 원본 프레임의 킬·사망·오브젝트는 유지하므로, 아직 일어나지 않은 경기 사건을 노출하지 않는다.
 */
function appendInterpolatedWindowFrame(body, series, mappedMs) {
  if (!Array.isArray(body?.frames) || body.frames.length === 0) return body;
  const previous = body.frames
    .filter((frame) => Date.parse(frame?.rfc460Timestamp) <= mappedMs)
    .at(-1);
  const previousMs = Date.parse(previous?.rfc460Timestamp);
  const next = findNextWindowFrame(series, mappedMs);
  const nextMs = Date.parse(next?.rfc460Timestamp);
  if (Number.isNaN(previousMs) || Number.isNaN(nextMs)
    || nextMs - previousMs < RESOURCE_INTERPOLATION_MIN_GAP_MS) {
    return body;
  }

  const ratio = (mappedMs - previousMs) / (nextMs - previousMs);
  if (ratio <= 0 || ratio >= 1) return body;

  const out = cloneJson(body);
  const interpolated = cloneJson(previous);
  interpolated.rfc460Timestamp = new Date(mappedMs).toISOString();
  interpolateTeamResources(interpolated.blueTeam, next.blueTeam, ratio);
  interpolateTeamResources(interpolated.redTeam, next.redTeam, ratio);
  out.frames.push(interpolated);
  out.frames.sort((left, right) => Date.parse(left.rfc460Timestamp) - Date.parse(right.rfc460Timestamp));
  return out;
}

/**
 * 녹화된 절대 시각(originMs 기준)을 "이번 재생이 지금 시작했다면 몇 시일지"로 옮긴다.
 *
 * getLive/getSchedule 의 매치 startTime 은 배팅 첫 세트 오픈(공식 시작 20분 전)과
 * PollingScheduler 의 배팅 후보 편입(30분 전) 판단에서 실제 지금(Instant.now())과
 * 직접 비교된다. 녹화 당시의 고정된 과거 시각을 그대로 돌려주면 이 비교가 항상
 * 어긋나므로, 배속을 반영해 재생 시작 시각 기준으로 다시 계산해야 한다.
 *
 * window/details의 프레임 전달 시각도 같은 시간축으로 옮긴다. 이 시각은 캐시에서 현재
 * 재생 위치의 프레임을 고르고, 다음 세트 배팅 마감을 판단하는 데 쓴다.
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

/** fixture 변환 전 대기 프레임을 기준으로 생긴 공통 +500G 오프셋을 제거한다. */
function correctLegacyOpeningGold(body, run, originalGameId) {
  if (!Array.isArray(body?.frames)) return body;
  // 아래 timestamp 이동은 body를 수정하므로, 보정 대상이 아닌 프레임도 fixture 원본을
  // 오염시키지 않도록 항상 복제한다.
  const corrected = cloneJson(body);
  const offset = run.openingGoldOffsetByOriginalId?.get(originalGameId);
  if (!Number.isFinite(offset) || offset <= 0) return corrected;
  for (const frame of corrected.frames) {
    const teams = [frame.blueTeam, frame.redTeam].filter(Boolean);
    if (teams.length > 0) {
      const participants = teams.flatMap((team) => team.participants || []);
      // 진짜 시작 프레임의 500G는 보존한다.
      const isOpeningFrame = participants.length > 0
        && participants.every((participant) => participant.totalGold === 500 && participant.creepScore === 0);
      if (isOpeningFrame) continue;
      for (const team of teams) {
        for (const participant of team.participants || []) {
          if (Number.isFinite(participant.totalGold)) {
            participant.totalGold = Math.max(0, participant.totalGold - offset);
          }
        }
        const totalGold = (team.participants || []).reduce(
          (total, participant) => total + (participant.totalGold ?? 0),
          0,
        );
        if (Number.isFinite(totalGold)) team.totalGold = totalGold;
      }
      continue;
    }

    const participants = frame.participants || [];
    const isOpeningFrame = participants.length > 0
      && participants.every((participant) => participant.totalGoldEarned === 500);
    if (isOpeningFrame) continue;
    for (const participant of participants) {
      if (Number.isFinite(participant.totalGoldEarned)) {
        participant.totalGoldEarned = Math.max(0, participant.totalGoldEarned - offset);
      }
    }
  }
  return corrected;
}

/**
 * window/details 프레임의 rfc460Timestamp를 재생 시간축의 실제 벽시계로 바꾼다.
 *
 * `rfc460Timestamp`는 재생 프레임을 현재 시각에서 선택할 수 있도록 배속만큼 압축한다.
 * 이 값으로 게임 시간을 계산하면 20배속이어도 1초씩만 늘어나므로, 원래 게임 경과 시간은
 * 별도 `gameTimeSeconds` 필드에 보존한다. 실제 livestats 응답에는 없는 replay 전용 필드다.
 */
function shiftFramesBody(body, run, originalGameId) {
  if (!Array.isArray(body?.frames)) return body;
  const shifted = correctLegacyOpeningGold(body, run, originalGameId);
  const gameStartMs = run.gameStartMsByOriginalId.get(originalGameId);
  for (const frame of shifted.frames) {
    if (typeof frame.rfc460Timestamp === 'string') {
      const ms = Date.parse(frame.rfc460Timestamp);
      if (!Number.isNaN(ms)) {
        if (Number.isFinite(gameStartMs)) {
          frame.gameTimeSeconds = Math.max(0, Math.floor((ms - gameStartMs) / 1000));
        }
        frame.rfc460Timestamp = new Date(wallMsForTimeline(run, ms)).toISOString();
      }
    }
  }
  return shifted;
}

/**
 * 녹화본에 다음 스냅샷이 없는 구간에도 마지막 프레임의 시계를 현재 재생 위치까지 전진시킨다.
 *
 * 실제 기록은 세트 시작 직후 다음 프레임이 몇 분 뒤에만 있는 경우가 있다. 그 프레임을
 * 그대로 재사용하면 백엔드 캐시가 같은 RFC 시각을 중복으로 버려 화면 타이머가 0초에
 * 고정된다. 선수·골드 값은 마지막 관측값을 유지하되, 프레임 시각과 gameTimeSeconds는
 * 재생 시계로 갱신해 1배속은 1초, 20배속은 20초씩 계속 흐르게 한다.
 */
function advanceLatestFrameToReplayClock(body, run, originalGameId, mappedMs, includeGameTime) {
  if (!Array.isArray(body?.frames) || body.frames.length === 0) return body;
  const gameStartMs = run.gameStartMsByOriginalId.get(originalGameId);
  if (!Number.isFinite(gameStartMs) || mappedMs < gameStartMs) return body;

  const latest = body.frames.at(-1);
  if (!latest || String(latest.gameState).toLowerCase() === 'finished') return body;
  const replayGameTimeSeconds = Math.max(0, Math.floor((mappedMs - gameStartMs) / 1000));
  // 수집 순서가 뒤섞인 fixture는 현재 재생 위치보다 앞선 캡처에 더 늦은 원본 프레임을
  // 담을 수 있다. 그 경우에는 검증 가능한 원본 프레임의 시간 정보를 덮어쓰지 않는다.
  if (includeGameTime && Number.isFinite(latest.gameTimeSeconds)
    && latest.gameTimeSeconds > replayGameTimeSeconds) {
    return body;
  }
  latest.rfc460Timestamp = new Date().toISOString();
  if (includeGameTime) {
    latest.gameTimeSeconds = replayGameTimeSeconds;
  }
  return body;
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

  const collectSeries = (series) => {
    if (!series) return;
    for (let index = 0; index < series.length; index++) {
      collectBody(series.entryAt(index).body);
    }
  };
  // getLive/eventDetails/getSchedule은 작아서 여기서 본문을 읽어도 된다. 대용량
  // window/details는 gameId를 파일 인덱스에서 이미 수집했으므로 본문을 전수 파싱하지 않는다.
  collectSeries(fixtures.getLive);
  collectSeries(fixtures.eventDetails);
  collectSeries(fixtures.getSchedule);

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
    matchIdMap,
    gameIdMap,
    originalGameIdByReplayId,
    gameStartMsByOriginalId: fixtures.gameStartMsByOriginalId,
    gameFinishMsByOriginalId: fixtures.gameFinishMsByOriginalId,
    openingGoldOffsetByOriginalId: fixtures.openingGoldOffsetByOriginalId,
    gameResultWinsByOriginalId: fixtures.gameResultWinsByOriginalId,
    // endpoint/game 별 마지막으로 백엔드에 전달한 JSONL 인덱스.
    // 새 테스트 시작 시 run 자체가 새로 만들어지므로 커서도 항상 초기화된다.
    lastServedFrameIndex: new Map(),
  };
}

/** 현재 실제 시각을 JSONL 타임라인 시각으로 바꾼다. 배속 변경 뒤에도 연속성을 유지한다. */
function timelineNowMs(run, wallMs = Date.now()) {
  return run.timelineAnchorMs + (wallMs - run.timelineAnchorWallMs) * run.speed;
}

/** 재생 시간축의 시각을 현재 배속을 반영한 실제 벽시계 시각으로 바꾼다. */
function wallMsForTimeline(run, timelineMs) {
  return run.timelineAnchorWallMs + (timelineMs - run.timelineAnchorMs) / run.speed;
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
  const eventDetails = loadJsonl(path.join(dir, 'eventDetails.jsonl')) || new FixtureSeries([]);
  const window = loadJsonlByGameId(path.join(dir, 'window.jsonl'));
  return {
    getLive: loadJsonl(path.join(dir, 'getLive.jsonl')) || new FixtureSeries([]),
    eventDetails,
    // schedule에는 팀 ID가 빠진 경우가 많다. 시작 전 배팅 이벤트를 만들려면 팀 ID를
    // 담은 안전한(unstarted) 상세 스냅샷을 첫 폴링부터 읽을 수 있어야 한다.
    preMatchEventDetails: findPreMatchEventDetails(eventDetails),
    // 없으면 pollMeta() 가 에러/백오프를 반복하지 않도록 빈 응답으로 대체한다.
    getSchedule: schedule,
    getStandings: standings,
    window,
    details: loadJsonlByGameId(path.join(dir, 'details.jsonl')),
    gameStartMsByOriginalId: findGameStartMsByOriginalId(window),
    gameFinishMsByOriginalId: findGameFinishMsByOriginalId(window),
    openingGoldOffsetByOriginalId: findOpeningGoldOffsetsByOriginalId(window),
    gameResultWinsByOriginalId: findGameResultWinsByOriginalId(eventDetails),
  };
}

/** replay 화면 타이머의 0초 기준인 게임별 첫 비정지 프레임 시각을 찾는다. */
function findGameStartMsByOriginalId(windowByGameId) {
  const starts = new Map();
  for (const [gameId, series] of windowByGameId) {
    let earliestStartedFrameMs = Number.POSITIVE_INFINITY;
    for (let index = 0; index < series.length; index++) {
      for (const frame of series.entryAt(index).body?.frames || []) {
        const timestamp = Date.parse(frame?.rfc460Timestamp);
        if (!Number.isNaN(timestamp) && !String(frame?.gameState).toLowerCase().includes('paused')) {
          earliestStartedFrameMs = Math.min(earliestStartedFrameMs, timestamp);
        }
      }
    }
    if (Number.isFinite(earliestStartedFrameMs)) {
      starts.set(gameId, earliestStartedFrameMs);
    }
  }
  return starts;
}

/** 게임별 최초 finished 프레임 시각. 세트 사이 대기 구간을 판별할 때 쓴다. */
function findGameFinishMsByOriginalId(windowByGameId) {
  const finishes = new Map();
  for (const [gameId, series] of windowByGameId) {
    let earliestFinishedFrameMs = Number.POSITIVE_INFINITY;
    for (let index = 0; index < series.length; index++) {
      for (const frame of series.entryAt(index).body?.frames || []) {
        const timestamp = Date.parse(frame?.rfc460Timestamp);
        if (!Number.isNaN(timestamp) && String(frame?.gameState).toLowerCase() === 'finished') {
          earliestFinishedFrameMs = Math.min(earliestFinishedFrameMs, timestamp);
        }
      }
    }
    if (Number.isFinite(earliestFinishedFrameMs)) {
      finishes.set(gameId, earliestFinishedFrameMs);
    }
  }
  return finishes;
}

/**
 * 기존 reshape 결과 중 일부는 paused 프레임을 골드 기준점으로 사용해, 시작 직후 전원에게
 * +500G가 한 번 더 붙었다. 시작 1분 내 500G → 전원 1,000G·CS 0 패턴만 보정 대상으로 잡는다.
 */
function findOpeningGoldOffsetsByOriginalId(windowByGameId) {
  const offsets = new Map();
  for (const [gameId, series] of windowByGameId) {
    const frames = [];
    for (let index = 0; index < series.length; index++) {
      frames.push(...(series.entryAt(index).body?.frames || []));
    }
    frames.sort((left, right) => Date.parse(left.rfc460Timestamp) - Date.parse(right.rfc460Timestamp));
    const start = frames.find((frame) => String(frame?.gameState).toLowerCase() === 'in_game');
    const startMs = Date.parse(start?.rfc460Timestamp);
    const openingParticipants = [start?.blueTeam, start?.redTeam]
      .flatMap((team) => team?.participants || []);
    const startsAtFiveHundred = openingParticipants.length > 0
      && openingParticipants.every((participant) => participant.totalGold === 500 && participant.creepScore === 0);
    const hasDuplicatedGrant = frames.some((frame) => {
      const timestamp = Date.parse(frame?.rfc460Timestamp);
      const participants = [frame?.blueTeam, frame?.redTeam].flatMap((team) => team?.participants || []);
      return Number.isFinite(timestamp) && timestamp > startMs && timestamp <= startMs + 60 * 1000
        && participants.length === openingParticipants.length
        && participants.every((participant) => participant.totalGold === 1000 && participant.creepScore === 0);
    });
    if (startsAtFiveHundred && hasDuplicatedGrant) {
      offsets.set(gameId, 500);
    }
  }
  return offsets;
}

/** 세트가 completed로 처음 관측된 공식 팀 승수를 gameId별로 기록한다. */
function findGameResultWinsByOriginalId(series) {
  const winsByGameId = new Map();
  for (let index = 0; index < series.length; index++) {
    const match = series.entryAt(index).body?.data?.event?.match;
    const wins = new Map((match?.teams || [])
      .filter((team) => team?.id != null && Number.isFinite(team.result?.gameWins))
      .map((team) => [team.id, team.result.gameWins]));
    if (wins.size === 0) continue;
    for (const game of match?.games || []) {
      if (game?.id != null && !winsByGameId.has(game.id)
        && String(game.state).toLowerCase() === 'completed') {
        winsByGameId.set(game.id, wins);
      }
    }
  }
  return winsByGameId;
}

/**
 * eventDetails/getLive 녹화본은 세트 상태 변경이 window 프레임보다 일찍 캡처될 수 있다.
 * 재생에서는 window의 실제 시작·종료 시각을 기준으로 상태를 다시 맞춰, 세트 사이
 * 대기 구간에 다음 세트가 활성 게임으로 선택되지 않게 한다.
 */
function synchronizeGameStatesWithReplayTimeline(body, run, mappedMs) {
  const normalized = cloneJson(body);
  const synchronizeMatch = (match) => {
    const games = match?.games;
    if (!Array.isArray(games)) return;
    let latestCompletedGame = null;
    for (const game of games) {
      const startMs = run.gameStartMsByOriginalId.get(game?.id);
      if (!Number.isFinite(startMs)) continue;
      const finishMs = run.gameFinishMsByOriginalId.get(game.id);
      if (mappedMs < startMs) {
        game.state = 'unstarted';
      } else if (Number.isFinite(finishMs) && mappedMs >= finishMs) {
        game.state = 'completed';
        if (latestCompletedGame == null
          || finishMs > run.gameFinishMsByOriginalId.get(latestCompletedGame.id)) {
          latestCompletedGame = game;
        }
      } else {
        game.state = 'inProgress';
      }
    }

    // 공식 승수 응답은 종종 다음 세트 시작 또는 매치 종료 뒤에야 온다. 그 지연을
    // 화면·정산까지 전파하지 않도록, 각 세트가 끝난 window 시점에 그 세트의 공식 승수를 적용한다.
    const officialWins = latestCompletedGame == null
      ? null : run.gameResultWinsByOriginalId.get(latestCompletedGame.id);
    if (officialWins == null || !Array.isArray(match.teams)) return;
    for (const team of match.teams) {
      const gameWins = officialWins.get(team?.id);
      if (Number.isFinite(gameWins)) {
        team.result = { ...(team.result || {}), gameWins };
      }
    }
  };

  const events = normalized?.data?.schedule?.events;
  if (Array.isArray(events)) {
    for (const event of events) {
      synchronizeMatch(event?.match);
    }
  }
  synchronizeMatch(normalized?.data?.event?.match);
  return normalized;
}

/** 아직 어느 세트도 시작하지 않은 eventDetails만 시작 전 메타데이터로 재사용한다. */
function findPreMatchEventDetails(series) {
  for (let index = 0; index < series.length; index++) {
    const entry = series.entryAt(index);
    const games = entry?.body?.data?.event?.match?.games;
    if (Array.isArray(games) && games.length > 0
      && games.every((game) => String(game?.state).toLowerCase() === 'unstarted')) {
      return entry;
    }
  }
  return null;
}

/** 로드된 모든 픽스처를 통틀어 가장 이른 시각 — 재생 시계의 기준점(t=0)이다. */
function earliestCapturedAtMs(fixtures) {
  const series = allFixtureSeries(fixtures).filter((item) => item.length > 0);
  if (series.length === 0) {
    throw new Error('픽스처가 비어 있다 — getLive.jsonl / eventDetails.jsonl / window.jsonl / details.jsonl 중 최소 하나는 있어야 한다');
  }
  return Math.min(...series.map((item) => item.capturedAtMsAt(0)));
}

/** JSONL 전체에서 가장 늦은 응답 시각 — 재생 타임라인의 끝이다. */
function latestCapturedAtMs(fixtures) {
  const series = allFixtureSeries(fixtures).filter((item) => item.length > 0);
  return Math.max(...series.map((item) => item.capturedAtMsAt(item.length - 1)));
}

function allFixtureSeries(fixtures) {
  return [
    fixtures.getLive,
    fixtures.eventDetails,
    fixtures.getSchedule,
    fixtures.getStandings,
    ...fixtures.window.values(),
    ...fixtures.details.values(),
  ].filter(Boolean);
}

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(payload);
}

/** livestats가 아직 프레임을 제공하지 않는 구간. 폴러가 재시도할 수 있도록 본문 없이 응답한다. */
function sendNoContent(res) {
  res.writeHead(204);
  res.end();
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
    `(프레임 시계도 재생 시간축으로 변환)`);
  console.log(`[replay] 배속 ${run.speed}x 로 재생 시작 (기준 시각 ${new Date(run.originMs).toISOString()})`);
  console.log(`[replay] 실행 ID: ${run.runId} · matchId: ${runSummary(run).matchId}`);
  console.log('[replay] 프레임 시계도 재생 배속을 따름 — 20배속이면 게임 시간도 실제 초당 20초 진행');

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
      // 사용자가 고른 배속을 유지해야, 새 테스트 경기의 첫 schedule 폴링도 같은 시간축으로 시작한다.
      run = createReplayRun(fixtures, run.speed);
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
    const synchronizeGameStates = (body) => synchronizeGameStatesWithReplayTimeline(body, run, mappedMs);
    const shiftSchedule = (body) => transform(synchronizeGameStates(
      shiftScheduleBody(body, mappedMs, Date.now(), run.speed),
    ));
    const shiftFrames = (body, originalGameId, advanceClock = false, series = null) => {
      const replayFrameBody = advanceClock && series != null
        ? appendInterpolatedWindowFrame(body, series, mappedMs)
        : body;
      const shifted = shiftFramesBody(replayFrameBody, run, originalGameId);
      if (advanceClock) {
        advanceLatestFrameToReplayClock(shifted, run, originalGameId, mappedMs, true);
      }
      return transform(shifted);
    };
    const shiftDetailsFrames = (body, originalGameId) => {
      const shifted = shiftFramesBody(body, run, originalGameId);
      advanceLatestFrameToReplayClock(shifted, run, originalGameId, mappedMs, false);
      return transform(shifted);
    };
    // 게임 시작 시각 탐색도 화면용 window 프레임과 같은 시간축을 써야
    // gameTimeSeconds(frameTs - gameStartTs)가 음수가 되지 않는다.
    const shiftGameStartFrames = (body, originalGameId) => shiftFrames(body, originalGameId);

    let match;
    if (pathname === '/getLive') {
      const entry = pickAtOrBefore(fixtures.getLive, mappedMs);
      // replay 시작 전의 getLive는 아직 경기 전 상태다. 미래의 inProgress 응답을
      // 미리 반환하면 첫 세트 배팅이 이미 진행 중인 경기로 동기화돼 곧바로 마감된다.
      return entry
        ? respondPicked(res, pathname, entry, shiftSchedule)
        : sendJson(res, 200, EMPTY_SCHEDULE_BODY);
    }
    if (pathname === '/getEventDetails') {
      const entry = pickAtOrBefore(fixtures.eventDetails, mappedMs)
        || fixtures.preMatchEventDetails;
      return respondPicked(res, pathname, entry, (body) => transform(synchronizeGameStates(body)));
    }
    if (pathname === '/getSchedule') {
      if (!fixtures.getSchedule) return sendJson(res, 200, EMPTY_SCHEDULE_BODY);
      const entry = pickAtOrBefore(fixtures.getSchedule, mappedMs);
      return entry
        ? respondPicked(res, pathname, entry, shiftSchedule)
        : sendJson(res, 200, EMPTY_SCHEDULE_BODY);
    }
    if (pathname === '/getStandings') {
      if (!fixtures.getStandings) return sendJson(res, 200, EMPTY_STANDINGS_BODY);
      const entry = pickAtOrBefore(fixtures.getStandings, mappedMs);
      return entry
        ? respondPicked(res, pathname, entry)
        : sendJson(res, 200, EMPTY_STANDINGS_BODY);
    }
    if ((match = pathname.match(/^\/window\/(.+)$/))) {
      const replayGameId = decodeURIComponent(match[1]);
      const originalGameId = run.originalGameIdByReplayId.get(replayGameId);
      const series = fixtures.window.get(originalGameId);
      if (!series) return sendJson(res, 404, { error: `녹화된 window 데이터 없음: ${replayGameId}` });
      const isGameStartProbe = url.searchParams.has('clutchGameStartProbe');
      // startingTime 파라미터가 없는 호출은 LiveStatsClient.getGameStartTimestamp() 전용 —
      // "게임 시작 첫 프레임"을 기대하므로 재생 시각과 무관하게 최초 항목을 돌려줘야 한다.
      if (!url.searchParams.has('startingTime')) {
        return respondPicked(
          res,
          pathname,
          series.entryAt(0),
          (body) => isGameStartProbe ? shiftGameStartFrames(body, originalGameId) : shiftFrames(body, originalGameId),
        );
      }
      return respondMergedFrames(res, pathname, 'window', originalGameId, series, mappedMs,
        (body) => isGameStartProbe
          ? shiftGameStartFrames(body, originalGameId)
          : shiftFrames(body, originalGameId, true, series));
    }
    if ((match = pathname.match(/^\/details\/(.+)$/))) {
      const replayGameId = decodeURIComponent(match[1]);
      const originalGameId = run.originalGameIdByReplayId.get(replayGameId);
      const series = fixtures.details.get(originalGameId);
      if (!series) return sendJson(res, 404, { error: `녹화된 details 데이터 없음: ${replayGameId}` });
      return respondMergedFrames(res, pathname, 'details', originalGameId, series, mappedMs,
        (body) => shiftDetailsFrames(body, originalGameId));
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
    // 실제 livestats도 게임 시작 전에는 204를 줄 수 있다. 404를 쓰면 백엔드는
    // "통계 미제공"으로 누적 판단할 수 있으므로, 아직 녹화 프레임이 없는 경우는 204다.
    if (!merged) return sendNoContent(res);

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
