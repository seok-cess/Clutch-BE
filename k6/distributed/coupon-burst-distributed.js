import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

// =============================================================================
// DISTRIBUTED_PHASE=prepare: 공유 이벤트와 회차를 한 번 생성·오픈한다.
// DISTRIBUTED_PHASE=load: 두 실행기가 같은 이벤트 회차에 분할 부하를 보낸다.
// DISTRIBUTED_PHASE=verify: 두 실행기 종료 후 최종 발급 수량을 검증한다.
// =============================================================================

const phase = distributedPhase(__ENV.DISTRIBUTED_PHASE);
const baseUrl = (__ENV.BASE_URL || 'http://100.101.76.93:8080')
  .replace(/\/$/, '');
const couponQuantity = positiveInteger(
  __ENV.COUPON_QUANTITY,
  undefined,
  'COUPON_QUANTITY',
);
const virtualUsers = phase === 'verify'
  ? null
  : positiveInteger(__ENV.COUPON_VUS, undefined, 'COUPON_VUS');
const userIdStart = phase === 'load'
  ? positiveInteger(__ENV.USER_ID_START, 900001, 'USER_ID_START')
  : null;
const matchId = phase === 'prepare'
  ? positiveInteger(__ENV.MATCH_ID, 316, 'MATCH_ID')
  : null;
const claimWindowSeconds = phase === 'prepare'
  ? positiveInteger(__ENV.CLAIM_WINDOW_SECONDS, 600, 'CLAIM_WINDOW_SECONDS')
  : null;
const persistenceTimeoutSeconds = phase === 'load'
  ? positiveNumber(
    __ENV.PERSISTENCE_TIMEOUT_SECONDS,
    600,
    'PERSISTENCE_TIMEOUT_SECONDS',
  )
  : null;
const finalVerificationTimeoutSeconds = phase === 'verify'
  ? positiveNumber(
    __ENV.FINAL_VERIFICATION_TIMEOUT_SECONDS,
    120,
    'FINAL_VERIFICATION_TIMEOUT_SECONDS',
  )
  : null;
const claimRequestTimeout = __ENV.CLAIM_REQUEST_TIMEOUT || '1m';
const rampUpSeconds = phase === 'load'
  ? positiveInteger(__ENV.RAMP_UP_SECONDS, 60, 'RAMP_UP_SECONDS')
  : null;
const holdSeconds = phase === 'load'
  ? positiveInteger(__ENV.HOLD_SECONDS, 30, 'HOLD_SECONDS')
  : null;
const verifyIndividualPersistence = phase === 'load'
  ? booleanValue(
    __ENV.VERIFY_INDIVIDUAL_PERSISTENCE,
    false,
    'VERIFY_INDIVIDUAL_PERSISTENCE',
  )
  : false;
const couponName = __ENV.COUPON_NAME || '[K6] 10%';
const sharedEvent = phase === 'load' ? configuredEvent() : null;
const expectedClaimCount = phase === 'load'
  ? positiveInteger(
    __ENV.EXPECTED_CLAIM_COUNT,
    undefined,
    'EXPECTED_CLAIM_COUNT',
  )
  : null;
const verificationEventId = phase === 'verify'
  ? positiveInteger(__ENV.COUPON_EVENT_ID, undefined, 'COUPON_EVENT_ID')
  : null;

if (phase !== 'verify' && couponQuantity >= virtualUsers) {
  throw new Error('COUPON_QUANTITY는 COUPON_VUS보다 작아야 품절 경쟁을 검증할 수 있습니다.');
}

const claimSuccesses = new Counter('coupon_claim_success_total');
const claimSoldOuts = new Counter('coupon_claim_sold_out_total');
const unexpectedClaims = new Counter('coupon_claim_unexpected_total');
const expectedClaimResults = new Rate('coupon_claim_expected');
const expectedClaims = new Counter('coupon_claim_expected_total');
const persistedCoupons = new Counter('coupon_persisted_total');
const persistenceFailures = new Counter('coupon_persistence_failed_total');
const claimFailures = new Counter('coupon_claim_failure');
const claimTransportFailures = new Counter('coupon_claim_transport_failure_total');
const claimDuration = new Trend('coupon_claim_duration', true);

