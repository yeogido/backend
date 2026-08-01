import { courseListParams, options, runCourseListScenario } from './common.js';

export { options };

const params = courseListParams({
  sort: 'DISTANCE',
  latitude: __ENV.LATITUDE || '37.5665',
  longitude: __ENV.LONGITUDE || '126.9780',
});

export default function () {
  runCourseListScenario('distance', params);
}
