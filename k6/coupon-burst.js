import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

// =============================================================================
// 테스트 구조
// 1. setup(): 단 한 번 실행되어 이벤트를 생성하고 즉시 연다.
// 2. claimCoupon(): setup이 끝난 뒤 COUPON_VUS 수만큼 동시에 한 번씩 신청한다.
// 3. teardown(): 모든 신청이 끝난 뒤 최종 발급 수량을 한 번 확인한다.
//
// 준비와 부하를 별도 파일로 나누지 않은 이유:
// 서로 다른 k6 프로세스는 이벤트 ID와 회차 ID를 자동으로 공유할 수 없다.
// 한 파일의 k6 생명주기를 사용하면 setup 반환값이 모든 VU에 안전하게 전달되고,
// 이벤트 오픈 실패 시 사용자 부하가 시작되는 것도 막을 수 있다.
// =============================================================================

// 1) 실행 명령의 환경변수로 조절하는 테스트 입력값
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
const claimWindowSeconds = positiveInteger(
  __ENV.CLAIM_WINDOW_SECONDS,
  600,
  'CLAIM_WINDOW_SECONDS',
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

// 2) k6 결과와 Prometheus/Grafana에 기록할 사용자 정의 지표
const claimSuccesses = new Counter('coupon_claim_success_total');
const claimSoldOuts = new Counter('coupon_claim_sold_out_total');
const unexpectedClaims = new Counter('coupon_claim_unexpected_total');
const expectedClaimResults = new Rate('coupon_claim_expected');
const persistedCoupons = new Counter('coupon_persisted_total');
const persistenceFailures = new Counter('coupon_persistence_failed_total');
const claimFailures = new Counter('coupon_claim_failure');
const claimDuration = new Trend('coupon_claim_duration', true);

// 3) 부하 단계 설정과 테스트 합격 기준
// setup은 scenarios보다 먼저 한 번 실행되므로 claimers에는 관리자 VU가 섞이지 않는다.
export const options = {
  scenarios: {
    claimers: {
      executor: 'per-vu-iterations',
      exec: 'claimCoupon',
      vus: virtualUsers,
      iterations: 1,
      maxDuration: '2m',
      gracefulStop: '5s',
      tags: { flow: 'coupon-claimer' },
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:claim}': ['p(95)<5000'],
    coupon_claim_expected: ['rate>0.99'],
    coupon_claim_success_total: [`count==${couponQuantity}`],
    coupon_claim_sold_out_total: [`count==${virtualUsers - couponQuantity}`],
    coupon_claim_unexpected_total: ['count==0'],
    coupon_persisted_total: [`count==${couponQuantity}`],
    coupon_persistence_failed_total: ['count==0'],
  },
};

/**
 * [1인 준비 단계]
 * 활성 쿠폰 종류를 찾고 테스트 이벤트를 생성한 뒤 즉시 수동 오픈한다.
 * 이 함수가 반환한 이벤트 ID와 회차 ID는 k6가 모든 사용자 VU에 전달한다.
 * 생성이나 오픈에 실패하면 fail()로 중단하여 잘못된 부하 테스트를 막는다.
 */
export function setup() {
  const couponType = findCouponType();
  const eventName = `K6 부하테스트_${todayInKorea()}_${virtualUsers}`;
  const payload = {
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
  const occurrence = openCouponEvent(couponEventId);
  console.log(
    `준비 완료: event=${couponEventId}, occurrence=${occurrence.couponEventOccurrenceId}, `
      + `users=${virtualUsers}, stock=${couponQuantity}, couponType=${couponType.couponTypeId}`,
  );

  return {
    couponEventId,
    couponEventOccurrenceId: occurrence.couponEventOccurrenceId,
    eventName,
  };
}

/**
 * [다중 사용자 부하 단계]
 * setup이 정상 종료된 뒤 COUPON_VUS 수만큼 실행된다.
 * VU마다 서로 다른 사용자 ID로 동일한 이벤트 회차에 한 번씩 신청한다.
 * 성공 사용자는 실제 쿠폰 저장까지 확인하고, 나머지는 재고 소진 응답을 기대한다.
 */
export function claimCoupon(data) {
  const userId = userIdStart + exec.scenario.iterationInTest;
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
      tags: { endpoint: 'claim', name: 'POST /coupon-events/:id/occurrences/:id/claims' },
    },
  );

  claimDuration.add(response.timings.duration);
  const code = jsonValue(response, 'code');
  if (response.status >= 500) {
    claimFailures.add(1, {
      status: String(response.status),
      error_code: code ?? 'NO_CODE',
    });

    console.error(
      `쿠폰 신청 오류: userId=${userId}, `
        + `status=${response.status}, `
        + `code=${code ?? 'NO_CODE'}, `
        + `body=${response.body}`,
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

/**
 * [준비 단계 내부 작업]
 * setup이 생성한 이벤트를 한 번 열고 회차 ID와 재고를 검증한다.
 * 별도 k6 scenario가 아니라 setup 안에서 호출되므로 VU 부족의 영향을 받지 않는다.
 */
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

/**
 * [1인 최종 검증 단계]
 * 모든 사용자 실행이 끝난 뒤 실제 발급 수량이 설정 재고와 같은지 확인한다.
 */
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

// 4) 아래 함수들은 준비·부하·검증 단계가 함께 사용하는 공통 도구다.

/** 테스트에 사용할 활성 상태의 정률 10% 쿠폰 종류를 찾는다. */
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

/** 성공 사용자에게 쿠폰이 실제 저장됐는지 제한 시간 동안 확인한다. */
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

/** JSON API 요청에 공통 헤더와 Prometheus 분류용 태그를 붙인다. */
function jsonParams(name, endpoint) {
  return {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    tags: { endpoint, name },
  };
}

/** 응답 JSON을 안전하게 읽고 읽을 수 없으면 null을 반환한다. */
function jsonValue(response, selector) {
  try {
    return response.json(selector);
  } catch (_) {
    return null;
  }
}

/** 이벤트 이름에 넣을 한국 날짜를 YYYYMMDD 형식으로 만든다. */
function todayInKorea() {
  const now = new Date(Date.now() + 9 * 60 * 60 * 1000);
  const year = now.getUTCFullYear();
  const month = String(now.getUTCMonth() + 1).padStart(2, '0');
  const day = String(now.getUTCDate()).padStart(2, '0');
  return `${year}${month}${day}`;
}

/** 환경변수가 1 이상의 정수인지 검증한다. */
function positiveInteger(rawValue, defaultValue, name) {
  const value = rawValue === undefined ? defaultValue : Number(rawValue);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name}은 1 이상의 정수여야 합니다.`);
  }
  return value;
}

/** 환경변수가 0보다 큰 숫자인지 검증한다. */
function positiveNumber(rawValue, defaultValue, name) {
  const value = rawValue === undefined ? defaultValue : Number(rawValue);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name}은 0보다 큰 숫자여야 합니다.`);
  }
  return value;
}
