import { courseListParams, options, runCourseListScenario } from './common.js';

export { options };

const params = courseListParams({
  sort: 'LATEST',
});

export default function () {
  runCourseListScenario('latest', params);
}
