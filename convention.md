# 🔖 여기도 (Yeogido) 개발 컨벤션 (Convention)

전국 로컬 문화 아카이빙 플랫폼 **"여기도"** 백엔드 개발의 일관성과 생산성을 위한 개발 컨벤션 가이드입니다. 
이 가이드는 Git 브랜치 전략, 커밋/PR 규칙, 코드 스타일, 그리고 Spring Boot 및 JPA 백엔드 아키텍처 규칙을 포함합니다.

---

## 📌 1. 서비스 개요 (Service Overview)
- **서비스명**: 여기도 (Yeogido)
- **한줄 소개**: 전국 어디서든 지역에 상관없이 문화가 이어지고, 사람들이 로컬 경험을 발견하고 기록하는 지도 기반 문화 아카이빙 플랫폼
- **핵심 가치**:
  1. 수도권-비수도권 간 문화 향유 격차 해소
  2. 지도 기반 로컬 문화 및 여행 코스 큐레이션 (행사/전시/공방/카페/로컬브랜드 동선 연결)
  3. 사진 기반 아카이빙 및 공유를 통한 지속적인 지역 방문 동기 부여
  4. 지역 소상공인 및 로컬 콘텐츠 활성화

---

## 🌿 2. Git 브랜치 전략 (Git Flow)

기본적으로 **Git Flow** 전략을 변형하여 사용합니다.

### 브랜치 종류
| 브랜치명 | 설명 |
| :--- | :--- |
| `main` | 실제 서비스 배포가 가능한 최상위 브랜치 |
| `develop` | 개발이 진행되는 통합 브랜치 (모든 기능이 병합되는 기준 브랜치) |
| `feat/*` | 새로운 기능 개발을 위한 브랜치 |
| `fix/*` | 버그 수정을 위한 브랜치 |
| `refactor/*` | 코드 개선 및 리팩토링을 위한 브랜치 |
| `docs/*` | 문서 작업 및 수정 (README, 컨벤션 등)을 위한 브랜치 |
| `chore/*` | 설정, 빌드 시스템, 패키지 의존성 등 기타 작업을 위한 브랜치 |

> [!IMPORTANT]
> - `main`과 `develop` 브랜치에 직접 `push`하는 것은 금지되어 있습니다.
> - 항상 작업용 브랜치(`feat/*`, `fix/*` 등)를 생성하여 개발한 뒤 Pull Request(PR)를 통해 `develop` 브랜치로 병합합니다.

### 브랜치 네이밍 규칙
**`타입/이슈번호-작업내용`** (소문자와 하이픈`-`을 사용합니다.)
- **추천코스 생성 기능 개발**: `feat/11-course-create`
- **소상공인 홍보 등록 기능 개발**: `feat/13-promotion-create`
- **로그인 에러 수정**: `fix/14-login-error`

---

## 📝 3. 커밋 컨벤션 (Commit Convention)

커밋 메시지는 협업 과정에서 변경 내용을 쉽게 추적할 수 있도록 일정한 형식을 유지합니다.

### 커밋 메시지 규칙
**`타입: 작업내용 #이슈번호`** (콜론 뒤에는 한 칸 띄웁니다.)
- `feat: 코스 생성 기능 추가 #11`
- `feat: 소상공인 홍보 등록 기능 추가 #13`
- `fix: 로그인 실패 시 에러 메시지 수정 #14`
- `refactor: 코스 조회 쿼리 최적화 #22`
- `docs: 개발 컨벤션 문서 추가 #1`

---

## 🔀 4. Pull Request (PR) 및 코드 리뷰 규칙

### PR 생성 규칙
- 작업 완료 시 **본인이 작업한 브랜치에서 develop 브랜치로 PR을 생성**합니다.
- **PR 제목 규칙**: `[타입] 작업 내용`
  - 예: `[feat] 코스 생성 기능 구현`
  - 예: `[fix] 로그인 에러 수정`

