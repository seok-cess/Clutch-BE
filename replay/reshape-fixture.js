#!/usr/bin/env node
'use strict';

/**
 * 실제 경기 replay fixture를 테스트하기 쉬운 시간축으로 다시 만든다.
 *
 * 승패·킬·오브젝트·선수 조합은 그대로 보존하고 다음만 바꾼다.
 * - 첫 세트 전 대기 시간
 * - 세트별 실제 진행 시간
 * - 세트 시작 시 각 선수 골드(500)
 *
 * 사용법:
 *   node replay/reshape-fixture.js --dir replay/fixtures/sample-match-bo3-001 --replace
 *   node replay/reshape-fixture.js --dir <source> --out <destination>
 */

const { once } = require('node:events');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const readline = require('node:readline');

const JSONL_FILES = [
  'getLive.jsonl',
  'eventDetails.jsonl',
  'getSchedule.jsonl',
  'getStandings.jsonl',
  'window.jsonl',
  'details.jsonl',
];
const DEFAULT_INITIAL_WAIT_SECONDS = 10 * 60;
const DEFAULT_SET_DURATION_SECONDS = 25 * 60;
const DEFAULT_BETWEEN_SET_SECONDS = 60;
const FINAL_RESULT_DELAY_SECONDS = 120;

function parseArgs(argv) {
  const args = {
    dir: null,
    output: null,
    replace: false,
    initialWaitSeconds: DEFAULT_INITIAL_WAIT_SECONDS,
    setDurationSeconds: DEFAULT_SET_DURATION_SECONDS,
    betweenSetSeconds: DEFAULT_BETWEEN_SET_SECONDS,
  };
  for (let i = 0; i < argv.length; i++) {
    const argument = argv[i];
    if (argument === '--dir') args.dir = argv[++i];
    else if (argument === '--out') args.output = argv[++i];
    else if (argument === '--replace') args.replace = true;
    else if (argument === '--initial-wait-minutes') args.initialWaitSeconds = Number(argv[++i]) * 60;
    else if (argument === '--set-duration-minutes') args.setDurationSeconds = Number(argv[++i]) * 60;
    else if (argument === '--between-set-minutes') args.betweenSetSeconds = Number(argv[++i]) * 60;
    else if (argument === '--help' || argument === '-h') {
      printUsage();
      process.exit(0);
    } else {
      throw new Error(`알 수 없는 인자입니다: ${argument}`);
    }
  }
  if (!args.dir || (args.output && args.replace)) {
    printUsage();
    process.exit(1);
  }
  for (const [name, seconds] of Object.entries({
    'initial-wait-minutes': args.initialWaitSeconds,
    'set-duration-minutes': args.setDurationSeconds,
    'between-set-minutes': args.betweenSetSeconds,
  })) {
    if (!Number.isFinite(seconds) || seconds < 0) {
      throw new Error(`--${name} 값은 0 이상의 숫자여야 합니다.`);
    }
  }
  if (args.setDurationSeconds === 0) {
    throw new Error('--set-duration-minutes 값은 0보다 커야 합니다.');
  }
  return args;
}

function printUsage() {
  console.error('사용법: node replay/reshape-fixture.js --dir <fixture-dir> [--out <destination>|--replace]');
  console.error('       [--initial-wait-minutes 10] [--set-duration-minutes 25] [--between-set-minutes 1]');
}

function createLineReader(filePath) {
  return readline.createInterface({
    input: fs.createReadStream(filePath, { encoding: 'utf8' }),
    crlfDelay: Infinity,
  });
}

function parseMs(value) {
  const ms = Date.parse(value);
  return Number.isNaN(ms) ? null : ms;
}

function iso(ms) {
  return new Date(ms).toISOString();
}

function gamesOf(body) {
  const event = body?.data?.event;
  if (Array.isArray(event?.match?.games)) return event.match.games;
  if (Array.isArray(event?.games)) return event.games;
  const scheduleEvent = body?.data?.schedule?.events?.[0];
  if (Array.isArray(scheduleEvent?.match?.games)) return scheduleEvent.match.games;
  return [];
}

