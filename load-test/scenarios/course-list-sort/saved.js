import { courseListParams, options, runCourseListScenario } from './common.js';

export { options };

const params = courseListParams({
  sort: 'SAVED',
});

export default function () {
  runCourseListScenario('saved', params);
}