### PR 본문 템플릿
PR 작성 시 아래 양식을 복사하여 구체적으로 기재합니다.
```markdown
## 💡 작업 내용
- 

## 🧪 테스트 내용
- 직접 확인한 테스트 내용을 작성 (Postman, JUnit 등 결과 이미지/텍스트)

## 📌 참고 사항
- 리뷰어가 알아야 할 주의사항이나 개발 중 고민한 부분
```

### 코드 리뷰 및 머지 규칙
- PR은 팀원 중 **최소 1명 이상의 리뷰 승인(Approve)**을 받아야 `merge`할 수 있습니다.
- 리뷰어가 확인해야 할 사항:
  - 기획 및 요구사항(이슈) 구현 완료 여부
  - 코드의 가독성 및 효율성
  - 적절한 예외 처리 및 로그 작성 여부
  - 네이밍 컨벤션 준수 여부
- 수정 요청(Request Changes)이 있을 경우, 작성자는 수정 코드를 push한 후 재리뷰를 요청합니다.
- 충돌(Conflict) 발생 시 **PR 작성자**가 로컬에서 해결한 뒤 push합니다.
- `merge` 완료 후, 작업에 사용한 로컬/원격 브랜치는 삭제합니다.

---

## 🎨 5. 코드 스타일 컨벤션 (General Code Style)

### 들여쓰기 (Indentation)
- **공백 2칸(2 spaces)**을 사용합니다. (탭 문자 사용 금지, IDE 설정 확인 필수)
- Java 코드 포맷 및 XML/JSON/YAML 파일 전체에 적용됩니다.

### 네이밍 규칙
- **변수명 / 함수명**: `camelCase` (예: `courseList`, `placeName`, `getCourseDetails`)
- **컴포넌트명 / 클래스명**: `PascalCase` (예: `CourseCard`, `PromotionForm`, `CourseController`)
- **상수명**: `UPPER_SNAKE_CASE` (예: `DEFAULT_PAGE_SIZE`, `MAX_IMAGE_COUNT`)
- **패키지명**: 모두 소문자 단어 연결 (예: `com.yeogido.backend.domain.course`)
- **파일명**: 역할이 직관적으로 드러나도록 명명합니다.

### 코드 청결성 (Code Cleanliness)
- 사용하지 않는 변수, 메서드, 클래스 및 `import`문은 빌드 전 반드시 제거합니다.
- 배포 전 불필요한 주석과 디버그용 출력 코드(예: `System.out.println`, `console.log`)는 완전히 제거하거나 적절한 로깅 라이브러리(`log.info`, `log.error`)로 교체합니다.
- 코드 포맷팅은 팀에서 합의한 IDE 포맷터 설정을 따릅니다. (Commit 전 `Ctrl + Alt + L` / `Cmd + Option + L` 수행 습관화)

---

## ☕ 6. 백엔드(Spring Boot & JPA) 전용 컨벤션

"여기도" 백엔드 아키텍처의 일관성을 높이기 위한 백엔드 개발자 전용 컨벤션입니다.

### 6.1. 패키지 및 레이어 아키텍처 구조
도메인 중심의 계층형 아키텍처(Domain-Driven Layered Structure)를 적용합니다.

```text
com.yeogido.backend
├── domain
│   ├── course           # 특정 도메인 단위
│   │   ├── controller   # REST API 컨트롤러 (@RestController)
│   │   ├── entity       # JPA 엔티티 (@Entity)
│   │   ├── enums        # 도메인 한정 Enum 파일
│   │   ├── dto          # Request/Response DTO (Java Record 권장)
│   │   ├── repository   # JPA/QueryDSL 레포지토리 인터페이스 및 구현체
│   │   └── service      # 비즈니스 로직 처리 계층 (@Service)
│   ├── user
│   ├── place
│   └── ...
└── global
    ├── config           # 글로벌 설정 파일 (Security, Swagger, QueryDsl 등)
    ├── common           # 공통 Response 및 유틸 클래스
    ├── entity           # 공통 엔티티 (BaseEntity 등)
    └── exception        # 예외 처리 관련 (GlobalExceptionHandler, ErrorCode 등)
```

