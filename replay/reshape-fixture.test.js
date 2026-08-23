'use strict';

const assert = require('node:assert/strict');
const { execFile } = require('node:child_process');
const { once } = require('node:events');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { promisify } = require('node:util');
const test = require('node:test');

const execFileAsync = promisify(execFile);
const SCRIPT = path.join(__dirname, 'reshape-fixture.js');

function line(capturedAt, body, gameId) {
  return `${JSON.stringify({ capturedAt, ...(gameId ? { gameId } : {}), body })}\n`;
}

function team(id, gold) {
  return {
    totalGold: gold * 5,
    participants: Array.from({ length: 5 }, (_, index) => ({
      participantId: id + index,
      totalGold: gold,
    })),
  };
}

function details(gold) {
  return Array.from({ length: 10 }, (_, index) => ({
    participantId: index + 1,
    totalGoldEarned: gold,
  }));
}

test('rebuilds wait, set duration and opening gold without changing game result bodies', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'clutch-reshape-'));
  const source = path.join(root, 'source');
  const output = path.join(root, 'output');
  const gameId = 'g1';
  const start = '2026-01-01T00:00:20.000Z';
  const finish = '2026-01-01T00:01:20.000Z';
  const eventDetails = { data: { event: { match: { games: [{ id: gameId, number: 1, state: 'completed' }] } } } };

  try {
    await fsp.mkdir(source);
    await fsp.writeFile(path.join(source, 'getSchedule.jsonl'), line(
      '2026-01-01T00:00:00.000Z',
      { data: { schedule: { events: [{ startTime: '2026-01-01T00:00:20.000Z' }] } } },
    ));
    await fsp.writeFile(path.join(source, 'getLive.jsonl'), line(
      start,
      { data: { schedule: { events: [{ match: { games: [{ id: gameId, state: 'inProgress' }] } }] } } },
    ));
    await fsp.writeFile(path.join(source, 'eventDetails.jsonl'), line('2026-01-01T00:01:40.000Z', eventDetails));
    await fsp.writeFile(path.join(source, 'window.jsonl'), [
      line(start, { frames: [{ rfc460Timestamp: start, gameState: 'in_game', blueTeam: team(1, 900), redTeam: team(6, 900) }] }, gameId),
      line(finish, { frames: [{ rfc460Timestamp: finish, gameState: 'finished', blueTeam: team(1, 1400), redTeam: team(6, 1400) }] }, gameId),
    ].join(''));
    await fsp.writeFile(path.join(source, 'details.jsonl'), [
      line(start, { frames: [{ rfc460Timestamp: start, participants: details(900) }] }, gameId),
      line(finish, { frames: [{ rfc460Timestamp: finish, participants: details(1400) }] }, gameId),
    ].join(''));

    await execFileAsync(process.execPath, [SCRIPT, '--dir', source, '--out', output]);

    const windows = (await fsp.readFile(path.join(output, 'window.jsonl'), 'utf8'))
      .trim().split('\n').map(JSON.parse);
    const firstFrame = windows[0].body.frames[0];
    const lastFrame = windows[1].body.frames[0];
    assert.equal(firstFrame.rfc460Timestamp, '2026-01-01T00:10:00.000Z');
    assert.equal(lastFrame.rfc460Timestamp, '2026-01-01T00:35:00.000Z');
    assert.equal(firstFrame.blueTeam.totalGold, 2500);
    assert.deepEqual(firstFrame.blueTeam.participants.map((player) => player.totalGold), [500, 500, 500, 500, 500]);

    const firstDetails = JSON.parse((await fsp.readFile(path.join(output, 'details.jsonl'), 'utf8')).split('\n')[0]);
    assert.deepEqual(firstDetails.body.frames[0].participants.map((player) => player.totalGoldEarned),
      Array.from({ length: 10 }, () => 500));
    const schedule = JSON.parse((await fsp.readFile(path.join(output, 'getSchedule.jsonl'), 'utf8')).trim());
    assert.equal(schedule.body.data.schedule.events[0].startTime, '2026-01-01T00:10:00.000Z');

    const shapedEventDetails = JSON.parse((await fsp.readFile(path.join(output, 'eventDetails.jsonl'), 'utf8')).trim());
    assert.deepEqual(shapedEventDetails.body, eventDetails);
  } finally {
    await fsp.rm(root, { recursive: true, force: true });
  }
});
