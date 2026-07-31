# Load Test

Spring Boot API를 대상으로 k6 부하 테스트를 작성하기 위한 공통 환경입니다. 공통 설정과 유틸을 재사용해 API별 테스트를 `scenarios/`에 추가합니다.

## k6 설치

macOS:

```bash
brew install k6
```

Docker:

```bash
docker pull grafana/k6
```

설치 확인:

```bash
k6 version
```

## 디렉터리 구조

```text
load-test/
├── config/
│   ├── env.js        # BASE_URL, ACCESS_TOKEN 등 공통 환경변수
│   └── options.js    # VU, duration, stages, thresholds 공통 옵션
├── utils/
│   ├── checks.js     # 상태 코드, 응답시간 등 공통 check
│   └── headers.js    # Authorization, Content-Type 공통 header
├── scenarios/
│   ├── .gitkeep
│   ├── course-list.js   # 추천 코스 목록 조회 API 단계형 부하 테스트
│   └── region-search.js # 지역 검색 API 단일 요청 테스트
├── README.md
└── .gitignore
```

## 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | 테스트 대상 Spring Boot API 주소 |
| `TOKEN` | 빈 값 | Bearer 인증 토큰. 값이 있으면 `Authorization` 헤더에 포함 |
| `ACCESS_TOKEN` | 빈 값 | 기존 실행 스크립트 호환용 인증 토큰. `TOKEN`이 있으면 `TOKEN`을 우선 사용 |
| `VUS` | `10` | 동시 가상 사용자 수 |
| `ITERATIONS` | 시나리오별 설정 | 전체 반복 실행 횟수 |
| `DURATION` | `30s` | 고정 부하 테스트 시간 |
| `MAX_RESPONSE_TIME_MS` | `1000` | 공통 응답시간 check와 threshold 기준 |
| `RAMP_UP_DURATION` | `30s` | 단계형 테스트 ramp-up 시간 |
| `RAMP_UP_VUS` | `VUS` | 단계형 테스트 ramp-up 목표 VU |
| `STEADY_DURATION` | `1m` | 단계형 테스트 유지 시간 |
| `STEADY_VUS` | `VUS` | 단계형 테스트 유지 VU |
| `RAMP_DOWN_DURATION` | `30s` | 단계형 테스트 ramp-down 시간 |

## 실행 방법

먼저 `scenarios/`에 테스트 파일을 추가한 뒤 `k6 run`으로 실행합니다.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e TOKEN=xxxxx \
  load-test/scenarios/your-scenario.js
```

결과 JSON을 저장하려면:

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  --summary-export load-test/result.json \
  load-test/scenarios/your-scenario.js
```

Docker로 실행:

```bash
docker run --rm \
  -i \
  -v "$PWD/load-test:/scripts" \
  grafana/k6 run \
  -e BASE_URL=http://host.docker.internal:8080 \
  /scripts/scenarios/your-scenario.js
```

## 지역 검색 API 시나리오

환경 확인용 단일 요청 시나리오입니다. 기본값은 VU 1, iteration 1이며, `keyword=군`으로 고정하여 `GET /api/v1/regions/search?keyword={군}`을 호출합니다.
`ACCESS_TOKEN`이 있으면 `Authorization` 헤더를 포함하고, 없으면 인증 헤더 없이 실행합니다.
응답 상태 코드와 응답 시간을 확인하려면 `DEBUG=true`를 전달합니다.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e DEBUG=true \
  load-test/scenarios/region-search.js
```

인증 토큰이 필요한 환경에서는 다음처럼 실행합니다.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e TOKEN=xxxxx \
  load-test/scenarios/region-search.js
```

`DEBUG=true`일 때만 `console.log`로 요청 키워드, 응답 상태 코드, 응답 시간이 출력됩니다.

## 추천 코스 목록 조회 API 시나리오

`load-test/scenarios/course-list.js`는 실제 `GET /api/v1/courses` 요청 모델의 쿼리 파라미터를 조합해 호출합니다.

- 필수 파라미터: `courseType`
- 필터 파라미터: `keyword`, `regionId`, `transportType`, `durationType`, `companionType`
- 정렬 파라미터: `sort=LATEST|RECOMMEND|DISTANCE|SAVED|REVIEW`
- 위치 파라미터: `latitude`, `longitude` (`sort=DISTANCE` 요청에서 사용)
- 커서 파라미터: `cursorValue`, `cursorId`
- 페이지 크기: `size`

