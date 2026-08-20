#!/usr/bin/env node
'use strict';

/**
 * 재생 중인 매치가 끝날 때마다(esports_match.lifecycle_status가 completed로 바뀌면)
 * 그 매치와 관련된 DB 데이터를 자동으로 지운다 — 스텁 서버로 같은 매치를 반복
 * 재생하며 테스트할 때, 매번 수동으로 정리하지 않아도 되게 하기 위한 도구다.
 *
 * 지우는 범위는 이 externalMatchId 하나로 좁게 잡는다 — 백필로 들어온 실제 경기나
 * 다른 유저·쿠폰 데이터는 절대 건드리지 않는다.
 *
 * 사용법:
 *   node auto-reset.js --match sample-match-bo3-001 [--grace-seconds 30] [--poll-seconds 5]
 *
 * 스텁 서버·백엔드와 같이, 별도 터미널에서 계속 띄워두고 쓴다. Ctrl+C 로 멈춘다.
 */

const { execFileSync } = require('child_process');

const MYSQL_CONTAINER = 'clutch-mysql-1';
const MYSQL_USER = 'clutch';
const MYSQL_PASSWORD = 'clutch_local_password';
const MYSQL_DB = 'clutch';

function parseArgs(argv) {
  const args = { match: null, graceSeconds: 30, pollSeconds: 5 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--match') args.match = argv[++i];
    else if (a === '--grace-seconds') args.graceSeconds = Number(argv[++i]);
    else if (a === '--poll-seconds') args.pollSeconds = Number(argv[++i]);
  }
  if (!args.match || !/^[A-Za-z0-9_-]+$/.test(args.match)) {
    console.error('사용법: node auto-reset.js --match <externalMatchId> [--grace-seconds 30] [--poll-seconds 5]');
    console.error('  matchId 는 영문/숫자/-/_ 만 허용한다.');
    process.exit(1);
  }
  return args;
}

function mysql(sql) {
  return execFileSync(
    'docker',
    ['exec', MYSQL_CONTAINER, 'mysql', `-u${MYSQL_USER}`, `-p${MYSQL_PASSWORD}`, MYSQL_DB, '-N', '-e', sql],
    { encoding: 'utf8' },
  );
}

/** 이 매치의 현재 lifecycle_status. 매치 자체가 없으면 null. */
function currentStatus(matchId) {
  const out = mysql(`SELECT lifecycle_status FROM esports_match WHERE external_match_id='${matchId}';`).trim();
  return out.length === 0 ? null : out.split('\n')[0].trim();
}

/** matchId 관련 데이터를 FK 순서대로 전부 지운다. */
function resetMatch(matchId) {
  const sql = `
    DELETE FROM bet_point_transaction WHERE user_bet_id IN (
      SELECT user_bet_id FROM user_bet WHERE betting_event_id IN (
        SELECT betting_event_id FROM betting_event WHERE external_match_id='${matchId}'));
    DELETE FROM user_bet WHERE betting_event_id IN (
      SELECT betting_event_id FROM betting_event WHERE external_match_id='${matchId}');
    DELETE FROM betting_event WHERE external_match_id='${matchId}';
    DELETE FROM watch_point_transaction WHERE esports_match_id IN (
      SELECT esports_match_id FROM esports_match WHERE external_match_id='${matchId}');
    DELETE FROM watch_session WHERE esports_match_id IN (
      SELECT esports_match_id FROM esports_match WHERE external_match_id='${matchId}');
    DELETE FROM game_player_stat WHERE game_id IN (
      SELECT esports_game_id FROM esports_game WHERE match_id IN (
        SELECT esports_match_id FROM esports_match WHERE external_match_id='${matchId}'));
    DELETE FROM game_timeline_point WHERE game_id IN (
      SELECT esports_game_id FROM esports_game WHERE match_id IN (
        SELECT esports_match_id FROM esports_match WHERE external_match_id='${matchId}'));
    DELETE FROM esports_game WHERE match_id IN (
      SELECT esports_match_id FROM esports_match WHERE external_match_id='${matchId}');
    DELETE FROM match_team WHERE match_id IN (
      SELECT esports_match_id FROM esports_match WHERE external_match_id='${matchId}');
    DELETE FROM esports_match WHERE external_match_id='${matchId}';
  `;
  mysql(sql);
}

function timestamp() {
  return new Date().toISOString().slice(11, 19);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  console.log(`[auto-reset] matchId=${args.match} 감시 시작 (완료 감지 후 ${args.graceSeconds}초 대기하고 정리, ${args.pollSeconds}초 간격 폴링)`);
  console.log('[auto-reset] 쿠폰·유저 데이터는 건드리지 않는다 — 이 매치의 경기 데이터만 지운다.');

  // 이미 completed 상태로 시작했다면(이전 실행이 안 지우고 끝났거나) 바로 한 번 정리해서 깨끗하게 시작한다.
  const initial = currentStatus(args.match);
  if (initial === 'completed') {
    console.log(`[${timestamp()}] 시작 시점에 이미 completed 상태 — 먼저 한 번 정리한다`);
    resetMatch(args.match);
  }

  let armed = true; // completed 를 아직 처리 안 한 상태면 true

  const tick = () => {
    try {
      const status = currentStatus(args.match);
      if (status === null) {
        armed = true; // 매치가 없다(정리 직후 또는 아직 시작 전) — 다음 completed 를 기다린다
      } else if (status === 'completed' && armed) {
        armed = false;
        console.log(`[${timestamp()}] 매치 종료 감지 (lifecycle_status=completed) — ${args.graceSeconds}초 뒤 정리`);
        setTimeout(() => {
          try {
            // 유예 시간 동안 상태가 바뀌지 않았는지 다시 확인하고 지운다
            if (currentStatus(args.match) === 'completed') {
              resetMatch(args.match);
              console.log(`[${timestamp()}] 정리 완료 — 다음 재생을 기다린다`);
            }
          } catch (e) {
            console.warn(`[${timestamp()}] 정리 실패, 다음 주기에 재시도: ${e.message}`);
            armed = true;
          }
        }, args.graceSeconds * 1000);
      }
    } catch (e) {
      console.warn(`[${timestamp()}] 상태 조회 실패(DB 연결 확인): ${e.message}`);
    }
  };

  setInterval(tick, args.pollSeconds * 1000);
  tick();
}

main();
