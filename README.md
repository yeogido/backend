# 여기도

지역의 숨은 명소와 행사, 소상공인 공간을 발견하고  
나만의 여행 코스를 공유하며 여행의 추억을 기록하는 **지역 기반 여행 플랫폼**입니다.

---

## ✨ 주요 기능

### 추천 코스
  공식 추천 코스와 사용자가 직접 만든 우리 동네 추천 코스를 조회하고 공유할 수 있습니다.

### 문화 콘텐츠
  지역별 축제, 공연, 전시 등의 정보를 제공하며 한국관광공사 TourAPI와 연동합니다.

### 여행 기록
  여행 지역, 기간, 사진을 기록하고 스티커를 활용해 여행 기록을 꾸밀 수 있습니다.

### 소상공인 홍보
  사업자 인증을 거쳐 지역 가게의 정보와 홍보 콘텐츠를 등록하고 관리할 수 있습니다.

---

## 🛠️ Tech Stack

### Backend

- Java 21
- Spring Boot
- Spring Data JPA
- Spring Security
- QueryDSL
- Flyway
- Gradle

### Database & Cache

- MySQL
- Redis

### Infrastructure

- AWS EC2
- Amazon RDS
- Amazon S3
- Amazon CloudFront
- AWS Lambda
- Nginx
- Docker
- Docker Hub

### CI/CD

- GitHub Actions

### External API

- 한국관광공사 TourAPI
- 국세청 사업자등록정보 진위확인 API

---

## 🏗️ Infrastructure

```text
Client
  │
  ├── API ──> Nginx ──> Spring Boot ──> RDS(MySQL)
  │                         │
  │                         └── Redis
  │
  └── Image ────────────────> S3 ──> CloudFront
```

### EC2 · Nginx · Docker

AWS EC2 Ubuntu 환경에서 서버를 운영하며, Nginx가 HTTPS 요청을 받아 Spring Boot로 전달하는 Reverse Proxy 역할을 합니다.

Spring Boot와 Redis는 Docker 컨테이너로 실행하여 개발 환경과 서버 환경의 차이를 줄였습니다.

### Amazon RDS

MySQL은 EC2와 분리하여 Amazon RDS로 운영하고 있으며, Flyway를 이용해 데이터베이스 스키마 변경 이력을 관리합니다.

### Redis

추천 코스 인기 랭킹과 캐싱 등에 사용하며, 현재 서비스 규모를 고려해 EC2 내부 Docker 컨테이너로 운영합니다.

### S3 · CloudFront

이미지는 Presigned URL을 이용해 클라이언트에서 S3로 직접 업로드합니다.

애플리케이션 서버가 이미지 파일을 직접 처리하지 않도록 구성했으며, 저장된 이미지는 CloudFront CDN을 통해 제공합니다.

### Lambda

Presigned URL 발급 후 실제 콘텐츠에 사용되지 않은 `temp/` 이미지를 일정 주기로 정리합니다.

### External API

외부 API의 인증 정보가 클라이언트에 노출되지 않도록 Spring Boot 서버에서 직접 호출하고 필요한 형태로 가공합니다.

- 한국관광공사 TourAPI
- 국세청 사업자등록정보 진위확인 API

---

## 🚀 CI/CD

GitHub Actions를 이용해 빌드, 테스트 및 배포 과정을 자동화했습니다.

### CI

```text
Pull Request
    ↓
Build & Test
    ↓
Code Review
    ↓
Merge
```

`develop` 브랜치를 대상으로 Pull Request가 생성되면 자동으로 빌드와 테스트를 수행합니다.

### CD

```text
develop Merge
      ↓
Docker Image Build
      ↓
Docker Hub Push
      ↓
EC2 Pull
      ↓
Deploy
```

현재는 빠른 개발과 QA를 위해 `develop` 브랜치에 Merge되면 개발 서버로 자동 배포합니다.

`main` 브랜치에는 개발 및 QA를 거쳐 안정성이 확인된 코드만 반영합니다.

---

## ❤️ Health Check

Spring Boot Actuator와 GitHub Actions를 이용해 서버 상태를 주기적으로 확인합니다.

```text
Health Check
     ↓
실패 시 재시도
     ↓
최종 실패
     ↓
Discord 알림
```

일시적인 네트워크 오류를 장애로 판단하지 않도록 재시도한 뒤, 지속적으로 실패하는 경우 Discord Webhook으로 알림을 전송합니다.

---

## 🔀 Git Convention

Git Flow를 기반으로 브랜치를 관리합니다.

### Branch

| Branch | Description |
| --- | --- |
| `main` | 안정성이 확인된 코드 |
| `develop` | 개발 통합 및 개발 서버 배포 |
| `feat/*` | 기능 개발 |
| `fix/*` | 버그 수정 |
| `hotfix/*` | 긴급 수정 |
| `refactor/*` | 코드 구조 개선 |
| `docs/*` | 문서 작업 |
| `chore/*` | 설정 및 기타 작업 |

### Branch Naming

```text
타입/이슈번호-작업내용
```

예시

```text
feat/11-course-create
fix/14-login-error
docs/35-api-spec
chore/40-deploy-config
```

---

## 💬 Commit Convention

```text
타입: 작업 내용 #이슈번호
```

예시

```text
feat: 추천 코스 생성 기능 추가 #11
fix: 로그인 오류 수정 #14
docs: README 작성 #35
chore: 배포 환경 설정 추가 #40
```

| Type | Description |
| --- | --- |
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `hotfix` | 긴급 오류 수정 |
| `refactor` | 코드 구조 개선 |
| `docs` | 문서 작업 |
| `test` | 테스트 코드 |
| `chore` | 설정·빌드 등 기타 작업 |

---

## 🔃 Development Flow

```text
Issue
  ↓
Branch
  ↓
Development
  ↓
Commit
  ↓
Pull Request
  ↓
CI
  ↓
Code Review
  ↓
Merge
  ↓
Deploy
```

- 가능한 경우 작업 전 Issue를 생성합니다.
- `main`, `develop` 브랜치에는 직접 Push하지 않습니다.
- 작업 완료 후 `develop`을 대상으로 Pull Request를 생성합니다.
- 모든 PR은 최소 1명 이상의 Approve 후 Merge합니다.
- Merge 완료 후 작업 브랜치는 삭제합니다.
- PR에는 관련 Issue를 `Resolves: #이슈번호` 형식으로 연결합니다.