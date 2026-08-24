#!/usr/bin/env node
'use strict';

/**
 * 실제 애플리케이션 폴링 로그를 replay-server.js 계약의 fixture로 스트리밍 변환한다.
 *
 * 입력 한 줄 형식:
 *   {
 *     "calledAt":"...",
 *     "api":"getLive|window|...",
 *     "request":{"path":"/window/<gameId>", ...},
 *     "status":200,
 *     "response":{...}
 *   }
 *
 * 사용법:
 *   node convert-recorded-fixture.js --in <recorded.jsonl> --match-id <matchId>
 *     [--out ~/Desktop/clutch-replay-recordings/<matchId>]
 *
 * 대용량 로그를 메모리에 전부 올리지 않는다. 첫 번째 순회에서 대상 세트 ID를 찾고,
 * 두 번째 순회에서 해당 매치·세트의 성공 응답만 endpoint별 JSONL로 기록한다.
 */

const { once } = require('events');
const fs = require('fs');
const os = require('os');
const path = require('path');
const readline = require('readline');

function parseArgs(argv) {
    const args = { in: null, matchId: null, out: null };
    for (let index = 0; index < argv.length; index++) {
        const argument = argv[index];
        if (argument === '--in') args.in = argv[++index];
        else if (argument === '--match-id') args.matchId = argv[++index];
        else if (argument === '--out') args.out = argv[++index];
        else if (argument === '--help' || argument === '-h') {
            printUsage();
            process.exit(0);
        }
    }

    if (!args.in || !args.matchId) {
        printUsage();
        process.exit(1);
    }
    args.out ??= path.join(os.homedir(), 'Desktop', 'clutch-replay-recordings', args.matchId);
    return args;
}

function printUsage() {
    console.error('사용법: node convert-recorded-fixture.js --in <recorded.jsonl> --match-id <matchId> [--out <fixture-dir>]');
}

function createLineReader(input) {
    return readline.createInterface({
        input: fs.createReadStream(input, { encoding: 'utf8' }),
        crlfDelay: Infinity,
    });
}

function parseRecord(line, lineNumber) {
    try {
        return JSON.parse(line);
    } catch (error) {
        throw new Error(`입력 ${lineNumber}번째 줄을 JSON으로 읽을 수 없습니다: ${error.message}`);
    }
}

function isSuccessfulResponse(record) {
    return record.status === 200
            && record.response != null
            && typeof record.response === 'object'
            && !Array.isArray(record.response)
            && typeof record.calledAt === 'string';
}

function isTargetEvent(event, matchId) {
    return event?.id === matchId || event?.match?.id === matchId;
}

function targetScheduleBody(response, matchId) {
    const schedule = response?.data?.schedule;
    if (!Array.isArray(schedule?.events)) {
        return null;
    }

    const events = schedule.events.filter((event) => isTargetEvent(event, matchId));
    if (events.length === 0) {
        return null;
    }

    // 단일 매치 fixture에는 후속 페이지를 담지 않으므로, 없는 페이지 토큰을 따라가지 않게 한다.
    return {
        ...response,
        data: {
            ...response.data,
            schedule: {
                ...schedule,
                pages: null,
                events,
            },
        },
    };
}

function emptyLiveBody(response) {
    const schedule = response?.data?.schedule;
    if (!Array.isArray(schedule?.events)) {
        return null;
    }

    return {
        ...response,
        data: {
            ...response.data,
            schedule: {
                ...schedule,
                pages: null,
                events: [],
            },
        },
    };
}

function gameIdFromPath(requestPath) {
    const matched = requestPath?.match(/^\/(window|details)\/([^/?]+)$/);
    return matched ? { endpoint: matched[1], gameId: matched[2] } : null;
}

/** 대상 매치의 세트 ID와 getLive 시작 전 마지막 응답 시각을 찾는 첫 번째 순회. */
async function scanInput(input, matchId) {
    const gameIds = new Set();
    let firstTargetLiveAt = null;
    let lines = 0;

    for await (const line of createLineReader(input)) {
        lines++;
        if (!line) {
            continue;
        }
        const record = parseRecord(line, lines);
        if (!isSuccessfulResponse(record)) {
            continue;
        }

        if (record.api === 'getLive') {
            const events = record.response?.data?.schedule?.events;
            if (Array.isArray(events) && events.some((event) => isTargetEvent(event, matchId))) {
                if (firstTargetLiveAt == null || record.calledAt < firstTargetLiveAt) {
                    firstTargetLiveAt = record.calledAt;
                }
            }
            continue;
        }

        if (record.api !== 'getEventDetails') {
            continue;
        }

        const match = record.response?.data?.event?.match;
        if (record.request?.query?.id !== matchId && match?.id !== matchId) {
            continue;
        }
        for (const game of match?.games || []) {
            if (typeof game?.id === 'string' && game.id.trim() !== '') {
                gameIds.add(game.id);
            }
        }
    }

    if (gameIds.size === 0) {
        throw new Error(`매치 ${matchId}의 getEventDetails 응답 또는 세트 ID를 찾지 못했습니다.`);
    }
    if (firstTargetLiveAt == null) {
        throw new Error(`매치 ${matchId}의 getLive 응답을 찾지 못했습니다.`);
    }

    return { gameIds, firstTargetLiveAt, lines };
}

class JsonlWriters {
    constructor(directory) {
        this.directory = directory;
        this.streams = new Map();
        this.stats = new Map();
    }

