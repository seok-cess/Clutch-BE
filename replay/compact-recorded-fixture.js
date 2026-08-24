#!/usr/bin/env node
'use strict';

/**
 * 변환된 실제 replay fixture를 Git에 올릴 수 있는 크기로 압축한다.
 *
 * window/details API는 한 번의 응답에 최근 수십 초 프레임을 반복해서 담는다. 이 스크립트는
 * 같은 세트·rfc460Timestamp의 프레임은 한 번만 남기고, 남은 프레임은 초당 하나로 샘플링해 JSONL
 * 한 줄씩 기록한다. replay-server.js가 여러 JSONL 항목을 다시 합쳐 반환하므로, 백엔드 입장에서는
 * 초단위 프레임 흐름을 그대로 받는다.
 *
 * 사용법:
 *   node compact-recorded-fixture.js --in <원본-fixture-dir> --out <압축-fixture-dir> --replace
 *     [--frame-interval-seconds 1] [--final-winner-team-id <외부팀ID>]
 *     [--final-result-delay-seconds 120]
 *
 * --final-winner-team-id는 원본 녹화가 마지막 세트 공식 결과 전에 끝난 경우에만 사용한다.
 * 마지막 getEventDetails 응답을 기반으로 미완료 세트 하나를 completed로 바꾸고, 지정 팀의
 * gameWins를 1 올린 보정 응답을 추가한다. 원본 파일은 전혀 수정하지 않는다.
 */

const { once } = require('events');
const fs = require('fs');
const path = require('path');
const readline = require('readline');

const REQUIRED_FILES = ['getLive.jsonl', 'eventDetails.jsonl', 'window.jsonl', 'details.jsonl'];
const OPTIONAL_FILES = ['getSchedule.jsonl', 'getStandings.jsonl'];

function parseArgs(argv) {
  const args = {
    input: null,
    output: null,
    replace: false,
    frameIntervalSeconds: 1,
    finalWinnerTeamId: null,
    finalResultDelaySeconds: 120,
  };

  for (let index = 0; index < argv.length; index++) {
    const argument = argv[index];
    if (argument === '--in') args.input = argv[++index];
    else if (argument === '--out') args.output = argv[++index];
    else if (argument === '--replace') args.replace = true;
    else if (argument === '--frame-interval-seconds') {
      args.frameIntervalSeconds = Number(argv[++index]);
    }
    else if (argument === '--final-winner-team-id') args.finalWinnerTeamId = argv[++index];
    else if (argument === '--final-result-delay-seconds') {
      args.finalResultDelaySeconds = Number(argv[++index]);
    } else if (argument === '--help' || argument === '-h') {
      printUsage();
      process.exit(0);
    } else {
      throw new Error(`알 수 없는 인자입니다: ${argument}`);
    }
  }

  if (!args.input || !args.output) {
    printUsage();
    process.exit(1);
  }
  if (!Number.isFinite(args.finalResultDelaySeconds) || args.finalResultDelaySeconds < 0) {
    throw new Error('--final-result-delay-seconds는 0 이상의 숫자여야 합니다.');
  }
  if (!Number.isFinite(args.frameIntervalSeconds) || args.frameIntervalSeconds <= 0) {
    throw new Error('--frame-interval-seconds는 0보다 큰 숫자여야 합니다.');
  }
  return args;
}

function printUsage() {
  console.error('사용법: node compact-recorded-fixture.js --in <원본-fixture-dir> --out <압축-fixture-dir> --replace [--frame-interval-seconds 1] [--final-winner-team-id <외부팀ID>] [--final-result-delay-seconds 120]');
}

function createLineReader(filePath) {
  return readline.createInterface({
    input: fs.createReadStream(filePath, { encoding: 'utf8' }),
    crlfDelay: Infinity,
  });
}

async function writeLine(stream, value) {
  if (!stream.write(`${JSON.stringify(value)}\n`)) {
    await once(stream, 'drain');
  }
}

async function closeStream(stream) {
  stream.end();
  await once(stream, 'finish');
}