### 6.2. JPA 및 엔티티(Entity) 설계 규칙

> [!IMPORTANT]
> 엔티티 설계 시 성능 및 코드 안전성을 위해 아래 규칙을 엄격하게 적용합니다.

1. **상속**: 모든 엔티티는 `BaseEntity`를 상속받아 `created_at` 및 `updated_at`을 자동으로 관리합니다.
2. **Lombok 사용 규칙**:
   - `@Getter`는 사용하되, 무분별한 `@Setter` 사용은 금지합니다. (필요 시 객체의 목적이 명확한 비즈니스 메서드로 수정 수행)
   - `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 기본 생성자를 제한하여 무분별한 객체 생성을 막습니다.
   - 빌더 패턴 사용을 위해 클래스 위에 `@Builder`와 `@AllArgsConstructor`를 함께 사용합니다.
3. **테이블 및 컬럼 매핑**:
   - `@Table(name = "테이블명")`은 데이터베이스 내 소문자 단수형 명칭을 지정합니다. (예: `name = "course"`, `name = "user"`)
   - 컬럼 이름은 데이터베이스 내 `snake_case`로 저장되도록 `@Column(name = "field_name")`으로 명시적으로 매핑하거나 규칙을 준수합니다.
4. **연관 관계 설정**:
   - `@ManyToOne`, `@OneToOne`과 같은 참조 연관 관계는 반드시 **`fetch = FetchType.LAZY`** (지연 로딩)로 설정하여 N+1 문제를 예방합니다.
   - 외래 키 컬럼명은 `@JoinColumn(name = "상대엔티티명_id")` 형식으로 명명합니다. (예: `name = "region_id"`)
   - `@Enumerated` 매핑 시 반드시 **`EnumType.STRING`**을 지정합니다. (`ORDINAL` 절대 사용 금지)
5. **예시 엔티티 코드**:
   ```java
   @Entity
   @Getter
   @Builder
   @NoArgsConstructor(access = AccessLevel.PROTECTED)
   @AllArgsConstructor
   @Table(name = "course")
   public class Course extends BaseEntity {
       @Id
       @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;

       @ManyToOne(fetch = FetchType.LAZY)
       @JoinColumn(name = "region_id", nullable = false)
       private Region region;

       @Column(nullable = false, length = 100)
       private String title;

       @Enumerated(EnumType.STRING)
       @Column(name = "course_type", nullable = false, length = 20)
       private CourseType courseType;
   }
   ```

### 6.3. API 공통 응답 규격 (ApiResponse)
모든 API 응답은 프론트엔드와의 원활한 협업을 위해 `ApiResponse<T>` 객체로 감싸서 반환합니다.

- **성공 응답 예시 (`ApiResponse.onSuccess(...)`)**:
  ```json
  {
    "isSuccess": true,
    "code": "COMMON200",
    "message": "요청에 성공했습니다.",
    "result": {
      "courseId": 11,
      "title": "구미 재즈 코스"
    }
  }
  ```
- **실패 응답 예시 (`ApiResponse.onFailure(...)`)**:
  ```json
  {
    "isSuccess": false,
    "code": "COMMON4001",
    "message": "잘못된 요청입니다.",
    "result": null
  }
  ```

### 6.4. 예외 처리 (Exception Handling)
- 예외 상황 발생 시 프런트엔드에게 시스템 예외나 StackTrace를 직접 노출해서는 안 됩니다.
- 비즈니스 예외는 반드시 custom 예외인 **`GeneralException`**을 발생시킵니다.
- 예외에 할당되는 에러 코드는 `GeneralErrorCode` 또는 각 도메인용 `ErrorCode` 구현체를 정의해 사용합니다.
- **예외 던지기 예시**:
  ```java
  throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
  ```
- 모든 예외는 `GlobalExceptionHandler`에 의해 잡혀서 정형화된 JSON 응답 규격으로 변환됩니다.

### 6.5. API 문서화 (Swagger) 컨벤션
개발된 API는 프론트엔드 작업자가 즉시 테스트하고 연동할 수 있도록 Swagger 어노테이션을 꼼꼼하게 작성합니다.
- `CourseController` 등 컨트롤러 클래스에 `@Tag(name = "...", description = "...")`를 붙입니다.
- 각 API 메서드에 `@Operation(summary = "...", description = "...")` 및 `@ApiResponse` 상세를 기재합니다.
- **컨트롤러 작성 예시**:
  ```java
  @Tag(name = "추천 코스 API", description = "추천 코스 생성, 조회, 수정 및 삭제 관련 API")
  @RestController
  @RequestMapping("/api/v1/courses")
  @RequiredArgsConstructor
  public class CourseController {
      
      @Operation(summary = "추천 코스 생성 API", description = "새로운 추천 코스를 생성합니다.")
      @PostMapping
      public ApiResponse<CourseResponseDto> createCourse(@Valid @RequestBody CourseRequestDto request) {
          CourseResponseDto result = courseService.createCourse(request);
          return ApiResponse.onSuccess(SuccessCode.CREATED, result);
      }
  }
  ```

### 6.6. 데이터베이스 마이그레이션 (Flyway)
- 데이터베이스 스키마(테이블, 컬럼 등) 변경 시, 로컬이나 서버 DB에서 직접 SQL 쿼리를 실행하여 수정하는 것을 엄격히 금지합니다.
- 모든 구조 변경은 반드시 Flyway 마이그레이션 스크립트 파일(`V2__xxx.sql`, `V3__xxx.sql` 등)을 신규로 생성하여 버전 관리를 수행합니다.
- 이미 실행된 과거의 스크립트 파일(예: `V1__init.sql`)은 체크섬 오류를 방지하기 위해 절대 수정하지 않습니다.

---
## 🚀 7. 프로젝트 실행 방법 (Project Setup Guide)

개발 환경을 설정하고 프로젝트를 로컬에서 구동하는 방법입니다.

1. **프로젝트 Clone**
   ```bash
   git clone <repository_url>
   ```
2. **`.env` 환경변수 설정**
   - 프로젝트 루트 디렉토리에 있는 `.env.example` 파일을 복사하여 `.env` 파일을 생성합니다.
   ```bash
   cp .env.example .env
   ```
3. **환경변수 입력**
   - 팀에서 공유받은 데이터베이스 계정 정보 및 외부 API 키 등의 환경 변수를 `.env` 파일에 기입합니다.
4. **Docker Desktop 실행**
   - 로컬 데이터베이스(MySQL, Redis 등) 컨테이너 구동을 위해 Docker Desktop을 실행합니다.
5. **Docker Compose 컨테이너 구동**
   - 백그라운드에서 데이터베이스 컨테이너를 구동합니다.
   ```bash
   docker compose up -d
   ```
6. **애플리케이션 실행**
   - IDE(IntelliJ IDEA 등)에서 [BackendApplication](file:///Users/minjun/학교/UMC/10기/여기도/backend/src/main/java/com/yeogido/backend/BackendApplication.java)을 실행합니다.
7. **정상 동작 확인**
   - 브라우저에서 `http://localhost:8080/swagger-ui/index.html`에 접속하여 API 명세가 로드되고 컨테이너들이 정상 연결되었는지 확인합니다.

---
> [!NOTE]
> 본 개발 컨벤션은 개발 진행 과정에서 팀원 합의 하에 추가 및 보완될 수 있습니다.