    async write(endpoint, record) {
        let stream = this.streams.get(endpoint);
        if (!stream) {
            stream = fs.createWriteStream(path.join(this.directory, `${endpoint}.jsonl`), {
                encoding: 'utf8',
            });
            this.streams.set(endpoint, stream);
        }

        const line = `${JSON.stringify(record)}\n`;
        if (!stream.write(line)) {
            await once(stream, 'drain');
        }

        const current = this.stats.get(endpoint) || { records: 0, bytes: 0 };
        current.records++;
        current.bytes += Buffer.byteLength(line);
        this.stats.set(endpoint, current);
    }

    async close() {
        await Promise.all([...this.streams.values()].map(async (stream) => {
            stream.end();
            await once(stream, 'finish');
        }));
    }
}

/** 대상 매치·세트의 성공 응답만 fixture 파일에 기록하는 두 번째 순회. */
async function convert(input, matchId, scan, writers) {
    let lines = 0;
    let skipped = 0;
    let bootstrapLive = null;

    for await (const line of createLineReader(input)) {
        lines++;
        if (!line) {
            continue;
        }
        const record = parseRecord(line, lines);
        if (!isSuccessfulResponse(record)) {
            skipped++;
            continue;
        }

        const baseRecord = { capturedAt: record.calledAt, body: record.response };
        if (record.api === 'getLive') {
            const body = targetScheduleBody(record.response, matchId);
            if (body != null) {
                await writers.write('getLive', { ...baseRecord, body });
            } else if (record.calledAt < scan.firstTargetLiveAt) {
                // getLive는 실제로 라이브가 된 뒤에만 대상 경기를 반환한다. replay 서버가
                // 첫 응답을 과거 시점에도 반환하지 않도록 직전의 빈 라이브 스냅샷을 남긴다.
                const empty = emptyLiveBody(record.response);
                if (empty != null && (bootstrapLive == null || record.calledAt > bootstrapLive.capturedAt)) {
                    bootstrapLive = { capturedAt: record.calledAt, body: empty };
                }
            }
            continue;
        }

        if (record.api === 'getSchedule') {
            const body = targetScheduleBody(record.response, matchId);
            if (body != null) {
                await writers.write('getSchedule', { ...baseRecord, body });
            } else {
                skipped++;
            }
            continue;
        }

        if (record.api === 'getStandings') {
            await writers.write('getStandings', baseRecord);
            continue;
        }

        if (record.api === 'getEventDetails') {
            const responseMatchId = record.response?.data?.event?.match?.id;
            if (record.request?.query?.id === matchId || responseMatchId === matchId) {
                await writers.write('eventDetails', baseRecord);
            } else {
                skipped++;
            }
            continue;
        }

        const route = gameIdFromPath(record.request?.path);
        if (route && scan.gameIds.has(route.gameId)) {
            await writers.write(route.endpoint, {
                capturedAt: record.calledAt,
                gameId: route.gameId,
                body: record.response,
            });
        } else {
            skipped++;
        }
    }

    if (bootstrapLive != null) {
        // 입력 순서가 timestamp와 다르더라도 replay-server.js가 capturedAt으로 정렬한다.
        await writers.write('getLive', bootstrapLive);
    }

    return { lines, skipped };
}

function assertRequiredEndpoints(stats) {
    const required = ['getLive', 'eventDetails', 'window', 'details'];
    const missing = required.filter((endpoint) => !stats.has(endpoint));
    if (missing.length > 0) {
        throw new Error(`변환 결과에 필수 endpoint가 없습니다: ${missing.join(', ')}`);
    }
}

function formatMegabytes(bytes) {
    return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

async function main() {
    const args = parseArgs(process.argv.slice(2));
    const input = path.resolve(args.in);
    const output = path.resolve(args.out);
    const outputParent = path.dirname(output);
    const outputName = path.basename(output);
    const temporary = path.join(outputParent, `.${outputName}.tmp-${process.pid}`);

    if (!fs.existsSync(input)) {
        throw new Error(`입력 파일이 없습니다: ${input}`);
    }
    if (fs.existsSync(output)) {
        throw new Error(`출력 경로가 이미 있습니다. 원본 fixture를 덮어쓰지 않습니다: ${output}`);
    }
    if (fs.existsSync(temporary)) {
        throw new Error(`임시 출력 경로가 이미 있습니다: ${temporary}`);
    }

    console.log(`[convert-recorded] 대상 매치 탐색: ${args.matchId}`);
    const scan = await scanInput(input, args.matchId);
    console.log(`[convert-recorded] 입력 ${scan.lines}줄, 세트 ${[...scan.gameIds].join(', ')}`);

    fs.mkdirSync(outputParent, { recursive: true });
    fs.mkdirSync(temporary);
    const writers = new JsonlWriters(temporary);

    try {
        const result = await convert(input, args.matchId, scan, writers);
        await writers.close();
        assertRequiredEndpoints(writers.stats);
        fs.renameSync(temporary, output);

        const totalBytes = [...writers.stats.values()].reduce((total, stat) => total + stat.bytes, 0);
        console.log(`[convert-recorded] 입력 ${result.lines}줄 처리, ${result.skipped}줄 제외`);
        for (const [endpoint, stat] of [...writers.stats.entries()].sort()) {
            console.log(`[convert-recorded] ${endpoint}.jsonl — ${stat.records}줄, ${formatMegabytes(stat.bytes)}`);
        }
        console.log(`[convert-recorded] 완료 — ${formatMegabytes(totalBytes)}, ${output}`);
    } catch (error) {
        await writers.close().catch(() => undefined);
        fs.rmSync(temporary, { recursive: true, force: true });
        throw error;
    }
}

main().catch((error) => {
    console.error(`[convert-recorded] 실패: ${error.message}`);
    process.exitCode = 1;
});
