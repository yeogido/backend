import { check } from 'k6';

// 상태 코드만 검증해야 하는 시나리오에서 재사용
export function checkStatus(response, expectedStatus = 200) {
  return check(response, {
    [`status is ${expectedStatus}`]: (res) => res.status === expectedStatus,
  });
}

// 응답시간 기준은 MAX_RESPONSE_TIME_MS 환경변수로 조정
export function checkResponseTime(
    response,
    maxDurationMs = Number(__ENV.MAX_RESPONSE_TIME_MS || 1000),
) {
  return check(response, {
    [`response time < ${maxDurationMs}ms`]: (res) =>
        res.timings.duration < maxDurationMs,
  });
}

// 공통 검증: 상태 코드와 응답시간만 확인
export function checkJsonResponse(
    response,
    expectedStatus = 200,
    maxDurationMs = Number(__ENV.MAX_RESPONSE_TIME_MS || 1000),
) {
  return check(response, {
    [`status is ${expectedStatus}`]: (res) =>
        res.status === expectedStatus,
    [`response time < ${maxDurationMs}ms`]: (res) =>
        res.timings.duration < maxDurationMs,
  });
}