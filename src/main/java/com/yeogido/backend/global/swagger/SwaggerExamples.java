package com.yeogido.backend.global.swagger;

public final class SwaggerExamples {

    private SwaggerExamples() {
    }

    public static final String COURSE_CREATE = """
            {
              "title": "부산 야경 여행",
              "regionId": 2,
              "description": "부산의 야경과 축제를 함께 즐길 수 있는 코스입니다.",
              "durationType": "DAY_TRIP",
              "transportType": "CAR",
              "companionType": "FRIEND",
              "thumbnailKey": "courses/thumbnail/abcd1234.jpg",
              "hashtagIds": [1, 3],
              "courseItems": [
                {
                  "order": 1,
                  "type": "PLACE",
                  "externalPlaceId": "123456",
                  "categoryGroupCode": "AT4",
                  "name": "광안리 해수욕장",
                  "roadAddress": "부산 수영구 광안해변로 219",
                  "lotAddress": "부산 수영구 광안동 192-20",
                  "latitude": 35.1531698,
                  "longitude": 129.118666,
                  "imageKey": "courses/place/gwangalli.jpg"
                },
                {
                  "order": 2,
                  "type": "CONTENT",
                  "contentId": 1
                }
              ]
            }
            """;
}