async function compactFrameFile(inputPath, outputPath, frameIntervalSeconds) {
  const stream = fs.createWriteStream(outputPath, { encoding: 'utf8' });
  const seenFrameKeys = new Set();
  const candidatesByGameId = new Map();
  const intervalMs = frameIntervalSeconds * 1000;
  let records = 0;
  let emitted = 0;
  let sourceFrames = 0;
  let duplicateFrames = 0;
  let sampledFrames = 0;

  const emit = async (record) => {
    await writeLine(stream, record);
    emitted++;
  };

  try {
    for await (const line of createLineReader(inputPath)) {
      if (!line) continue;
      records++;
      const record = JSON.parse(line);
      const frames = record.body?.frames;
      if (!Array.isArray(frames) || frames.length === 0) {
        throw new Error(`${inputPath} ${records}번째 줄에 frames가 없습니다.`);
      }

      sourceFrames += frames.length;
      for (let frameIndex = 0; frameIndex < frames.length; frameIndex++) {
        const frame = frames[frameIndex];
        const timestamp = frame?.rfc460Timestamp;
        const gameId = record.gameId ?? '';
        const key = `${gameId}\u0000${typeof timestamp === 'string' ? timestamp : `record:${records}:frame:${frameIndex}`}`;
        if (seenFrameKeys.has(key)) {
          duplicateFrames++;
          continue;
        }
        seenFrameKeys.add(key);

        const compactRecord = {
          ...record,
          body: {
            ...record.body,
            frames: [frame],
          },
        };
        const timestampMs = Date.parse(timestamp);
        // rfc460Timestamp를 읽지 못하는 예외 프레임은 보존한다.
        if (Number.isNaN(timestampMs)) {
          await emit(compactRecord);
          continue;
        }

        const bucket = Math.floor(timestampMs / intervalMs);
        const current = candidatesByGameId.get(gameId);
        if (current == null) {
          candidatesByGameId.set(gameId, { bucket, timestampMs, record: compactRecord });
          continue;
        }
        if (bucket === current.bucket) {
          if (timestampMs >= current.timestampMs) {
            candidatesByGameId.set(gameId, { bucket, timestampMs, record: compactRecord });
          }
          sampledFrames++;
          continue;
        }
        if (bucket > current.bucket) {
          await emit(current.record);
          candidatesByGameId.set(gameId, { bucket, timestampMs, record: compactRecord });
          sampledFrames++;
          continue;
        }

        // 원본 응답이 시간순이 아닌 경우에도 프레임을 버리지 않는다.
        await emit(compactRecord);
      }
    }
    for (const candidate of candidatesByGameId.values()) {
      await emit(candidate.record);
    }
  } finally {
    await closeStream(stream);
  }

  return { records, emitted, sourceFrames, duplicateFrames, sampledFrames };
}

async function copyJsonl(inputPath, outputPath) {
  const stream = fs.createWriteStream(outputPath, { encoding: 'utf8' });
  let records = 0;
  try {
    for await (const line of createLineReader(inputPath)) {
      if (!line) continue;
      await writeLine(stream, JSON.parse(line));
      records++;
    }
  } finally {
    await closeStream(stream);
  }
  return records;
}

async function copyEventDetails(inputPath, outputPath, finalWinnerTeamId, delaySeconds) {
  const stream = fs.createWriteStream(outputPath, { encoding: 'utf8' });
  let records = 0;
  let latestRecord = null;

  try {
    for await (const line of createLineReader(inputPath)) {
      if (!line) continue;
      const record = JSON.parse(line);
      await writeLine(stream, record);
      records++;
      if (latestRecord == null || record.capturedAt > latestRecord.capturedAt) {
        latestRecord = record;
      }
    }

    if (finalWinnerTeamId != null) {
      if (latestRecord == null) {
        throw new Error('마지막 공식 결과를 만들 eventDetails 응답이 없습니다.');
      }
      const finalRecord = buildFinalResultRecord(latestRecord, finalWinnerTeamId, delaySeconds);
      await writeLine(stream, finalRecord);
      records++;
    }
  } finally {
    await closeStream(stream);
  }
  return records;
}

