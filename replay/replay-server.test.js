'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const test = require('node:test');
const { once } = require('node:events');

const REPOSITORY_ROOT = path.resolve(__dirname, '..');
const REPLAY_SERVER = path.join(__dirname, 'replay-server.js');

function jsonLine(capturedAt, body, gameId) {
  return JSON.stringify({ capturedAt, ...(gameId ? { gameId } : {}), body });
}

function reservePort() {
  return new Promise((resolve, reject) => {
    const server = http.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      server.close((error) => error ? reject(error) : resolve(port));
    });
  });
}

function get(port, pathname) {
  return new Promise((resolve, reject) => {
    const request = http.get({ hostname: '127.0.0.1', port, path: pathname }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.once('end', () => resolve({
        status: response.statusCode,
        body: Buffer.concat(chunks).toString(),
      }));
    });
    request.once('error', reject);
  });
}

function post(port, pathname) {
  return new Promise((resolve, reject) => {
    const request = http.request({ hostname: '127.0.0.1', port, path: pathname, method: 'POST' }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.once('end', () => resolve({
        status: response.statusCode,
        body: Buffer.concat(chunks).toString(),
      }));
    });
    request.once('error', reject);
    request.end();
  });
}

async function startReplayServer(fixtureDirectory, port, speed = 20) {
  const child = spawn(process.execPath, [REPLAY_SERVER, '--dir', fixtureDirectory, '--port', String(port), '--speed', String(speed)], {
    cwd: REPOSITORY_ROOT,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let output = '';
  const ready = new Promise((resolve, reject) => {
    const collect = (chunk) => {
      output += chunk.toString();
      if (output.includes('스텁 서버 대기 중')) {
        resolve();
      }
    };
    child.stdout.on('data', collect);
    child.stderr.on('data', collect);
    child.once('error', reject);
    child.once('exit', (code) => reject(new Error(`replay server exited before ready: ${code}\n${output}`)));
  });
  await ready;
  return child;
}

test('does not expose future live or live-stat data before its capturedAt', async () => {
  const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-replay-'));
  const gameId = 'recorded-game-1';
  const origin = '2026-01-01T00:00:00.000Z';
  const firstFrameAt = '2026-01-01T00:00:20.000Z';
  const secondFrameAt = '2026-01-01T00:00:40.000Z';
  const frame = { rfc460Timestamp: firstFrameAt, gameState: 'in_game' };
  const secondFrame = { rfc460Timestamp: secondFrameAt, gameState: 'in_game' };
  const port = await reservePort();
  let child;

  try {
    fs.writeFileSync(
      path.join(fixtureDirectory, 'getSchedule.jsonl'),
      `${jsonLine(origin, { data: { schedule: { events: [] } } })}\n`,
    );
    fs.writeFileSync(
      path.join(fixtureDirectory, 'getLive.jsonl'),
      `${jsonLine(firstFrameAt, { data: { schedule: { events: [{ id: 'live-event' }] } } })}\n`,
    );
    fs.writeFileSync(
      path.join(fixtureDirectory, 'window.jsonl'),
      `${jsonLine(firstFrameAt, { frames: [frame] }, gameId)}\n`
        + `${jsonLine(secondFrameAt, { frames: [secondFrame] }, gameId)}\n`,
    );
    fs.writeFileSync(
      path.join(fixtureDirectory, 'details.jsonl'),
      `${jsonLine(firstFrameAt, { frames: [frame] }, gameId)}\n`,
    );

    child = await startReplayServer(fixtureDirectory, port);
    const status = await get(port, '/__replay/status');
    assert.equal(status.status, 200);
    const replayGameId = JSON.parse(status.body).matches[0].gameIds[0];

    const liveBefore = await get(port, '/getLive');
    assert.equal(liveBefore.status, 200);
    assert.deepEqual(JSON.parse(liveBefore.body).data.schedule.events, []);
    assert.equal((await get(port, `/window/${replayGameId}?startingTime=now`)).status, 204);
    assert.equal((await get(port, `/details/${replayGameId}?startingTime=now`)).status, 204);

    await new Promise((resolve) => setTimeout(resolve, 1_200));

    const liveAfter = await get(port, '/getLive');
    assert.equal(liveAfter.status, 200);
    assert.equal(JSON.parse(liveAfter.body).data.schedule.events.length, 1);
    const window = await get(port, `/window/${replayGameId}?startingTime=now`);
    assert.equal(window.status, 200);
    const firstGameTime = JSON.parse(window.body).frames.at(-1).gameTimeSeconds;
    assert.ok(firstGameTime >= 0);
    assert.ok(firstGameTime < 20,
      '다음 녹화 프레임 전에도 현재 재생 위치까지만 시계를 진행한다');
    assert.equal((await get(port, `/details/${replayGameId}?startingTime=now`)).status, 200);

    const gameStartProbe = await get(port, `/window/${replayGameId}?clutchGameStartProbe=true`);
    assert.equal(gameStartProbe.status, 200);
    assert.ok(
      Date.parse(JSON.parse(gameStartProbe.body).frames[0].rfc460Timestamp)
        <= Date.parse(JSON.parse(window.body).frames.at(-1).rfc460Timestamp),
      '게임 시작 탐색 프레임은 현재 화면 프레임보다 뒤일 수 없다',
    );

    await new Promise((resolve) => setTimeout(resolve, 1_100));
    const secondWindow = await get(port, `/window/${replayGameId}?startingTime=now`);
    assert.equal(secondWindow.status, 200);
    const firstTimestamp = Date.parse(JSON.parse(window.body).frames.at(-1).rfc460Timestamp);
    const secondTimestamp = Date.parse(JSON.parse(secondWindow.body).frames.at(-1).rfc460Timestamp);
    const secondGameTime = JSON.parse(secondWindow.body).frames.at(-1).gameTimeSeconds;
    assert.ok(secondTimestamp - firstTimestamp >= 900 && secondTimestamp - firstTimestamp <= 1_400,
      '20배속이면 현재 재생 시계가 실제 1초 간격으로 전진해야 한다');
    assert.ok(secondGameTime - firstGameTime >= 18 && secondGameTime - firstGameTime <= 28,
      '20배속에서는 화면 타이머도 실제 1초마다 게임 시간 20초씩 진행해야 한다');
  } finally {
    if (child && !child.killed) {
      child.kill();
      await once(child, 'exit');
    }
    fs.rmSync(fixtureDirectory, { recursive: true, force: true });
  }
});

test('exposes only unstarted event details early so first-set betting can resolve team IDs', async () => {
  const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-replay-prematch-'));
  const origin = '2026-01-01T00:00:00.000Z';
  const detailsAt = '2026-01-01T00:00:20.000Z';
  const port = await reservePort();
  let child;

  try {
    fs.writeFileSync(
      path.join(fixtureDirectory, 'getSchedule.jsonl'),
      `${jsonLine(origin, { data: { schedule: { events: [] } } })}\n`,
    );
    fs.writeFileSync(
      path.join(fixtureDirectory, 'eventDetails.jsonl'),
      `${jsonLine(detailsAt, { data: { event: { match: {
        teams: [{ id: 'team-a' }, { id: 'team-b' }],
        games: [{ id: 'game-1', state: 'unstarted' }],
      } } } })}\n`,
    );

    child = await startReplayServer(fixtureDirectory, port, 1);
    const response = await get(port, '/getEventDetails');

    assert.equal(response.status, 200);
    const match = JSON.parse(response.body).data.event.match;
    assert.deepEqual(match.teams.map((team) => team.id), ['team-a', 'team-b']);
    assert.equal(match.games[0].state, 'unstarted');
  } finally {
    if (child && !child.killed) {
      child.kill();
      await once(child, 'exit');
    }
    fs.rmSync(fixtureDirectory, { recursive: true, force: true });
  }
});

test('keeps the next set unstarted during the replay fixture intermission', async () => {
  const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-replay-intermission-'));
  const firstGameId = 'recorded-game-1';
  const secondGameId = 'recorded-game-2';
  const origin = '2026-01-01T00:00:00.000Z';
  const firstFinishedAt = '2026-01-01T00:00:20.000Z';
  const secondStartedAt = '2026-01-01T00:00:40.000Z';
  const port = await reservePort();
  let child;

  try {
    fs.writeFileSync(
      path.join(fixtureDirectory, 'getSchedule.jsonl'),
      `${jsonLine(origin, { data: { schedule: { events: [] } } })}\n`,
    );
    fs.writeFileSync(
      path.join(fixtureDirectory, 'eventDetails.jsonl'), [
        jsonLine(origin, { data: { event: { match: { games: [
          { id: firstGameId, state: 'inProgress' },
          { id: secondGameId, state: 'unstarted' },
        ] } } } }),
        // 녹화 당시 다음 세트 상태가 너무 일찍 inProgress가 된 응답을 재현한다.
        jsonLine(firstFinishedAt, { data: { event: { match: { games: [
          { id: firstGameId, state: 'completed' },
          { id: secondGameId, state: 'inProgress' },
        ] } } } }),
      ].join('\n') + '\n',
    );
    fs.writeFileSync(
      path.join(fixtureDirectory, 'window.jsonl'), [
        jsonLine(origin, { frames: [{ rfc460Timestamp: origin, gameState: 'in_game' }] }, firstGameId),
        jsonLine(firstFinishedAt, {
          frames: [{ rfc460Timestamp: firstFinishedAt, gameState: 'finished' }],
        }, firstGameId),
        jsonLine(secondStartedAt, {
          frames: [{ rfc460Timestamp: secondStartedAt, gameState: 'in_game' }],
        }, secondGameId),
      ].join('\n') + '\n',
    );

    child = await startReplayServer(fixtureDirectory, port, 20);
    await new Promise((resolve) => setTimeout(resolve, 1_100));

    const intermission = JSON.parse((await get(port, '/getEventDetails')).body).data.event.match.games;
    assert.equal(intermission[0].state, 'completed');
    assert.equal(intermission[1].state, 'unstarted');

    await new Promise((resolve) => setTimeout(resolve, 1_100));
    const secondSet = JSON.parse((await get(port, '/getEventDetails')).body).data.event.match.games;
    assert.equal(secondSet[1].state, 'inProgress');
  } finally {
    if (child && !child.killed) {
      child.kill();
      await once(child, 'exit');
    }
    fs.rmSync(fixtureDirectory, { recursive: true, force: true });
  }
});

test('applies each completed set’s official game score at its window finish', async () => {
  const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-replay-score-'));
  const teamA = 'team-a';
  const teamB = 'team-b';
  const gameIds = ['recorded-game-1', 'recorded-game-2', 'recorded-game-3'];
  const origin = '2026-01-01T00:00:00.000Z';
  const at = (seconds) => new Date(Date.parse(origin) + seconds * 1000).toISOString();
  const match = (wins, states) => ({
    data: { event: { match: {
      teams: [
        { id: teamA, result: { gameWins: wins[0] } },
        { id: teamB, result: { gameWins: wins[1] } },
      ],
      games: gameIds.map((id, index) => ({ id, state: states[index] })),
    } } },
  });
  const port = await reservePort();
  let child;

  try {
    fs.writeFileSync(
      path.join(fixtureDirectory, 'getSchedule.jsonl'),
      `${jsonLine(origin, { data: { schedule: { events: [] } } })}\n`,
    );
    fs.writeFileSync(path.join(fixtureDirectory, 'eventDetails.jsonl'), [
      jsonLine(origin, match([0, 0], ['inProgress', 'unstarted', 'unstarted'])),
      jsonLine(at(20), match([1, 0], ['completed', 'inProgress', 'unstarted'])),
      jsonLine(at(40), match([1, 1], ['completed', 'completed', 'inProgress'])),
      // 실제 녹화에서는 최종 공식 응답이 늦게 와도, 세트 종료 시점에 이 결과를 적용한다.
      jsonLine(at(100), match([2, 1], ['completed', 'completed', 'completed'])),
    ].join('\n') + '\n');
    fs.writeFileSync(path.join(fixtureDirectory, 'window.jsonl'), [
      jsonLine(origin, { frames: [{ rfc460Timestamp: origin, gameState: 'in_game' }] }, gameIds[0]),
      jsonLine(at(20), { frames: [{ rfc460Timestamp: at(20), gameState: 'finished' }] }, gameIds[0]),
      jsonLine(at(25), { frames: [{ rfc460Timestamp: at(25), gameState: 'in_game' }] }, gameIds[1]),
      jsonLine(at(40), { frames: [{ rfc460Timestamp: at(40), gameState: 'finished' }] }, gameIds[1]),
      jsonLine(at(45), { frames: [{ rfc460Timestamp: at(45), gameState: 'in_game' }] }, gameIds[2]),
      jsonLine(at(60), { frames: [{ rfc460Timestamp: at(60), gameState: 'finished' }] }, gameIds[2]),
    ].join('\n') + '\n');

    child = await startReplayServer(fixtureDirectory, port, 20);
    await new Promise((resolve) => setTimeout(resolve, 1_100));
    let response = await get(port, '/getEventDetails');
    assert.deepEqual(JSON.parse(response.body).data.event.match.teams
      .map((team) => team.result.gameWins), [1, 0]);

    await new Promise((resolve) => setTimeout(resolve, 1_100));
    response = await get(port, '/getEventDetails');
    assert.deepEqual(JSON.parse(response.body).data.event.match.teams
      .map((team) => team.result.gameWins), [1, 1]);

    await new Promise((resolve) => setTimeout(resolve, 1_100));
    response = await get(port, '/getEventDetails');
    assert.deepEqual(JSON.parse(response.body).data.event.match.teams
      .map((team) => team.result.gameWins), [2, 1]);
  } finally {
    if (child && !child.killed) {
      child.kill();
      await once(child, 'exit');
    }
    fs.rmSync(fixtureDirectory, { recursive: true, force: true });
  }
});

test('interpolates only gold and CS across a long opening telemetry gap', async () => {
  const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-replay-resources-'));
  const gameId = 'recorded-game-1';
  const origin = '2026-01-01T00:00:00.000Z';
  const later = '2026-01-01T00:02:00.000Z';
  const resourceTeam = (participantStartId, gold, creepScore) => ({
    totalGold: gold * 5,
    participants: Array.from({ length: 5 }, (_, index) => ({
      participantId: participantStartId + index,
      totalGold: gold,
      creepScore,
      kills: 0,
    })),
  });
  const port = await reservePort();
  let child;

  try {
    fs.writeFileSync(
      path.join(fixtureDirectory, 'getSchedule.jsonl'),
      `${jsonLine(origin, { data: { schedule: { events: [] } } })}\n`,
    );
    fs.writeFileSync(path.join(fixtureDirectory, 'window.jsonl'), [
      jsonLine(origin, { frames: [{
        rfc460Timestamp: origin,
        gameState: 'in_game',
        blueTeam: resourceTeam(1, 500, 0),
        redTeam: resourceTeam(6, 500, 0),
      }] }, gameId),
      jsonLine(later, { frames: [{
        rfc460Timestamp: later,
        gameState: 'in_game',
        blueTeam: resourceTeam(1, 1700, 20),
        redTeam: resourceTeam(6, 1700, 20),
      }] }, gameId),
    ].join('\n') + '\n');

    child = await startReplayServer(fixtureDirectory, port, 20);
    const replayGameId = JSON.parse((await get(port, '/__replay/status')).body).matches[0].gameIds[0];
    await new Promise((resolve) => setTimeout(resolve, 450));
    const response = await get(port, `/window/${replayGameId}?startingTime=now`);
    const frame = JSON.parse(response.body).frames.at(-1);

    assert.ok(frame.blueTeam.participants[0].totalGold > 500);
    assert.ok(frame.blueTeam.participants[0].totalGold < 1700);
    assert.ok(frame.blueTeam.participants[0].creepScore > 0);
    assert.ok(frame.blueTeam.participants[0].creepScore < 20);
    assert.equal(frame.blueTeam.participants[0].kills, 0,
      '미래 프레임의 전투 사건은 보간하지 않아야 한다');
  } finally {
    if (child && !child.killed) {
      child.kill();
      await once(child, 'exit');
    }
    fs.rmSync(fixtureDirectory, { recursive: true, force: true });
  }
});

test('reports replay game time at one second per wall-clock second at 1x', async () => {
  const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-replay-timer-'));
  const gameId = 'recorded-game-1';
  const origin = '2026-01-01T00:00:00.000Z';
  const port = await reservePort();
  let child;

  try {
    fs.writeFileSync(
      path.join(fixtureDirectory, 'getSchedule.jsonl'),
      `${jsonLine(origin, { data: { schedule: { events: [] } } })}\n`,
    );
    fs.writeFileSync(
      path.join(fixtureDirectory, 'window.jsonl'), [
        jsonLine(origin, { frames: [{ rfc460Timestamp: origin, gameState: 'in_game' }] }, gameId),
        jsonLine('2026-01-01T00:00:01.000Z', {
          frames: [{ rfc460Timestamp: '2026-01-01T00:00:01.000Z', gameState: 'in_game' }],
        }, gameId),
      ].join('\n') + '\n',
    );

    child = await startReplayServer(fixtureDirectory, port, 1);
    const replayGameId = JSON.parse((await get(port, '/__replay/status')).body).matches[0].gameIds[0];
    const first = await get(port, `/window/${replayGameId}?startingTime=now`);
    assert.equal(JSON.parse(first.body).frames.at(-1).gameTimeSeconds, 0);

    await new Promise((resolve) => setTimeout(resolve, 1_100));
    const second = await get(port, `/window/${replayGameId}?startingTime=now`);
    assert.equal(JSON.parse(second.body).frames.at(-1).gameTimeSeconds, 1,
      '1배속에서는 화면 타이머가 실제 1초마다 게임 시간 1초씩 진행해야 한다');
  } finally {
    if (child && !child.killed) {
      child.kill();
      await once(child, 'exit');
    }
    fs.rmSync(fixtureDirectory, { recursive: true, force: true });
  }
});

test('advances the clock while the next recorded frame is still minutes away', async () => {
  const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-replay-gap-'));
  const gameId = 'recorded-game-1';
  const origin = '2026-01-01T00:00:00.000Z';
  const port = await reservePort();
  let child;

  try {
    fs.writeFileSync(path.join(fixtureDirectory, 'getSchedule.jsonl'),
      `${jsonLine(origin, { data: { schedule: { events: [] } } })}\n`);
    fs.writeFileSync(path.join(fixtureDirectory, 'window.jsonl'), [
      jsonLine(origin, { frames: [{ rfc460Timestamp: origin, gameState: 'in_game' }] }, gameId),
      jsonLine('2026-01-01T00:04:00.000Z', {
        frames: [{ rfc460Timestamp: '2026-01-01T00:04:00.000Z', gameState: 'in_game' }],
      }, gameId),
    ].join('\n') + '\n');

    child = await startReplayServer(fixtureDirectory, port, 1);
    const replayGameId = JSON.parse((await get(port, '/__replay/status')).body).matches[0].gameIds[0];
    const first = JSON.parse((await get(port, `/window/${replayGameId}?startingTime=now`)).body).frames.at(-1);
    await new Promise((resolve) => setTimeout(resolve, 1_100));
    const second = JSON.parse((await get(port, `/window/${replayGameId}?startingTime=now`)).body).frames.at(-1);

    assert.equal(first.gameTimeSeconds, 0);
    assert.equal(second.gameTimeSeconds, 1);
    assert.ok(Date.parse(second.rfc460Timestamp) > Date.parse(first.rfc460Timestamp));
  } finally {
    if (child && !child.killed) {
      child.kill();
      await once(child, 'exit');
    }
    fs.rmSync(fixtureDirectory, { recursive: true, force: true });
  }
});

test('uses the earliest in-game frame timestamp even when JSONL capture order differs', async () => {
  const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-replay-game-start-'));
  const gameId = 'recorded-game-1';
  const origin = '2026-01-01T00:00:00.000Z';
  const firstCapturedFrame = '2026-01-01T00:04:00.000Z';
  const port = await reservePort();
  let child;

  try {
    fs.writeFileSync(
      path.join(fixtureDirectory, 'getSchedule.jsonl'),
      `${jsonLine(origin, { data: { schedule: { events: [] } } })}\n`,
    );
    fs.writeFileSync(
      path.join(fixtureDirectory, 'window.jsonl'), [
        // 수집 순서는 뒤 프레임이 먼저일 수 있다. 게임 시계 기준은 수집 순서가 아니라 프레임 시각이다.
        jsonLine(origin, { frames: [{ rfc460Timestamp: firstCapturedFrame, gameState: 'in_game' }] }, gameId),
        jsonLine('2026-01-01T00:00:05.000Z', {
          frames: [{ rfc460Timestamp: origin, gameState: 'in_game' }],
        }, gameId),
      ].join('\n') + '\n',
    );

    child = await startReplayServer(fixtureDirectory, port, 1);
    const replayGameId = JSON.parse((await get(port, '/__replay/status')).body).matches[0].gameIds[0];
    const response = await get(port, `/window/${replayGameId}?startingTime=now`);

    assert.equal(response.status, 200);
    assert.equal(JSON.parse(response.body).frames.at(-1).gameTimeSeconds, 240,
      '처음 수집된 프레임보다 더 이른 실제 게임 프레임을 0초 기준으로 써야 한다');
  } finally {
    if (child && !child.killed) {
      child.kill();
      await once(child, 'exit');
    }
    fs.rmSync(fixtureDirectory, { recursive: true, force: true });
  }
});

test('keeps frame timestamps monotonic when replay speed changes', async () => {
  const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-replay-speed-'));
  const gameId = 'recorded-game-1';
  const origin = '2026-01-01T00:00:00.000Z';
  const port = await reservePort();
  let child;

  try {
    fs.writeFileSync(
      path.join(fixtureDirectory, 'getSchedule.jsonl'),
      `${jsonLine(origin, { data: { schedule: { events: [] } } })}\n`,
    );
    fs.writeFileSync(
      path.join(fixtureDirectory, 'window.jsonl'), [
        jsonLine('2026-01-01T00:00:01.000Z', {
          frames: [{ rfc460Timestamp: '2026-01-01T00:00:01.000Z', gameState: 'in_game' }],
        }, gameId),
        jsonLine('2026-01-01T00:00:02.000Z', {
          frames: [{ rfc460Timestamp: '2026-01-01T00:00:02.000Z', gameState: 'in_game' }],
        }, gameId),
        jsonLine('2026-01-01T00:00:03.000Z', {
          frames: [{ rfc460Timestamp: '2026-01-01T00:00:03.000Z', gameState: 'in_game' }],
        }, gameId),
      ].join('\n') + '\n',
    );

    child = await startReplayServer(fixtureDirectory, port, 1);
    const replayGameId = JSON.parse((await get(port, '/__replay/status')).body).matches[0].gameIds[0];

    await new Promise((resolve) => setTimeout(resolve, 1_100));
    const before = await get(port, `/window/${replayGameId}?startingTime=now`);
    const beforeTimestamp = Date.parse(JSON.parse(before.body).frames.at(-1).rfc460Timestamp);

    assert.equal((await post(port, '/__replay/speed?value=20')).status, 200);
    await new Promise((resolve) => setTimeout(resolve, 150));
    const after = await get(port, `/window/${replayGameId}?startingTime=now`);
    const afterTimestamp = Date.parse(JSON.parse(after.body).frames.at(-1).rfc460Timestamp);

    assert.ok(afterTimestamp >= beforeTimestamp,
      '배속 변경 뒤 새 프레임의 시각은 이미 표시한 프레임보다 과거가 되면 안 된다');
  } finally {
    if (child && !child.killed) {
      child.kill();
      await once(child, 'exit');
    }
    fs.rmSync(fixtureDirectory, { recursive: true, force: true });
  }
});
