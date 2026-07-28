# Load Test

Spring Boot API를 대상으로 k6 부하 테스트를 작성하기 위한 공통 환경입니다. 이 디렉터리는 아직 특정 API 시나리오를 포함하지 않으며, API별 테스트는 이후 `scenarios/`에 별도 파일로 추가합니다.

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
│   └── .gitkeep      # 이후 API별 테스트 파일을 추가할 위치
├── README.md
└── .gitignore
```

## 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | 테스트 대상 Spring Boot API 주소 |
| `ACCESS_TOKEN` | 빈 값 | Bearer 인증 토큰. 값이 있으면 `Authorization` 헤더에 포함 |
| `VUS` | `10` | 동시 가상 사용자 수 |
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
  -e ACCESS_TOKEN=xxxxx \
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

