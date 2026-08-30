import exec from 'k6/execution';
import { sleep } from 'k6';
import {
  claimThresholds,
  claimCouponForUser,
  setupCouponTest,
  teardownCouponTest,
} from '../common/coupon-claim.js';

const virtualUsers = positiveInteger(__ENV.COUPON_VUS, 100, 'COUPON_VUS');
const userIdStart = positiveInteger(__ENV.USER_ID_START, 900001, 'USER_ID_START');
const rampUpSeconds = positiveInteger(__ENV.RAMP_UP_SECONDS, 60, 'RAMP_UP_SECONDS');
const holdSeconds = positiveInteger(__ENV.HOLD_SECONDS, 30, 'HOLD_SECONDS');
const finalVerificationTimeoutSeconds = positiveNumber(
  __ENV.FINAL_VERIFICATION_TIMEOUT_SECONDS,
  120,
  'FINAL_VERIFICATION_TIMEOUT_SECONDS',
);

export const thresholds = claimThresholds;

export const options = {
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
  thresholds,
  teardownTimeout: `${Math.ceil(finalVerificationTimeoutSeconds) + 10}s`,
};

export function setup() {
  return setupCouponTest();
}

export function claimCoupon(data) {
  if (exec.vu.iterationInScenario > 0) {
    sleep(rampUpSeconds + holdSeconds + 30);
    return;
  }

  const userId = userIdStart + exec.vu.idInTest - 1;
  claimCouponForUser(data, userId);
}

export function teardown(data) {
  teardownCouponTest(data);
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
