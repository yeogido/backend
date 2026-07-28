const DEFAULT_BASE_URL = 'http://localhost:8080';

// k6 실행 시 -e 옵션으로 전달한 값을 한곳에서 관리.
export const BASE_URL = (__ENV.BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, '');
export const ACCESS_TOKEN = __ENV.ACCESS_TOKEN || '';

// BASE_URL 뒤의 슬래시 중복을 막기 위한 API URL 헬퍼
export function apiUrl(path) {
  return `${BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
}