function noteMinimum(map, key, ms) {
  if (key == null || ms == null) return;
  const current = map.get(key);
  if (current == null || ms < current) map.set(key, ms);
}

function noteMaximum(map, key, ms) {
  if (key == null || ms == null) return;
  const current = map.get(key);
  if (current == null || ms > current) map.set(key, ms);
}

/** fixture 첫 순회: 세트 시간·승리 상태는 읽기만 하고, 골드 기준값을 수집한다. */
async function inspectFixture(dir) {
  let originMs = Number.POSITIVE_INFINITY;
  let latestCapturedAtMs = Number.NEGATIVE_INFINITY;
  const firstLiveAtByGame = new Map();
  const firstInGameFrameAtByGame = new Map();
  const finishedFrameAtByGame = new Map();
  const firstGoldByGameAndPlayer = new Map();

  for (const file of JSONL_FILES) {
    const filePath = path.join(dir, file);
    if (!fs.existsSync(filePath)) continue;
    for await (const line of createLineReader(filePath)) {
      if (!line) continue;
      const record = JSON.parse(line);
      const capturedAtMs = parseMs(record.capturedAt);
      if (capturedAtMs != null) {
        originMs = Math.min(originMs, capturedAtMs);
        latestCapturedAtMs = Math.max(latestCapturedAtMs, capturedAtMs);
      }

      if (file === 'getLive.jsonl' || file === 'eventDetails.jsonl') {
        for (const game of gamesOf(record.body)) {
          if (game?.state === 'inProgress') {
            noteMinimum(firstLiveAtByGame, game.id, capturedAtMs);
          }
        }
      }

      if (file !== 'window.jsonl') continue;
      for (const frame of record.body?.frames || []) {
        const frameMs = parseMs(frame?.rfc460Timestamp);
        if (frame?.gameState === 'in_game') {
          noteMinimum(firstInGameFrameAtByGame, record.gameId, frameMs);
        }
        if (frame?.gameState === 'finished') {
          noteMaximum(finishedFrameAtByGame, record.gameId, frameMs);
        }
        // paused/loading 프레임은 실제 시작 자원값이 아니다. 이를 기준값으로 삼으면
        // 첫 in_game 스냅샷에 +500G가 한 번 더 붙어 다음 세트가 1,000G로 시작한다.
        if (frame?.gameState !== 'in_game') continue;
        for (const team of [frame?.blueTeam, frame?.redTeam]) {
          for (const participant of team?.participants || []) {
            const gold = participant?.totalGold;
            if (record.gameId == null || participant?.participantId == null || !Number.isFinite(gold) || frameMs == null) {
              continue;
            }
            const key = `${record.gameId}\u0000${participant.participantId}`;
            const current = firstGoldByGameAndPlayer.get(key);
            if (current == null || frameMs < current.frameMs) {
              firstGoldByGameAndPlayer.set(key, { frameMs, gold });
            }
          }
        }
      }
    }
  }

  if (!Number.isFinite(originMs) || firstInGameFrameAtByGame.size === 0) {
    throw new Error('fixture에서 세트 window 프레임을 찾지 못했습니다.');
  }
  const games = [...firstInGameFrameAtByGame.entries()]
    .map(([gameId, firstInGameFrameAtMs]) => ({
      gameId,
      firstInGameFrameAtMs,
      firstLiveAtMs: firstLiveAtByGame.get(gameId) ?? firstInGameFrameAtMs,
      finishedFrameAtMs: finishedFrameAtByGame.get(gameId),
    }))
    .sort((left, right) => left.firstInGameFrameAtMs - right.firstInGameFrameAtMs);
  if (games.some((game) => game.finishedFrameAtMs == null)) {
    throw new Error('모든 세트에 finished window 프레임이 있어야 시간을 재구성할 수 있습니다.');
  }
  return { originMs, latestCapturedAtMs, games, firstGoldByGameAndPlayer };
}

