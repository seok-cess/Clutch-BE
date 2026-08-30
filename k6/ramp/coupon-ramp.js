import exec from 'k6/execution';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import {
  claimThresholds,
  claimCouponForUser,
  setupCouponTest,
  teardownCouponTest,
} from '../common/coupon-claim.js';

// 한 프로세스가 이벤트를 한 번 생성·오픈한 뒤 VU를 0명에서 목표 인원까지 선형적으로 늘린다.
// 각 VU는 고유한 사용자 ID로 정확히 한 번만 신청하므로 20,000 VU는 중복 없는 20,000명을 의미한다.
const totalUsers = positiveInteger(__ENV.COUPON_VUS, 100, 'COUPON_VUS');
const rampUpSeconds = positiveInteger(__ENV.RAMP_UP_SECONDS, 60, 'RAMP_UP_SECONDS');
const holdSeconds = positiveInteger(__ENV.HOLD_SECONDS, 1, 'HOLD_SECONDS');
const userIdStart = positiveInteger(__ENV.USER_ID_START, 900001, 'USER_ID_START');
const finalVerificationTimeoutSeconds = positiveNumber(
  __ENV.FINAL_VERIFICATION_TIMEOUT_SECONDS,
  120,
  'FINAL_VERIFICATION_TIMEOUT_SECONDS',
);
const claimAttempts = new Counter('coupon_claim_attempt_total');

export const options = {
  noConnectionReuse: true,
  scenarios: {
    claimers: {
      executor: 'ramping-vus',
      exec: 'claimCoupon',
      startVUs: 0,
      stages: [
        { duration: `${rampUpSeconds}s`, target: totalUsers },
        { duration: `${holdSeconds}s`, target: totalUsers },
      ],
      gracefulStop: '1m',
      tags: { flow: 'coupon-claimer' },
    },
  },
  thresholds: {
    ...claimThresholds,
    coupon_claim_attempt_total: [`count==${totalUsers}`],
  },
  teardownTimeout: `${Math.ceil(finalVerificationTimeoutSeconds) + 10}s`,
};

export function setup() {
  return setupCouponTest();
}

export function teardown(data) {
  teardownCouponTest(data);
}

export function claimCoupon(data) {
  // ramping-vus는 같은 VU를 반복 실행하므로 각 VU의 첫 번째 신청만 전송한다.
  // 신청을 마친 VU는 테스트 종료까지 유지해 동일 사용자의 반복 요청을 막는다.
  if (exec.vu.iterationInScenario > 0) {
    sleep(rampUpSeconds + holdSeconds + 30);
    return;
  }

  claimAttempts.add(1);
  const userId = userIdStart + exec.vu.idInTest - 1;
  claimCouponForUser(data, userId);
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
