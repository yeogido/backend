import http from 'k6/http';
import { sleep } from 'k6';
import { apiUrl } from '../config/env.js';
import { requestParams } from '../utils/headers.js';
import { checkJsonResponse } from '../utils/checks.js';

const maxResponseTime = Number(__ENV.MAX_RESPONSE_TIME_MS || 1000);
const debug = __ENV.DEBUG === 'true';

const courseListRequests = [
  {
    name: 'official latest',
    params: {
      courseType: 'OFFICIAL',
      sort: 'LATEST',
      size: __ENV.SIZE || '20',
    },
  },
  {
    name: 'official recommend by region',
    params: {
      courseType: 'OFFICIAL',
      regionId: __ENV.REGION_ID || '1',
      sort: 'RECOMMEND',
      size: __ENV.SIZE || '20',
    },
  },
  {
    name: 'official distance from current location',
    params: {
      courseType: 'OFFICIAL',
      sort: 'DISTANCE',
      latitude: __ENV.LATITUDE || '37.5665',
      longitude: __ENV.LONGITUDE || '126.9780',
      size: __ENV.SIZE || '20',
    },
  },
  {
    name: 'local filtered latest',
    params: {
      courseType: 'LOCAL',
      transportType: __ENV.TRANSPORT_TYPE || 'CAR',
      durationType: __ENV.DURATION_TYPE || 'DAY_TRIP',
      companionType: __ENV.COMPANION_TYPE || 'FRIEND',
      sort: 'LATEST',
      size: __ENV.SIZE || '20',
    },
  },
  {
    name: 'keyword search',
    params: {
      courseType: __ENV.COURSE_TYPE || 'OFFICIAL',
      keyword: __ENV.KEYWORD || '강릉',
      sort: 'LATEST',
      size: __ENV.SIZE || '20',
    },
  },
  {
    name: 'saved count sort',
    params: {
      courseType: __ENV.COURSE_TYPE || 'OFFICIAL',
      sort: 'SAVED',
      size: __ENV.SIZE || '20',
    },
  },
  {
    name: 'review count sort',
    params: {
      courseType: __ENV.COURSE_TYPE || 'OFFICIAL',
      sort: 'REVIEW',
      size: __ENV.SIZE || '20',
    },
  },
];

if (__ENV.CURSOR_VALUE && __ENV.CURSOR_ID) {
  courseListRequests.push({
    name: 'cursor pagination',
    params: {
      courseType: __ENV.COURSE_TYPE || 'OFFICIAL',
      sort: __ENV.CURSOR_SORT || __ENV.SORT || 'LATEST',
      cursorValue: __ENV.CURSOR_VALUE,
      cursorId: __ENV.CURSOR_ID,
      size: __ENV.SIZE || '20',
    },
  });
}

export const options = {
  stages: [
    { duration: __ENV.STAGE_10_DURATION || '30s', target: 10 },
    { duration: __ENV.STAGE_30_DURATION || '1m', target: 30 },
    { duration: __ENV.STAGE_50_DURATION || '1m', target: 50 },
    { duration: __ENV.STAGE_100_DURATION || '1m', target: 100 },
    { duration: __ENV.RAMP_DOWN_DURATION || '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${maxResponseTime}`],
    checks: ['rate>0.99'],
  },
};

function buildQuery(params) {
  return Object.entries(params)
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
      .join('&');
}

export default function () {
  const request = courseListRequests[__ITER % courseListRequests.length];
  const url = apiUrl(`/api/v1/courses?${buildQuery(request.params)}`);
  const response = http.get(url, {
    ...requestParams(),
    tags: {
      api: 'course-list',
      case: request.name,
    },
  });

  if (debug) {
    console.log(
        `case=${request.name}, status=${response.status}, duration=${response.timings.duration}ms`,
    );
  }

  checkJsonResponse(response, 200, maxResponseTime);
  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
