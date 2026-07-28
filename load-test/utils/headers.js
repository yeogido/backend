import { ACCESS_TOKEN } from '../config/env.js';

// ACCESS_TOKEN이 있으면 Bearer 토큰을 포함하고, 없으면 공개 API 호출용 헤더만 반환
export function jsonHeaders(token = ACCESS_TOKEN) {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (token) {
    headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  }

  return headers;
}

// k6 http 요청의 두 번째 인자로 바로 전달할 수 있는 공통 param
export function requestParams(token = ACCESS_TOKEN) {
  return {
    headers: jsonHeaders(token),
  };
}
