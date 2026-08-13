import { courseListParams, options, runCourseListScenario } from './common.js';

export { options };

const params = courseListParams({
  sort: 'REVIEW',
});

export default function () {
  runCourseListScenario('review', params);
}