기본 시나리오는 다음 케이스를 순환 실행합니다.

- 공식 코스 최신순
- 공식 코스 지역별 추천순
- 공식 코스 현재 위치 기준 거리순
- 로컬 코스 필터 최신순
- 키워드 검색
- 저장수순
- 리뷰수순

부하는 k6 `options.stages`로 10 -> 30 -> 50 -> 100 VUs까지 단계적으로 증가한 뒤 0 VUs로 내려갑니다. 각 요청은 응답 상태 코드 200과 `MAX_RESPONSE_TIME_MS` 이하 응답시간을 check로 검증하며, 기본 기준은 1000ms입니다.

로컬 실행:

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  load-test/scenarios/course-list.js
```

인증 토큰이 필요한 테스트 환경:

```bash
k6 run \
  -e BASE_URL=https://api.example.com \
  -e TOKEN=xxxxx \
  load-test/scenarios/course-list.js
```

주요 파라미터와 응답시간 기준을 바꿔 실행:

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e TOKEN=xxxxx \
  -e MAX_RESPONSE_TIME_MS=800 \
  -e REGION_ID=1 \
  -e KEYWORD=강릉 \
  -e LATITUDE=37.5665 \
  -e LONGITUDE=126.9780 \
  -e SIZE=20 \
  load-test/scenarios/course-list.js
```

커서 기반 다음 페이지 요청까지 포함하려면 `CURSOR_VALUE`와 `CURSOR_ID`를 함께 전달합니다. `CURSOR_VALUE` 형식은 정렬 기준에 맞춰야 합니다.

| 정렬 | `CURSOR_VALUE` 형식 |
| --- | --- |
| `LATEST` | `2026-07-27T10:15:30` 같은 ISO local datetime |
| `RECOMMEND` | 추천 순서 숫자 |
| `DISTANCE` | 거리 숫자 |
| `SAVED`, `REVIEW` | 카운트 숫자 |

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e CURSOR_SORT=LATEST \
  -e CURSOR_VALUE=2026-07-27T10:15:30 \
  -e CURSOR_ID=100 \
  load-test/scenarios/course-list.js
```

Docker 실행:

```bash
docker run --rm \
  -i \
  -v "$PWD/load-test:/scripts" \
  grafana/k6 run \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e TOKEN=xxxxx \
  /scripts/scenarios/course-list.js
```

단계별 지속 시간은 필요에 따라 조정할 수 있습니다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `STAGE_10_DURATION` | `30s` | 10 VUs까지 증가하는 시간 |
| `STAGE_30_DURATION` | `1m` | 30 VUs까지 증가하는 시간 |
| `STAGE_50_DURATION` | `1m` | 50 VUs까지 증가하는 시간 |
| `STAGE_100_DURATION` | `1m` | 100 VUs까지 증가하는 시간 |
| `RAMP_DOWN_DURATION` | `30s` | 0 VUs까지 감소하는 시간 |
| `SLEEP_SECONDS` | `1` | VU별 요청 사이 대기 시간 |

테스트 종료 후 k6 summary에서 다음 지표를 확인합니다.

| 지표 | k6 summary 항목 | 확인 기준 |
| --- | --- | --- |
| RPS | `http_reqs` rate | 목표 트래픽 대비 처리량이 충분한지 확인 |
| 평균 응답 시간 | `http_req_duration avg` | 일반적인 사용자 체감 응답시간 확인 |
| p95 | `http_req_duration p(95)` | 상위 5% 지연 요청이 `MAX_RESPONSE_TIME_MS` 이내인지 확인 |
| 실패율 | `http_req_failed` | 기본 threshold `rate<0.01`, 즉 1% 미만인지 확인 |

## 새로운 시나리오 추가

`load-test/scenarios/` 아래에 API별 파일을 만들고 공통 설정을 import합니다.

```javascript
import http from 'k6/http';
import { sleep } from 'k6';
import { commonOptions } from '../config/options.js';
import { apiUrl } from '../config/env.js';
import { requestParams } from '../utils/headers.js';
import { checkJsonResponse } from '../utils/checks.js';

export const options = commonOptions;

export default function () {
  const response = http.get(apiUrl('/api/v1/your-api'), requestParams());

  checkJsonResponse(response);
  sleep(1);
}
```

단계형 부하 테스트가 필요하면 `commonOptions` 대신 `stagedOptions`를 사용합니다.

```javascript
import { stagedOptions } from '../config/options.js';

export const options = stagedOptions;
```
