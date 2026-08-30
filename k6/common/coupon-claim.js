import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://100.101.76.93:8080')
  .replace(/\/$/, '');

const totalUsers = positiveInteger(__ENV.COUPON_VUS, 100, 'COUPON_VUS');
const couponQuantity = positiveInteger(
  __ENV.COUPON_QUANTITY,
  Math.floor(totalUsers / 2),
  'COUPON_QUANTITY',
);
const matchId = positiveInteger(__ENV.MATCH_ID, 316, 'MATCH_ID');
const claimWindowSeconds = positiveInteger(
  __ENV.CLAIM_WINDOW_SECONDS,
  600,
  'CLAIM_WINDOW_SECONDS',
);
const finalVerificationTimeoutSeconds = positiveNumber(
  __ENV.FINAL_VERIFICATION_TIMEOUT_SECONDS,
  120,
  'FINAL_VERIFICATION_TIMEOUT_SECONDS',
);
const claimRequestTimeout = __ENV.CLAIM_REQUEST_TIMEOUT || '1m';
const couponName = __ENV.COUPON_NAME || '[K6] 10%';

if (couponQuantity >= totalUsers) {
  throw new Error('COUPON_QUANTITY는 COUPON_VUS보다 작아야 품절 경쟁을 검증할 수 있습니다.');
}

const claimSuccesses = new Counter('coupon_claim_success_total');
const claimSoldOuts = new Counter('coupon_claim_sold_out_total');
const unexpectedClaims = new Counter('coupon_claim_unexpected_total');
const claimTransportFailures = new Counter('coupon_claim_transport_failure_total');
const claimDuration = new Trend('coupon_claim_duration', true);
const finalVerificationSuccesses = new Counter('coupon_final_verification_success_total');

// Ramp와 Burst가 공통으로 지켜야 하는 최소 판정만 둔다.
// 개별 사용자 저장 조회와 DB·Redis·Kafka 정합성 검증은 부하 종료 후 별도 검증에서 수행한다.
export const claimThresholds = {
  'http_req_duration{endpoint:claim,expected_response:true}': ['p(95)<5000'],
  coupon_claim_success_total: [`count==${couponQuantity}`],
  coupon_claim_sold_out_total: [`count==${totalUsers - couponQuantity}`],
  coupon_claim_unexpected_total: ['count==0'],
  coupon_claim_transport_failure_total: ['count==0'],
  coupon_final_verification_success_total: ['count==1'],
};

/** 테스트 이벤트를 생성하고 수동 오픈한 뒤 공유 식별자를 반환한다. */
export function setupCouponTest() {
  const couponType = findCouponType();
  const response = http.post(
    `${baseUrl}/api/v1/admin/coupon-events`,
    JSON.stringify({
      esportsMatchId: matchId,
      eventName: `K6 부하테스트_${todayInKorea()}_${totalUsers}`,
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
  console.log(
    `준비 완료: event=${couponEventId}, occurrence=${occurrence.couponEventOccurrenceId}, `
      + `users=${totalUsers}, stock=${couponQuantity}, couponType=${couponType.couponTypeId}, `
      + `claimTimeout=${claimRequestTimeout}`,
  );

  return {
    couponEventId,
    couponEventOccurrenceId: occurrence.couponEventOccurrenceId,
  };
}

/** 지정한 고유 사용자로 쿠폰을 한 번 신청하고 최소 결과 지표만 기록한다. */
export function claimCouponForUser(data, userId) {
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
  const success = response.status === 201;
  const soldOut = response.status === 409
    && code === 'COUPON_STOCK_EXHAUSTED';
  const expected = success || soldOut;

  claimSuccesses.add(success ? 1 : 0);
  claimSoldOuts.add(soldOut ? 1 : 0);
  unexpectedClaims.add(!transportFailure && !expected ? 1 : 0);

  if (!transportFailure && !expected) {
    console.error(
      `쿠폰 신청 오류: userId=${userId}, status=${response.status}, `
        + `code=${code ?? 'NO_CODE'}, body=${response.body}`,
    );
  }
}

/** 부하가 끝난 뒤 이벤트의 최종 발급 수량만 확인한다. */
export function teardownCouponTest(data) {
  const deadline = Date.now() + finalVerificationTimeoutSeconds * 1000;
  let response;
  let issuedQuantity = 0;

  do {
    response = http.get(
      `${baseUrl}/api/v1/admin/coupon-events/${data.couponEventId}`,
      jsonParams('GET /admin/coupon-events/:id', 'final-event-detail'),
    );
    issuedQuantity = Number(jsonValue(response, 'issuedQuantity'));
    if (response.status === 200 && issuedQuantity === couponQuantity) {
      break;
    }
    sleep(1);
  } while (Date.now() < deadline);

  const verified = response.status === 200 && issuedQuantity === couponQuantity;
  finalVerificationSuccesses.add(verified ? 1 : 0);
  console.log(
    `FINAL_VERIFICATION event=${data.couponEventId} `
      + `issued=${issuedQuantity} expected=${couponQuantity} `
      + `result=${verified ? 'PASS' : 'FAIL'}`,
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
