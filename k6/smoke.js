import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');

export const options = {
  vus: Number(__ENV.K6_VUS || 5),
  duration: __ENV.K6_DURATION || '10s',
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/actuator/health`);

  check(response, {
    'health endpoint returns 200': (res) => res.status === 200,
    'application status is UP': (res) => {
      try {
        return res.json('status') === 'UP';
      } catch (_) {
        return false;
      }
    },
  });

  sleep(1);
}
