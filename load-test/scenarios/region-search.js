import http from 'k6/http';
import { commonOptions } from '../config/options.js';
import { apiUrl } from '../config/env.js';
import { requestParams } from '../utils/headers.js';
import { checkJsonResponse } from '../utils/checks.js';

const keyword = '군';
const debug = __ENV.DEBUG === 'true';

export const options = {
  ...commonOptions,
  vus: Number(__ENV.VUS || 1),
  iterations: Number(__ENV.ITERATIONS || 1),
};

export default function () {
  const response = http.get(
      apiUrl(`/api/v1/regions/search?keyword=${encodeURIComponent(keyword)}`),
      requestParams(),
  );

  if (debug) {
    console.log(
        `keyword=${keyword}, status=${response.status}, duration=${response.timings.duration}ms`,
    );
  }

  checkJsonResponse(response, 200);
}