const loadThresholds = {
  checks: ['rate>0.99'],
  http_req_failed: ['rate<0.01'],
  'http_req_duration{endpoint:claim,expected_response:true}': ['p(95)<5000'],
  coupon_claim_expected: ['rate>0.99'],
  coupon_claim_expected_total: [`count==${expectedClaimCount}`],
  coupon_claim_unexpected_total: ['count==0'],
  coupon_claim_transport_failure_total: ['count==0'],
};

if (verifyIndividualPersistence) {
  loadThresholds.coupon_persistence_failed_total = ['count==0'];
}

export const options = phase === 'load'
  ? {
    scenarios: {
      claimers: {
        executor: 'ramping-vus',
        exec: 'claimCoupon',
        startVUs: 0,
        stages: [
          { duration: `${rampUpSeconds}s`, target: virtualUsers },
          { duration: `${holdSeconds}s`, target: virtualUsers },
        ],
        gracefulRampDown: '30s',
        tags: { flow: 'coupon-claimer' },
      },
    },
    thresholds: loadThresholds,
  }
  : {
    vus: 1,
    iterations: 1,
  };

export function setup() {
  if (phase === 'prepare') {
    return prepareSharedEvent();
  }
  if (phase === 'load') {
    console.log(
      `공유 회차 부하 시작: event=${sharedEvent.couponEventId}, `
        + `occurrence=${sharedEvent.couponEventOccurrenceId}, users=${virtualUsers}, `
        + `expectedClaims=${expectedClaimCount}, rampUp=${rampUpSeconds}s`,
    );
    return sharedEvent;
  }
  return null;
}

export default function () {
  if (phase === 'verify') {
    verifyFinalIssuedQuantity();
  }
}

export function claimCoupon(data) {
  // ramping-vus는 같은 VU가 함수를 반복 호출하므로 각 VU의 첫 요청만 전송한다.
  if (exec.vu.iterationInScenario > 0) {
    sleep(rampUpSeconds + holdSeconds + 30);
    return;
  }

  // idInTest는 execution segment가 적용된 두 실행기 전체에서 중복되지 않는다.
  const userId = userIdStart + exec.vu.idInTest - 1;
  const response = http.post(
    `${baseUrl}/api/v1/coupon-events/${data.couponEventId}`
      + `/occurrences/${data.couponEventOccurrenceId}/claims`,
    null,
    {
      headers: {
        Accept: 'application/json',
        'X-User-Id': String(userId),
      },
      responseCallback: http.expectedStatuses(201, 409),
      timeout: claimRequestTimeout,
      tags: { endpoint: 'claim', name: 'POST /coupon-events/:id/occurrences/:id/claims' },
    },
  );

  const transportFailure = response.status === 0;
  claimTransportFailures.add(transportFailure ? 1 : 0);
  if (!transportFailure) {
    claimDuration.add(response.timings.duration);
  }
  const code = jsonValue(response, 'code');
  if (response.status >= 500) {
    claimFailures.add(1, {
      status: String(response.status),
      error_code: code ?? 'NO_CODE',
    });
    console.error(
      `쿠폰 신청 오류: userId=${userId}, status=${response.status}, `
        + `code=${code ?? 'NO_CODE'}, body=${response.body}`,
    );
  }

  const success = response.status === 201;
  const soldOut = response.status === 409
    && (code === 'COUPON_STOCK_EXHAUSTED'
      || code === 'COUPON_EVENT_ITEM_NOT_AVAILABLE');
  const expected = success || soldOut;

  claimSuccesses.add(success ? 1 : 0);
  claimSoldOuts.add(soldOut ? 1 : 0);
  unexpectedClaims.add(expected ? 0 : 1);
  expectedClaimResults.add(expected);
  expectedClaims.add(expected ? 1 : 0);
  check(response, {
    'claim result is success or sold out': () => expected,
  });

  if (success && verifyIndividualPersistence) {
    const persisted = waitForPersistedCoupon(userId, data.couponEventId);
    persistedCoupons.add(persisted ? 1 : 0);
    persistenceFailures.add(persisted ? 0 : 1);
    check(persisted, {
      'issued coupon is persisted': (value) => value,
    });
  }
}

