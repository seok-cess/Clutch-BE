import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import {
  claimCouponForUser,
  setup,
  teardown,
  thresholds as couponThresholds,
} from './coupon-burst.js';

// 한 프로세스가 이벤트를 한 번 생성·오픈한 뒤 총 사용자를 램프업 시간에 고르게 투입한다.
// 20,000명 / 60초이면 약 333.33개의 신규 신청을 매초 시작한다.
const totalUsers = positiveInteger(__ENV.COUPON_VUS, 100, 'COUPON_VUS');
const rampUpSeconds = positiveInteger(__ENV.RAMP_UP_SECONDS, 60, 'RAMP_UP_SECONDS');
const userIdStart = positiveInteger(__ENV.USER_ID_START, 900001, 'USER_ID_START');
const preAllocatedVus = positiveInteger(
  __ENV.PRE_ALLOCATED_VUS,
  Math.min(totalUsers, 5000),
  'PRE_ALLOCATED_VUS',
);
const maxVus = positiveInteger(__ENV.MAX_VUS, totalUsers, 'MAX_VUS');
const finalVerificationTimeoutSeconds = positiveNumber(
  __ENV.FINAL_VERIFICATION_TIMEOUT_SECONDS,
  120,
  'FINAL_VERIFICATION_TIMEOUT_SECONDS',
);
const claimAttempts = new Counter('coupon_claim_attempt_total');

if (preAllocatedVus > maxVus) {
  throw new Error('PRE_ALLOCATED_VUS는 MAX_VUS보다 클 수 없습니다.');
}

export const options = {
  scenarios: {
    claimers: {
      executor: 'constant-arrival-rate',
      exec: 'claimCoupon',
      rate: totalUsers,
      timeUnit: `${rampUpSeconds}s`,
      duration: `${rampUpSeconds}s`,
      preAllocatedVUs: preAllocatedVus,
      maxVUs: maxVus,
      gracefulStop: '1m',
      tags: { flow: 'coupon-claimer' },
    },
  },
  thresholds: {
    ...couponThresholds,
    dropped_iterations: ['count==0'],
    coupon_claim_attempt_total: [`count==${totalUsers}`],
  },
  teardownTimeout: `${Math.ceil(finalVerificationTimeoutSeconds) + 10}s`,
};

export { setup, teardown };

export function claimCoupon(data) {
  const iteration = exec.scenario.iterationInTest;
  // constant-arrival-rate가 시간 경계에서 1회를 더 예약해도 실제 신청은 정확히
  // COUPON_VUS건만 전송한다. 판정도 iterations가 아니라 이 전용 지표를 사용한다.
  if (iteration >= totalUsers) {
    return;
  }
  claimAttempts.add(1);
  const userId = userIdStart + iteration;
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