function addAnchor(targets, sourceMs, targetMs) {
  if (!Number.isFinite(sourceMs) || !Number.isFinite(targetMs)) return;
  const existing = targets.get(sourceMs);
  if (existing != null && existing !== targetMs) {
    throw new Error(`같은 원본 시각에 서로 다른 목표 시각을 지정할 수 없습니다: ${iso(sourceMs)}`);
  }
  targets.set(sourceMs, targetMs);
}

function createTimelinePlan(inspected, args) {
  const anchors = new Map();
  addAnchor(anchors, inspected.originMs, inspected.originMs);

  inspected.games.forEach((game, index) => {
    const setStartMs = inspected.originMs + (args.initialWaitSeconds
      + index * (args.setDurationSeconds + args.betweenSetSeconds)) * 1000;
    const setEndMs = setStartMs + args.setDurationSeconds * 1000;
    game.targetStartMs = setStartMs;
    game.targetEndMs = setEndMs;
    // getLive가 inProgress가 되는 시점과 첫 stats 프레임을 모두 세트 시작에 맞춘다.
    addAnchor(anchors, game.firstLiveAtMs, setStartMs);
    addAnchor(anchors, game.firstInGameFrameAtMs, setStartMs);
    addAnchor(anchors, game.finishedFrameAtMs, setEndMs);
  });

  const finalEndMs = inspected.games.at(-1).targetEndMs + FINAL_RESULT_DELAY_SECONDS * 1000;
  addAnchor(anchors, inspected.latestCapturedAtMs, finalEndMs);
  const points = [...anchors.entries()]
    .map(([sourceMs, targetMs]) => ({ sourceMs, targetMs }))
    .sort((left, right) => left.sourceMs - right.sourceMs);

  function mapMs(sourceMs) {
    if (!Number.isFinite(sourceMs)) return sourceMs;
    if (sourceMs <= points[0].sourceMs) return points[0].targetMs;
    for (let index = 1; index < points.length; index++) {
      const previous = points[index - 1];
      const next = points[index];
      if (sourceMs <= next.sourceMs) {
        if (next.sourceMs === previous.sourceMs) return next.targetMs;
        const ratio = (sourceMs - previous.sourceMs) / (next.sourceMs - previous.sourceMs);
        return Math.round(previous.targetMs + (next.targetMs - previous.targetMs) * ratio);
      }
    }
    return points.at(-1).targetMs;
  }

  return { ...inspected, mapMs };
}

function setMatchStartTime(body, firstSetStartMs) {
  const events = body?.data?.schedule?.events;
  if (!Array.isArray(events)) return;
  for (const event of events) {
    if (typeof event?.startTime === 'string') {
      event.startTime = iso(firstSetStartMs);
    }
  }
}

function transformGold(gameId, participant, field, plan) {
  if (gameId == null || participant?.participantId == null || !Number.isFinite(participant[field])) return;
  const base = plan.firstGoldByGameAndPlayer.get(`${gameId}\u0000${participant.participantId}`)?.gold;
  if (!Number.isFinite(base)) return;
  participant[field] = 500 + Math.max(0, participant[field] - base);
}

function transformWindowFrame(gameId, frame, plan) {
  if (typeof frame?.rfc460Timestamp === 'string') {
    const ms = parseMs(frame.rfc460Timestamp);
    if (ms != null) frame.rfc460Timestamp = iso(plan.mapMs(ms));
  }
  for (const team of [frame?.blueTeam, frame?.redTeam]) {
    if (team == null || !Array.isArray(team.participants)) continue;
    for (const participant of team.participants) {
      transformGold(gameId, participant, 'totalGold', plan);
    }
    const total = team.participants.reduce((sum, participant) =>
      sum + (Number.isFinite(participant?.totalGold) ? participant.totalGold : 0), 0);
    if (team.participants.length > 0) {
      team.totalGold = total;
    }
  }
}

function transformDetailsFrame(gameId, frame, plan) {
  if (typeof frame?.rfc460Timestamp === 'string') {
    const ms = parseMs(frame.rfc460Timestamp);
    if (ms != null) frame.rfc460Timestamp = iso(plan.mapMs(ms));
  }
  for (const participant of frame?.participants || []) {
    transformGold(gameId, participant, 'totalGoldEarned', plan);
  }
}