function prepareSharedEvent() {
  const couponType = findCouponType();
  const eventName = `K6 분산 부하테스트_${todayInKorea()}_${virtualUsers}`;
  const response = http.post(
    `${baseUrl}/api/v1/admin/coupon-events`,
    JSON.stringify({
      esportsMatchId: matchId,
      eventName,
      issueMode: 'SINGLE_FIRST_COME',
      triggerType: 'MANUAL_TEST',
      claimWindowSeconds,
      items: [
        {
          couponTypeId: couponType.couponTypeId,
          quantity: couponQuantity,
          openOffsetSeconds: 0,
        },
      ],
    }),
    jsonParams('POST /admin/coupon-events', 'event-create'),
  );
  const created = check(response, {
    'coupon event is created': (res) => res.status === 201,
    'coupon event starts READY': (res) => jsonValue(res, 'eventStatus') === 'READY',
  });
  if (!created) {
    fail(`쿠폰 이벤트 생성 실패: status=${response.status}, body=${response.body}`);
  }

  const couponEventId = response.json('couponEventId');
  const occurrence = openCouponEvent(couponEventId);
  const prepared = {
    couponEventId,
    couponEventOccurrenceId: occurrence.couponEventOccurrenceId,
  };
  console.log(
    `공유 회차 준비 완료: COUPON_EVENT_ID=${prepared.couponEventId} `
      + `COUPON_EVENT_OCCURRENCE_ID=${prepared.couponEventOccurrenceId} `
      + `users=${virtualUsers}, stock=${couponQuantity}`,
  );
  return prepared;
}

function verifyFinalIssuedQuantity() {
  const deadline = Date.now() + finalVerificationTimeoutSeconds * 1000;
  let response;
  let issuedQuantity = 0;

  do {
    response = http.get(
      `${baseUrl}/api/v1/admin/coupon-events/${verificationEventId}`,
      jsonParams('GET /admin/coupon-events/:id', 'final-event-detail'),
    );
    issuedQuantity = Number(jsonValue(response, 'issuedQuantity'));
    if (response.status === 200 && issuedQuantity === couponQuantity) {
      break;
    }
    sleep(1);
  } while (Date.now() < deadline);

  const verified = check(response, {
    'final event detail is returned': (res) => res.status === 200,
    'final issued quantity equals stock': () => issuedQuantity === couponQuantity,
  });
  if (!verified) {
    fail(
      `최종 발급 수량 검증 실패: event=${verificationEventId}, `
        + `issued=${issuedQuantity}, expected=${couponQuantity}`,
    );
  }
  console.log(
    `최종 확인: event=${verificationEventId}, issued=${issuedQuantity}/${couponQuantity}`,
  );
}

function openCouponEvent(couponEventId) {
  const response = http.post(
    `${baseUrl}/api/v1/admin/coupon-events/${couponEventId}`
      + '/occurrences/manual-open',
    null,
    jsonParams(
      'POST /admin/coupon-events/:id/occurrences/manual-open',
      'event-open',
    ),
  );
  const opened = check(response, {
    'admin opens coupon event': (res) => res.status === 201,
    'opened occurrence is OPEN': (res) => jsonValue(res, 'occurrenceStatus') === 'OPEN',
    'opened occurrence has an id': (res) => Number(
      jsonValue(res, 'couponEventOccurrenceId'),
    ) > 0,
    'opened event has expected stock': (res) => Number(jsonValue(res, 'remainingQuantity'))
      === couponQuantity,
  });
  if (!opened) {
    fail(`쿠폰 이벤트 오픈 실패: status=${response.status}, body=${response.body}`);
  }
  return response.json();
}

