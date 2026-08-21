import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const frontendUrl = (__ENV.FRONTEND_URL || 'http://100.101.76.93:5173')
  .replace(/\/$/, '');
const baseUrl = (__ENV.BASE_URL || 'http://100.101.76.93:8080')
  .replace(/\/$/, '');

const virtualUsers = positiveInteger(__ENV.COUPON_VUS, 100, 'COUPON_VUS');
const couponQuantity = positiveInteger(
  __ENV.COUPON_QUANTITY,
  Math.floor(virtualUsers / 2),
  'COUPON_QUANTITY',
);
const userIdStart = positiveInteger(__ENV.USER_ID_START, 900001, 'USER_ID_START');
const matchId = positiveInteger(__ENV.MATCH_ID, 316, 'MATCH_ID');
const openDelay = __ENV.OPEN_DELAY || '15s';
const pollIntervalSeconds = positiveNumber(
  __ENV.POLL_INTERVAL_SECONDS,
  1.5,
  'POLL_INTERVAL_SECONDS',
);
const eventPollTimeoutSeconds = positiveNumber(
  __ENV.EVENT_POLL_TIMEOUT_SECONDS,
  60,
  'EVENT_POLL_TIMEOUT_SECONDS',
);
const persistenceTimeoutSeconds = positiveNumber(
  __ENV.PERSISTENCE_TIMEOUT_SECONDS,
  30,
  'PERSISTENCE_TIMEOUT_SECONDS',
);
const couponName = __ENV.COUPON_NAME || '[K6] 10%';

if (couponQuantity >= virtualUsers) {
  throw new Error('COUPON_QUANTITY는 COUPON_VUS보다 작아야 품절 경쟁을 검증할 수 있습니다.');
}

const claimSuccesses = new Counter('coupon_claim_success_total');
const claimSoldOuts = new Counter('coupon_claim_sold_out_total');
const unexpectedClaims = new Counter('coupon_claim_unexpected_total');
const expectedClaimResults = new Rate('coupon_claim_expected');
const eventDetected = new Rate('coupon_event_detected');
const persistedCoupons = new Counter('coupon_persisted_total');
const persistenceFailures = new Counter('coupon_persistence_failed_total');
const claimDuration = new Trend('coupon_claim_duration', true);

export const options = {
  scenarios: {
    watchers: {
      executor: 'per-vu-iterations',
      exec: 'watchAndClaim',
      vus: virtualUsers,
      iterations: 1,
      maxDuration: '2m',
      gracefulStop: '5s',
      tags: { flow: 'coupon-watcher' },
    },
    admin_opener: {
      executor: 'shared-iterations',
      exec: 'openCouponEvent',
      startTime: openDelay,
      vus: 1,
      iterations: 1,
      maxDuration: '30s',
      tags: { flow: 'coupon-admin-open' },
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:claim}': ['p(95)<3000'],
    coupon_claim_expected: ['rate>0.99'],
    coupon_event_detected: ['rate>0.99'],
    coupon_claim_success_total: [`count==${couponQuantity}`],
    coupon_claim_sold_out_total: [`count==${virtualUsers - couponQuantity}`],
    coupon_claim_unexpected_total: ['count==0'],
    coupon_persisted_total: [`count==${couponQuantity}`],
    coupon_persistence_failed_total: ['count==0'],
  },
};