function buildFinalResultRecord(latestRecord, winnerTeamId, delaySeconds) {
  const body = JSON.parse(JSON.stringify(latestRecord.body));
  const match = body?.data?.event?.match;
  const unfinishedGames = match?.games?.filter((game) => game?.state !== 'completed') ?? [];
  if (unfinishedGames.length !== 1) {
    throw new Error(`보정할 미완료 세트는 정확히 하나여야 합니다. 현재 ${unfinishedGames.length}개입니다.`);
  }

  const winner = match?.teams?.find((team) => team?.id === winnerTeamId);
  if (winner == null) {
    throw new Error(`매치 팀에서 승리 팀 ID ${winnerTeamId}를 찾지 못했습니다.`);
  }
  if (typeof winner.result?.gameWins !== 'number') {
    throw new Error(`승리 팀 ${winnerTeamId}의 result.gameWins가 없습니다.`);
  }

  unfinishedGames[0].state = 'completed';
  winner.result.gameWins++;

  const capturedAtMs = Date.parse(latestRecord.capturedAt);
  if (Number.isNaN(capturedAtMs)) {
    throw new Error(`eventDetails capturedAt을 읽을 수 없습니다: ${latestRecord.capturedAt}`);
  }
  return {
    capturedAt: new Date(capturedAtMs + delaySeconds * 1000).toISOString(),
    body,
  };
}

function assertInputDirectory(input) {
  if (!fs.existsSync(input) || !fs.statSync(input).isDirectory()) {
    throw new Error(`입력 fixture 디렉터리가 없습니다: ${input}`);
  }
  const missing = REQUIRED_FILES.filter((file) => !fs.existsSync(path.join(input, file)));
  if (missing.length > 0) {
    throw new Error(`입력 fixture에 필수 파일이 없습니다: ${missing.join(', ')}`);
  }
}

function formatMegabytes(bytes) {
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const input = path.resolve(args.input);
  const output = path.resolve(args.output);
  const temporary = path.join(path.dirname(output), `.${path.basename(output)}.tmp-${process.pid}`);
  assertInputDirectory(input);

  if (fs.existsSync(output) && !args.replace) {
    throw new Error(`출력 경로가 이미 있습니다: ${output} (--replace를 지정하면 교체합니다.)`);
  }
  if (fs.existsSync(temporary)) {
    throw new Error(`임시 출력 경로가 이미 있습니다: ${temporary}`);
  }

  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.mkdirSync(temporary);
  try {
    // fixture 디렉터리에 사람이 작성한 설명이 있다면 --replace 뒤에도 유지한다.
    const existingReadme = path.join(output, 'README.md');
    if (fs.existsSync(existingReadme)) {
      fs.copyFileSync(existingReadme, path.join(temporary, 'README.md'));
    }

    const copied = new Map();
    for (const file of [...REQUIRED_FILES, ...OPTIONAL_FILES]) {
      const source = path.join(input, file);
      if (!fs.existsSync(source)) continue;
      const destination = path.join(temporary, file);
      if (file === 'window.jsonl' || file === 'details.jsonl') {
        const stat = await compactFrameFile(source, destination, args.frameIntervalSeconds);
        copied.set(file, stat);
      } else if (file === 'eventDetails.jsonl') {
        copied.set(file, await copyEventDetails(source, destination, args.finalWinnerTeamId, args.finalResultDelaySeconds));
      } else {
        copied.set(file, await copyJsonl(source, destination));
      }
    }

    if (fs.existsSync(output)) {
      fs.rmSync(output, { recursive: true, force: true });
    }
    fs.renameSync(temporary, output);

    const files = fs.readdirSync(output);
    const totalBytes = files.reduce((total, file) => total + fs.statSync(path.join(output, file)).size, 0);
    console.log(`[compact-recorded] 완료 — ${formatMegabytes(totalBytes)}, ${output}`);
    for (const [file, stat] of copied) {
      if (typeof stat === 'number') {
        console.log(`[compact-recorded] ${file} — ${stat}줄 복사`);
      } else {
        console.log(`[compact-recorded] ${file} — ${stat.records}응답/${stat.sourceFrames}프레임 → ${stat.emitted}프레임 (${stat.duplicateFrames} 중복, ${stat.sampledFrames} 샘플링 제외)`);
      }
    }
  } catch (error) {
    fs.rmSync(temporary, { recursive: true, force: true });
    throw error;
  }
}

main().catch((error) => {
  console.error(`[compact-recorded] 실패: ${error.message}`);
  process.exitCode = 1;
});
