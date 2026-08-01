import http from 'k6/http';
import { sleep } from 'k6';
import { courseListSortOptions } from '../../config/options.js';
import { apiUrl } from '../../config/env.js';
import { requestParams } from '../../utils/headers.js';
import { checkJsonResponse } from '../../utils/checks.js';

const maxResponseTime = Number(__ENV.MAX_RESPONSE_TIME_MS || 1000);
const debug = __ENV.DEBUG === 'true';

export const options = courseListSortOptions;

export function courseListParams(params) {
  return {
    courseType: __ENV.COURSE_TYPE || 'OFFICIAL',
    size: __ENV.SIZE || '20',
    ...params,
  };
}

export function runCourseListScenario(name, params) {
  const url = apiUrl(`/api/v1/courses?${buildQuery(params)}`);
  const response = http.get(url, {
    ...requestParams(),
    tags: {
      api: 'course-list',
      sort_case: name,
    },
  });

  if (debug) {
    console.log(
        `case=${name}, status=${response.status}, duration=${response.timings.duration}ms`,
    );
  }

  checkJsonResponse(response, 200, maxResponseTime);
  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}

function buildQuery(params) {
  return Object.entries(params)
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
      .join('&');
}
