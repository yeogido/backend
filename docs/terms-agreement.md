# 필수 약관 동의 연동 (#428)

## 범위

- 필수 약관 두 개: `SERVICE`(서비스 이용약관), `PRIVACY`(개인정보 수집·이용 동의).
- 마케팅, 위치 관련 동의는 포함하지 않는다.
- 이메일 가입 및 카카오·네이버 추가 프로필 완료 요청 모두 동의가 필수다.
- 기존 회원은 자동 동의 처리하지 않는다. 로그인 후 프론트가 `termsAgreementRequired`를 확인하여 동의 화면을 표시한다.
- 이번 변경은 기존 회원의 모든 API 사용을 차단하는 접근제어가 아니다. 토큰은 정상 발급하며 화면 이동은 프론트에서 구현해야 한다.

## API

| Method | URL | 인증 | 동작 |
| --- | --- | --- | --- |
| GET | `/api/v1/terms` | 불필요 | 현재 시행 중인 약관 전문·버전·시행일 조회 |
| GET | `/api/v1/users/me/terms` | Bearer JWT | 현재 약관에 대한 내 동의 여부·시각 조회 |
| PUT | `/api/v1/users/me/terms` | Bearer JWT | 기존 회원 최초 동의 또는 개정 약관 재동의 |

공통 응답 `isSuccess`, `code`, `message`, `result`를 유지한다.

약관 조회 `result`는 배열이다. 각 항목은 `id`, `type`, `version`, `title`, `content`, `required`, `effectiveAt`을 포함한다. `content`는 평문으로 표시하고 HTML로 해석하지 않는다.

동의 상태 `result` 예시:

```json
{
  "termsAgreementRequired": false,
  "agreements": [
    {"type": "SERVICE", "version": "1.0", "agreed": true, "agreedAt": "2026-11-02T12:00:00"},
    {"type": "PRIVACY", "version": "1.0", "agreed": true, "agreedAt": "2026-11-02T12:00:00"}
  ]
}
```

미동의 항목은 `agreed=false`, `agreedAt=null`이다. 약관 버전과 내용은 예시이며 실제 운영 문서와 시각으로 등록해야 한다.

### 회원가입 요청 변경 (호환성 주의)

`POST /api/v1/auth/signup` 및 `POST /api/v1/auth/social-signup/complete`의 기존 Body에 다음 필드를 추가한다. `PUT /api/v1/users/me/terms`도 같은 Body를 사용한다.

```json
{
  "agreements": [
    {"type": "SERVICE", "version": "1.0", "agreed": true},
    {"type": "PRIVACY", "version": "1.0", "agreed": true}
  ]
}
```

버전을 하드코딩하지 않고 `GET /terms`에서 표시한 버전을 보낸다. 누락·false·null·중복 종류·빈 버전은 거절한다. 요청 ID나 동의 시각을 클라이언트에서 받지 않는다.

### 로그인 응답 변경

이메일 로그인, 소셜 로그인, 소셜 가입 완료, 토큰 재발급의 `result`에 `termsAgreementRequired`를 추가한다. 기존 필드는 유지한다.

- 기존 회원 로그인 후 `true`: 현재 약관 조회 → 체크박스 입력 → `PUT /users/me/terms` → 동의 완료 후 원래 화면으로 이동.
- `false`: 기존 로그인 흐름 유지.
- 소셜 로그인 `isNewUser=true`: 추가 프로필 화면에서 약관도 표시하고 가입 완료 Body에 포함한다.
- 기존 로그인 세션은 `GET /users/me/terms`로 최신 동의 상태를 확인할 수 있다.
- 필수 항목 초기값은 체크 해제. 반복 로그인 시 이미 동의한 동일 버전에는 다시 동의를 요구하지 않는다.
- 개인정보 동의 전문에 실제 수집 항목·목적·보유기간을 확정해서 기재해야 한다.

## 오류

| HTTP | Code | 의미 |
| --- | --- | --- |
| 400 | TERMS4001 | 필수 약관 동의 누락·거절 또는 잘못된 동의 목록 |
| 409 | TERMS4091 | 조회했던 버전이 현재 시행 버전과 다름. 전문 재조회 및 재동의 필요 |
| 503 | TERMS5031 | 시행 중인 필수 약관 두 개가 모두 준비되지 않음 |

인증 실패·탈퇴 사용자·JSON 형식 오류 등은 기존 공통 처리를 따른다.

## 데이터와 운영

`V40`은 `terms_document`와 `user_terms_agreement`만 생성한다. 승인되지 않은 약관 전문이나 기존 회원의 가짜 동의 기록은 삽입하지 않는다.

배포 전 운영팀이 확정한 두 약관을 DB에 등록해야 한다. 필드는 `type`, `version`, `title`, `content`, `effective_at`이다. 서버와 DB 운영 시간대에 맞춰 시행일을 입력한다. 각 종류에서 현재 시각 이전의 가장 최근 시행 버전을 선택한다.

- 두 약관 중 하나라도 없으면 조회·가입·동의는 `TERMS5031`을 반환한다.
- 이때 기존 회원 로그인은 가능하지만 `termsAgreementRequired=true`다.
- 운영 배포 시 마이그레이션 적용 → 승인된 전문 등록 → 공개 약관 API 확인 → 프론트 가입·기존 회원 동의 화면 연동 확인 순서로 검증한다. 준비 전 배포하면 신규 가입이 차단된다.
- 약관 개정은 새로운 버전의 행을 추가한다. 동의한 기록이 참조하는 기존 전문·버전·시행일을 수정하거나 삭제하지 않는다.
- 같은 사용자의 같은 버전 동의 재시도는 최초 동의 시각을 유지한다. 기존 회원 동의는 사용자 행 잠금으로 중복 요청을 직렬화한다.
- 회원가입과 동의 저장은 동일 DB 트랜잭션에서 수행한다. 가입 실패 시 동의 기록도 롤백된다.
- 현재 회원 탈퇴는 soft delete 정책을 유지한다. 동의 기록에 원문·버전 참조가 남는다. 최종 보유기간 및 파기 정책은 운영팀에서 확정해야 한다.

## 검증

```sh
./gradlew test --tests '*TermsIntegrationTest' --tests '*TermsControllerTest'
./gradlew test
```

서비스·DB 테스트에서는 테스트 전용 약관을 생성한다. 실제 운영 전문으로 사용하지 않는다.