function findCouponType() {
  const response = http.get(
    `${baseUrl}/api/v1/admin/coupon-types?status=ACTIVE`,
    jsonParams('GET /admin/coupon-types', 'coupon-type-list'),
  );
  if (response.status !== 200) {
    fail(`쿠폰 종류 조회 실패: status=${response.status}, body=${response.body}`);
  }

  let payload;
  try {
    payload = response.json();
  } catch (_) {
    fail(`쿠폰 종류 응답이 JSON이 아닙니다: ${response.body}`);
  }
  const couponTypes = Array.isArray(payload) ? payload : payload?.couponTypes;
  if (!Array.isArray(couponTypes)) {
    fail(`쿠폰 종류 응답에서 couponTypes 배열을 찾지 못했습니다: ${response.body}`);
  }

  const couponType = couponTypes.find((candidate) => (
    candidate.status === 'ACTIVE'
      && String(candidate.couponName).trim() === couponName.trim()
      && candidate.discountType === 'RATE'
      && Number(candidate.discountValue) === 10
  ));
  if (!couponType) {
    fail(`활성 상태인 '${couponName}' 정률 10% 쿠폰을 찾지 못했습니다.`);
  }
  return couponType;
}

function waitForPersistedCoupon(userId, couponEventId) {
  const deadline = Date.now() + persistenceTimeoutSeconds * 1000;
  while (Date.now() < deadline) {
    const response = http.get(`${baseUrl}/api/users/me/coupons?size=100`, {
      headers: {
        Accept: 'application/json',
        'X-User-Id': String(userId),
      },
      timeout: claimRequestTimeout,
      tags: { endpoint: 'my-coupons', name: 'GET /users/me/coupons' },
    });
    if (response.status === 200) {
      const items = jsonValue(response, 'items');
      if (Array.isArray(items)
          && items.some((coupon) => Number(coupon.couponEventId) === Number(couponEventId))) {
        return true;
      }
    }
    sleep(0.5);
  }
  return false;
}

function configuredEvent() {
  return {
    couponEventId: positiveInteger(__ENV.COUPON_EVENT_ID, undefined, 'COUPON_EVENT_ID'),
    couponEventOccurrenceId: positiveInteger(
      __ENV.COUPON_EVENT_OCCURRENCE_ID,
      undefined,
      'COUPON_EVENT_OCCURRENCE_ID',
    ),
  };
}

function distributedPhase(rawValue) {
  const value = String(rawValue || '').trim().toLowerCase();
  if (value === 'prepare' || value === 'load' || value === 'verify') {
    return value;
  }
  throw new Error('DISTRIBUTED_PHASE는 prepare, load, verify 중 하나여야 합니다.');
}

function jsonParams(name, endpoint) {
  return {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    tags: { endpoint, name },
  };
}

function jsonValue(response, selector) {
  try {
    return response.json(selector);
  } catch (_) {
    return null;
  }
}

function todayInKorea() {
  const now = new Date(Date.now() + 9 * 60 * 60 * 1000);
  const year = now.getUTCFullYear();
  const month = String(now.getUTCMonth() + 1).padStart(2, '0');
  const day = String(now.getUTCDate()).padStart(2, '0');
  return `${year}${month}${day}`;
}

function positiveInteger(rawValue, defaultValue, name) {
  const value = rawValue === undefined ? defaultValue : Number(rawValue);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name}은 1 이상의 정수여야 합니다.`);
  }
  return value;
}

function positiveNumber(rawValue, defaultValue, name) {
  const value = rawValue === undefined ? defaultValue : Number(rawValue);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name}은 0보다 큰 숫자여야 합니다.`);
  }
  return value;
}

function booleanValue(rawValue, defaultValue, name) {
  if (rawValue === undefined) {
    return defaultValue;
  }
  const normalized = String(rawValue).trim().toLowerCase();
  if (normalized === 'true') {
    return true;
  }
  if (normalized === 'false') {
    return false;
  }
  throw new Error(`${name}은 true 또는 false여야 합니다.`);
}
