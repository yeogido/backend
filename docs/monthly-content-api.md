# 캘린더 행사 조회 API (#410)

`GET /api/v1/contents/calendar?startDate=2026-10-01&endDate=2026-10-31`

인증 없이 조회할 수 있습니다. 로그인하면 현재 사용자의 좋아요 여부를 반환합니다.

| 쿼리 파라미터 | 타입 | 필수 | 형식 및 범위 |
| --- | --- | --- | --- |
| startDate | LocalDate | O | yyyy-MM-dd, 연도 1~9999 |
| endDate | LocalDate | O | yyyy-MM-dd, 시작일 이상, 연도 1~9999 |

모든 조회는 날짜 두 개로 통일합니다. year·month 파라미터는 더 이상 지원하지 않습니다.

- 월별: 프론트에서 해당 월의 첫날·말일을 계산해 전달합니다.
- 하루: startDate·endDate에 같은 날짜를 전달합니다.
- 기간: 선택한 시작일·종료일을 전달합니다. 월·연도를 넘어도 허용합니다.

하루 선택:
`GET /api/v1/contents/calendar?startDate=2026-10-15&endDate=2026-10-15`

기간 드래그 선택:
`GET /api/v1/contents/calendar?startDate=2026-10-10&endDate=2026-11-02`

양 끝 날짜를 포함해 하루라도 겹치는 행사를 반환합니다.
조회 조건은 `행사 시작일 <= 조회 종료일 AND 행사 종료일 >= 조회 시작일`입니다.
응답의 startDate·endDate는 요청한 조회 기간입니다. year·month는 응답에서도 제거했습니다.
게시 상태가 `PUBLISHED`인 콘텐츠만 반환하며, 시작일이나 종료일이 없는 행사는 제외합니다.
현재 날짜 기준의 예정/진행 중 필터는 적용하지 않으므로 과거 월의 행사도 조회할 수 있습니다.
정렬과 카테고리를 선택할 수 있으며 페이징 없이 선택 기간의 전체 목록을 반환합니다.

| 선택 파라미터 | 설명 |
| --- | --- |
| category | FESTIVAL(행사), EXPERIENCE(체험), EXHIBITION(전시), PERFORMANCE(공연). 생략 시 전체 |
| sort | RECOMMEND(추천순, 기본), LIKE(저장순), DISTANCE(거리순), DEADLINE(종료 임박순) |
| regionId | 선택 지역과 하위 지역 필터. GPS 좌표가 있는 거리순은 지역 제한을 적용하지 않음 |
| latitude | 거리순 기준 위도 (-90~90), longitude와 함께 전달 |
| longitude | 거리순 기준 경도 (-180~180), latitude와 함께 전달 |

추천순은 추천 우선순위가 0이 아닌 행사를 우선하고 우선순위 오름차순으로 정렬합니다.
저장순은 좋아요 수 내림차순, 종료 임박순은 종료일 오름차순입니다.
거리순은 기존 목록과 같은 좌표 차이 제곱합을 사용하며 위도·경도 또는 지역 중심 좌표를 기준으로 합니다.
각 정렬에서 동점이면 콘텐츠 ID 오름차순입니다. 기준 좌표와 지역 ID가 모두 없거나 지역 중심 좌표가 없으면 거리순은 400입니다.
잘못된 카테고리/정렬 값, 위도·경도 한쪽 누락, 비정상/범위 밖 좌표는 400입니다.
없는 지역 ID는 지역 오류(404)를 반환합니다.

예: `GET /api/v1/contents/calendar?startDate=2026-10-01&endDate=2026-10-31&category=EXHIBITION&sort=DEADLINE`
거리순 예: `GET /api/v1/contents/calendar?startDate=2026-10-01&endDate=2026-10-31&sort=DISTANCE&latitude=37.5665&longitude=126.9780`

성공 응답(HTTP 200) 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "요청에 성공했습니다.",
  "result": {
    "startDate": "2026-10-01",
    "endDate": "2026-10-31",
    "totalCount": 1,
    "contents": [{
      "contentId": 1,
      "title": "가을 축제",
      "category": "FESTIVAL",
      "thumbnailImageUrl": "https://example.com/image.jpg",
      "regionName": "서울특별시",
      "hashtags": ["가을"],
      "likeCount": 0,
      "isLiked": false,
      "startDate": "2026.09.30",
      "endDate": "2026.10.02"
    }]
  }
}
```

날짜는 조회 월에 맞춰 자르지 않고 실제 행사 기간을 `yyyy.MM.dd`로 반환합니다.
결과가 없으면 HTTP 200과 `totalCount: 0`, `contents: []`를 반환합니다.

필수 날짜 누락·날짜 역전·잘못된 형식·존재하지 않는 날짜·연도 범위 초과는 HTTP 400을 반환합니다.
요청 검증/바인딩 실패는 COMMON4001, 서비스에서 잘못된 날짜를 감지한 경우 COMMON4002를 사용합니다.

10월 조회 예: 9/30~10/1, 10/31~11/2, 9/1~11/1은 포함하며 9/30 종료 또는 11/1 시작 행사는 제외합니다.
조회 기간의 startDate·endDate는 yyyy-MM-dd, 행사 자체의 날짜는 기존 yyyy.MM.dd 형식을 유지합니다.
