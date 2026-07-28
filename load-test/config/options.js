const vus = Number(__ENV.VUS || 10);
const duration = __ENV.DURATION || '30s';
const maxResponseTime = Number(__ENV.MAX_RESPONSE_TIME_MS || 1000);

// 짧은 단일 API 부하 테스트에 쓰는 기본 옵션
export const commonOptions = {
  vus,
  duration,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${maxResponseTime}`],
  },
};

// 램프업/유지/램프다운이 필요한 시나리오에서 선택적으로 사용
export const stagedOptions = {
  stages: [
    { duration: __ENV.RAMP_UP_DURATION || '30s', target: Number(__ENV.RAMP_UP_VUS || vus) },
    { duration: __ENV.STEADY_DURATION || '1m', target: Number(__ENV.STEADY_VUS || vus) },
    { duration: __ENV.RAMP_DOWN_DURATION || '30s', target: 0 },
  ],
  thresholds: commonOptions.thresholds,
};
