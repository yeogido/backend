import { courseListParams, options, runCourseListScenario } from './common.js';

export { options };

const params = courseListParams({
  sort: 'RECOMMEND',
  regionId: __ENV.REGION_ID || '1',
});

export default function () {
  runCourseListScenario('recommend', params);
}
