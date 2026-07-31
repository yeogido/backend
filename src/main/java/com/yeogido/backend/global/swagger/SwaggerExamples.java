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
              "monthStart": 4,
              "monthEnd": 10,
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
          "monthStart": 5,
          "monthEnd": 9,
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

    public static final String COURSE_DETAIL = """
        {
          "isSuccess": true,
          "code": "COMMON200",
          "message": "요청에 성공했습니다.",
          "result": {
            "courseId": 9,
            "courseType": "OFFICIAL",
            "title": "부산 야경 여행",
            "thumbnailUrl": "https://yeogido-images-174132708084-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/courses/thumbnail/abcd1234.jpg",
            "description": "부산의 야경과 축제를 함께 즐길 수 있는 코스입니다.",
            "tags": [
              "야경",
              "드라이브"
            ],
            "durationType": "DAY_TRIP",
            "transportType": "CAR",
            "startMonth": 4,
            "endMonth": 10,
            "companionType": "FRIEND",
            "isLiked": false,
            "courseItems": [
              {
                "order": 1,
                "type": "PLACE",
                "placeId": 12,
                "isLiked": false,
                "source": "KAKAO",
                "externalPlaceId": "123456",
                "name": "광안리 해수욕장",
                "roadAddress": "부산광역시 수영구 광안해변로 219",
                "lotAddress": "부산광역시 수영구 광안동 192-20",
                "latitude": 35.1531698,
                "longitude": 129.118666,
                "imageKey": "courses/place/gwangalli.jpg",
                "imageUrl": "https://yeogido-images-174132708084-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/courses/place/gwangalli.jpg"
              },
              {
                "order": 2,
                "type": "CONTENT",
                "contentId": 3,
                "isLiked": false,
                "contentStatus": "AFTER",
                "source": "KAKAO",
                "externalPlaceId": "987654",
                "name": "부산 불꽃축제",
                "roadAddress": "부산광역시 수영구 광안해변로 219",
                "lotAddress": "부산광역시 수영구 광안동",
                "latitude": 35.1531698,
                "longitude": 129.118666,
                "imageUrl": "https://yeogido-images-174132708084-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/contents/fireworks.jpg"
              }
            ]
          }
        }
        """;
}