function transformRecord(record, file, plan) {
  const out = JSON.parse(JSON.stringify(record));
  const frames = out.body?.frames;
  if (Array.isArray(frames) && (file === 'window.jsonl' || file === 'details.jsonl')) {
    let latestFrameMs = null;
    for (const frame of frames) {
      const frameMs = parseMs(frame?.rfc460Timestamp);
      if (frameMs != null && (latestFrameMs == null || frameMs > latestFrameMs)) latestFrameMs = frameMs;
      if (file === 'window.jsonl') transformWindowFrame(out.gameId, frame, plan);
      else transformDetailsFrame(out.gameId, frame, plan);
    }
    if (latestFrameMs != null) {
      out.capturedAt = iso(plan.mapMs(latestFrameMs));
    }
  } else {
    const capturedAtMs = parseMs(out.capturedAt);
    if (capturedAtMs != null) out.capturedAt = iso(plan.mapMs(capturedAtMs));
  }
  if (file === 'getLive.jsonl' || file === 'getSchedule.jsonl') {
    setMatchStartTime(out.body, plan.games[0].targetStartMs);
  }
  return out;
}

async function writeLine(stream, line) {
  if (!stream.write(`${line}\n`)) await once(stream, 'drain');
}

async function transformFile(source, destination, file, plan) {
  if (!fs.existsSync(source)) return;
  const output = fs.createWriteStream(destination, { encoding: 'utf8' });
  try {
    for await (const line of createLineReader(source)) {
      if (!line) continue;
      const transformed = transformRecord(JSON.parse(line), file, plan);
      await writeLine(output, JSON.stringify(transformed));
    }
  } finally {
    output.end();
    await once(output, 'finish');
  }
}

async function buildFixture(sourceDir, outputDir, plan) {
  await fsp.mkdir(outputDir, { recursive: true });
  const readme = path.join(sourceDir, 'README.md');
  if (fs.existsSync(readme)) {
    await fsp.copyFile(readme, path.join(outputDir, 'README.md'));
  }
  for (const file of JSONL_FILES) {
    await transformFile(path.join(sourceDir, file), path.join(outputDir, file), file, plan);
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const sourceDir = path.resolve(args.dir);
  if (!fs.statSync(sourceDir).isDirectory()) {
    throw new Error(`fixture 디렉터리를 찾을 수 없습니다: ${sourceDir}`);
  }
  const inspected = await inspectFixture(sourceDir);
  const plan = createTimelinePlan(inspected, args);
  const temporaryDir = args.replace
    ? `${sourceDir}.reshape-${process.pid}`
    : path.resolve(args.output ?? `${sourceDir}-reshaped`);
  if (fs.existsSync(temporaryDir)) {
    throw new Error(`출력 경로가 이미 있습니다: ${temporaryDir}`);
  }

  await buildFixture(sourceDir, temporaryDir, plan);
  if (args.replace) {
    const backupDir = `${sourceDir}.backup-${process.pid}`;
    await fsp.rename(sourceDir, backupDir);
    try {
      await fsp.rename(temporaryDir, sourceDir);
      await fsp.rm(backupDir, { recursive: true, force: true });
    } catch (error) {
      if (!fs.existsSync(sourceDir) && fs.existsSync(backupDir)) {
        await fsp.rename(backupDir, sourceDir);
      }
      throw error;
    }
  }

  console.log(`[reshape] 첫 세트 대기 ${args.initialWaitSeconds / 60}분 · 세트당 ${args.setDurationSeconds / 60}분 · 세트 사이 ${args.betweenSetSeconds / 60}분`);
  for (const [index, game] of plan.games.entries()) {
    console.log(`[reshape] ${index + 1}세트 ${game.gameId}: ${iso(game.targetStartMs)} ~ ${iso(game.targetEndMs)}`);
  }
}

main().catch((error) => {
  console.error(`[reshape] ${error.stack || error.message}`);
  process.exit(1);
});
