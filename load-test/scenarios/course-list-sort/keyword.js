import { courseListParams, options, runCourseListScenario } from './common.js';

export { options };

const params = courseListParams({
  keyword: __ENV.KEYWORD || '강릉',
  sort: __ENV.KEYWORD_SORT || 'LATEST',
});

export default function () {
  runCourseListScenario('keyword', params);
}
