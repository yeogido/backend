import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { apiUrl } from '../../config/env.js';
import { requestParams } from '../../utils/headers.js';

const size = Number(__ENV.SIZE || 20);
const rate = Number(__ENV.RATE || 10);
const vus = Number(__ENV.VUS || 20);
const page = __ENV.PAGE || 'first';
const sort = __ENV.SORT || 'POPULAR';
const successDuration = new Trend('successful_page_duration', true);
const expectedIds = (__ENV.EXPECTED_IDS || '').split(',').filter(Boolean).map(Number);

if (!['first', 'next'].includes(page)) {
  throw new Error('PAGE must be first or next');
}
if (page === 'next' && (!__ENV.CURSOR_ID || !__ENV.CURSOR_VALUE)) {
  throw new Error('PAGE=next requires CURSOR_ID and CURSOR_VALUE');
}

export const options = {
  scenarios: {
    course_list: {
      // 응답이 느려져도 목표 요청률 기준으로 부하를 주고 시작하지 못한 요청을 기록
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: vus,
      gracefulStop: '15s',
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    dropped_iterations: ['count==0'],
  },
};

const query = [
  ['courseType', __ENV.COURSE_TYPE || 'OFFICIAL'],
  ['sort', sort],
  ['size', size],
];
if (page === 'next') {
  query.push(['cursorId', __ENV.CURSOR_ID], ['cursorValue', __ENV.CURSOR_VALUE]);
}
const url = apiUrl('/api/v1/courses?' + query
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`).join('&'));

export default function () {
  const response = http.get(url, {
    ...requestParams(),
    timeout: __ENV.REQUEST_TIMEOUT || '5s',
    tags: { api: 'course-list', sort_case: sort, page },
  });
  let items;
  if (response.status === 200) {
    try {
      items = response.json('result.items');
    } catch (_) {
      // 파싱할 수 없는 응답은 아래 검증에서 페이지 실패로 기록
    }
  }
  const valid = check(response, {
    'returns the expected course page': () => Array.isArray(items)
        && items.length === size
        && new Set(items.map((item) => item.courseId)).size === size
        && (expectedIds.length === 0 || (expectedIds.length === size
            && items.every((item, index) => item.courseId === expectedIds[index]))),
  });
  if (valid) {
    successDuration.add(response.timings.duration);
  }
}
