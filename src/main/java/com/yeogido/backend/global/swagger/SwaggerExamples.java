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

    public static final String COURSE_UPDATE = """
        {
          "title": "부산 감성 여행 코스",
          "description": "광안리와 해운대를 중심으로 감성 카페와 야경을 즐기는 코스입니다.",
          "thumbnailKey": "courses/thumbnail/busan-emotion.jpg",
          "hashtagIds": [2, 4, 6],
          "courseItems": [
            {
              "order": 1,
              "type": "PLACE",
              "externalPlaceId": "654321",
              "categoryGroupCode": "CE7",
              "name": "흰여울문화마을",
              "roadAddress": "부산 영도구 흰여울길 379",
              "lotAddress": "부산 영도구 영선동4가",
              "latitude": 35.078961,
              "longitude": 129.045771,
              "imageKey": "courses/place/huinnyeoul.jpg"
            },
            {
              "order": 2,
              "type": "CONTENT",
              "contentId": 8
            },
            {
              "order": 3,
              "type": "PLACE",
              "externalPlaceId": "987654",
              "categoryGroupCode": "AT4",
              "name": "해운대해수욕장",
              "roadAddress": "부산 해운대구 해운대해변로 264",
              "lotAddress": "부산 해운대구 우동",
              "latitude": 35.158698,
              "longitude": 129.160384,
              "imageKey": null
            }
          ]
        }
        """;
}