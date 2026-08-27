#!/usr/bin/env node
'use strict';

/**
 * replay fixture 에 펜타킬을 심는다.
 *
 * 실제 녹화(GEN–KT)에는 펜타킬이 없다. 30초 안에 한 선수가 얻은 최대 킬이 3이라
 * 쿠폰 PENTAKILL 트리거를 이 fixture 로는 시연할 수 없다.
 *
 * 그래서 원본을 건드리지 않고 사본을 만들어, 지정한 세트의 지정한 시점부터
 * 한 선수의 kills 를 5 올린다. 팀 totalKills 도 같이 올려 앞뒤가 맞게 한다.
 *
 * 킬은 한 프레임에 몰아넣지 않고 KILL_SPREAD_SECONDS 에 걸쳐 하나씩 올린다.
 * 실제 펜타킬이 그렇고, 감지기도 시간창 누적으로 판정하기 때문이다.
 *
 * 사용법:
 *   node replay/inject-pentakill.js \
 *     --src replay/fixtures/sample-match-bo3-001 \
 *     --out replay/fixtures/sample-match-pentakill \
 *     [--game 1] [--at 600] [--participant 3]
 */

const { once } = require('node:events');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const readline = require('node:readline');

/** window/details 외 파일은 그대로 복사한다 — 킬 수치는 window 에만 있다 */
const COPY_FILES = [
  'getLive.jsonl',
  'eventDetails.jsonl',
  'getSchedule.jsonl',
  'getStandings.jsonl',
  'details.jsonl',
  'README.md',
];

/** 5킬을 이 시간에 걸쳐 하나씩 올린다 (초) */
const KILL_SPREAD_SECONDS = 20;

/** 펜타킬 킬 수 */
const PENTAKILL_KILLS = 5;

function parseArgs(argv) {
  const args = { game: 1, at: 600, participant: 3 };
  for (let i = 2; i < argv.length; i += 1) {
    const key = argv[i];
    const value = argv[i + 1];
    if (key === '--src') { args.src = value; i += 1; }
    else if (key === '--out') { args.out = value; i += 1; }
    else if (key === '--game') { args.game = Number(value); i += 1; }
    else if (key === '--at') { args.at = Number(value); i += 1; }
    else if (key === '--participant') { args.participant = Number(value); i += 1; }
  }
  if (!args.src || !args.out) {
    console.error('사용법: node replay/inject-pentakill.js --src <dir> --out <dir> [--game 1] [--at 600] [--participant 3]');
    process.exit(1);
  }
  return args;
}

/** window.jsonl 에 등장하는 세트 ID 를 등장 순서대로 모은다 */
async function collectGameIds(windowPath) {
  const ids = [];
  const seen = new Set();
  const rl = readline.createInterface({
    input: fs.createReadStream(windowPath),
    crlfDelay: Infinity,
  });
  for await (const line of rl) {
    if (!line.trim()) continue;
    const gameId = JSON.parse(line).body?.esportsGameId;
    if (gameId && !seen.has(gameId)) {
      seen.add(gameId);
      ids.push(gameId);
    }
  }
  return ids;
}

/** 대상 세트의 첫 프레임 시각 (경과 초의 기준점) */
async function findGameStart(windowPath, targetGameId) {
  const rl = readline.createInterface({
    input: fs.createReadStream(windowPath),
    crlfDelay: Infinity,
  });
  for await (const line of rl) {
    if (!line.trim()) continue;
    const body = JSON.parse(line).body;
    if (body?.esportsGameId !== targetGameId) continue;
    const first = (body.frames || [])[0];
    if (first?.rfc460Timestamp) {
      rl.close();
      return Date.parse(first.rfc460Timestamp);
    }
  }
  return null;
}

/**
 * 프레임 시각에 따라 몇 킬을 더해야 하는지.
 * 시작 전이면 0, 퍼지는 구간에서는 비례해서, 끝난 뒤에는 5 로 유지한다.
 */
function bonusKillsAt(frameMs, startMs) {
  if (frameMs < startMs) return 0;
  const elapsed = (frameMs - startMs) / 1000;
  if (elapsed >= KILL_SPREAD_SECONDS) return PENTAKILL_KILLS;
  const step = KILL_SPREAD_SECONDS / PENTAKILL_KILLS;
  return Math.min(PENTAKILL_KILLS, Math.floor(elapsed / step) + 1);
}

async function main() {
  const args = parseArgs(process.argv);
  const srcWindow = path.join(args.src, 'window.jsonl');
  const gameIds = await collectGameIds(srcWindow);
  const targetGameId = gameIds[args.game - 1];
  if (!targetGameId) {
    console.error(`세트 ${args.game} 을 찾지 못했습니다. fixture 의 세트: ${gameIds.length}개`);
    process.exit(1);
  }

  const gameStartMs = await findGameStart(srcWindow, targetGameId);
  if (gameStartMs == null) {
    console.error('세트 시작 프레임을 찾지 못했습니다.');
    process.exit(1);
  }
  const pentakillStartMs = gameStartMs + args.at * 1000;

  await fsp.mkdir(args.out, { recursive: true });
  for (const name of COPY_FILES) {
    const from = path.join(args.src, name);
    if (fs.existsSync(from)) {
      await fsp.copyFile(from, path.join(args.out, name));
    }
  }

  const out = fs.createWriteStream(path.join(args.out, 'window.jsonl'));
  const rl = readline.createInterface({
    input: fs.createReadStream(srcWindow),
    crlfDelay: Infinity,
  });

  let patchedFrames = 0;
  for await (const line of rl) {
    if (!line.trim()) continue;
    const entry = JSON.parse(line);
    const body = entry.body;

    if (body?.esportsGameId === targetGameId) {
      for (const frame of body.frames || []) {
        const frameMs = Date.parse(frame.rfc460Timestamp);
        const bonus = bonusKillsAt(frameMs, pentakillStartMs);
        if (bonus === 0) continue;

        for (const teamKey of ['blueTeam', 'redTeam']) {
          const team = frame[teamKey];
          const target = (team?.participants || [])
            .find((p) => p.participantId === args.participant);
          if (!target) continue;
          target.kills = (target.kills || 0) + bonus;
          // 팀 합계도 올려야 스코어보드가 선수 합과 어긋나지 않는다
          if (typeof team.totalKills === 'number') {
            team.totalKills += bonus;
          }
          patchedFrames += 1;
        }
      }
    }

    if (!out.write(`${JSON.stringify(entry)}\n`)) {
      await once(out, 'drain');
    }
  }
  out.end();
  await once(out, 'finish');

  console.log(`세트 ${args.game} (${targetGameId}) 참가자 ${args.participant} 에 펜타킬 주입`);
  console.log(`  시작: 세트 시작 후 ${args.at}초, ${KILL_SPREAD_SECONDS}초에 걸쳐 5킬`);
  console.log(`  수정한 프레임: ${patchedFrames}개`);
  console.log(`  출력: ${args.out}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