export function setup() {
  const couponType = findCouponType();
  const eventName = `K6 부하테스트_${todayInKorea()}_${virtualUsers}`;
  const payload = {
    esportsMatchId: matchId,
    eventName,
    issueMode: 'SINGLE_FIRST_COME',
    triggerType: 'MANUAL_TEST',
    claimWindowSeconds: 600,
    items: [
      {
        couponTypeId: couponType.couponTypeId,
        quantity: couponQuantity,
        openOffsetSeconds: 0,
      },
    ],
  };

  const response = http.post(
    `${baseUrl}/api/v1/admin/coupon-events`,
    JSON.stringify(payload),
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
  console.log(
    `준비 완료: event=${couponEventId}, users=${virtualUsers}, stock=${couponQuantity}, couponType=${couponType.couponTypeId}`,
  );

  return {
    couponEventId,
    eventName,
  };
}

export function watchAndClaim(data) {
  const userId = userIdStart + exec.scenario.iterationInTest;
  const pageResponse = http.get(`${frontendUrl}/sample`, {
    tags: { endpoint: 'sample-page', name: 'GET /sample' },
  });
  check(pageResponse, {
    'sample page is reachable': (res) => res.status === 200,
  });

  const activeEvent = waitForActiveEvent(data.couponEventId);
  const detected = activeEvent !== null;
  eventDetected.add(detected);
  if (!detected) {
    unexpectedClaims.add(1);
    expectedClaimResults.add(false);
    return;
  }

  const response = http.post(
    `${baseUrl}/api/v1/coupon-events/${data.couponEventId}`
      + `/occurrences/${activeEvent.couponEventOccurrenceId}/claims`,
    null,
    {
      headers: {
        Accept: 'application/json',
        'X-User-Id': String(userId),
      },
      responseCallback: http.expectedStatuses(201, 409),
      tags: { endpoint: 'claim', name: 'POST /coupon-events/:id/occurrences/:id/claims' },
    },
  );

  claimDuration.add(response.timings.duration);
  const code = jsonValue(response, 'code');
  const success = response.status === 201;
  const soldOut = response.status === 409
    && (code === 'COUPON_STOCK_EXHAUSTED'
      || code === 'COUPON_EVENT_ITEM_NOT_AVAILABLE');
  const expected = success || soldOut;

  claimSuccesses.add(success ? 1 : 0);
  claimSoldOuts.add(soldOut ? 1 : 0);
  unexpectedClaims.add(expected ? 0 : 1);
  expectedClaimResults.add(expected);
  check(response, {
    'claim result is success or sold out': () => expected,
  });

  if (success) {
    const persisted = waitForPersistedCoupon(userId, data.couponEventId);
    persistedCoupons.add(persisted ? 1 : 0);
    persistenceFailures.add(persisted ? 0 : 1);
    check(persisted, {
      'issued coupon is persisted after Kafka processing': (value) => value,
    });
  }
}

export function openCouponEvent(data) {
  const response = http.post(
    `${baseUrl}/api/v1/admin/coupon-events/${data.couponEventId}`
      + '/occurrences/manual-open',
    null,
    jsonParams(
      'POST /admin/coupon-events/:id/occurrences/manual-open',
      'event-open',
    ),
  );

  check(response, {
    'admin opens coupon event': (res) => res.status === 201,
    'opened occurrence is OPEN': (res) => jsonValue(res, 'occurrenceStatus') === 'OPEN',
    'opened event has expected stock': (res) => Number(jsonValue(res, 'remainingQuantity'))
      === couponQuantity,
  });
}

export function teardown(data) {
  const response = http.get(
    `${baseUrl}/api/v1/admin/coupon-events/${data.couponEventId}`,
    jsonParams('GET /admin/coupon-events/:id', 'final-event-detail'),
  );
  const issuedQuantity = Number(jsonValue(response, 'issuedQuantity'));

  check(response, {
    'final event detail is returned': (res) => res.status === 200,
    'final issued quantity equals stock': () => issuedQuantity === couponQuantity,
  });
  console.log(
    `최종 확인: event=${data.couponEventId}, issued=${issuedQuantity}/${couponQuantity}`,
  );
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

function waitForActiveEvent(couponEventId) {
  const deadline = Date.now() + eventPollTimeoutSeconds * 1000;
  while (Date.now() < deadline) {
    const response = http.get(`${baseUrl}/api/v1/coupon-events/active`, {
      tags: { endpoint: 'active-event', name: 'GET /coupon-events/active' },
    });
    if (response.status === 200
        && Number(jsonValue(response, 'couponEventId')) === Number(couponEventId)) {
      return response.json();
    }
    sleep(pollIntervalSeconds);
  }
  return null;
}

function waitForPersistedCoupon(userId, couponEventId) {
  const deadline = Date.now() + persistenceTimeoutSeconds * 1000;
  while (Date.now() < deadline) {
    const response = http.get(`${baseUrl}/api/users/me/coupons?size=100`, {
      headers: {
        Accept: 'application/json',
        'X-User-Id': String(userId),
      },
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
