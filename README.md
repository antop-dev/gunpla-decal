# 건담 메뉴얼 데칼 관리 시스템

건담프라 조립 메뉴얼 PDF를 업로드하고, 데칼 위치를 직접 마킹하여 관리할 수 있는 웹 애플리케이션입니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 | Kotlin 1.9.25 |
| 프레임워크 | Spring Boot 3.5 |
| 보안 | Spring Security (폼 로그인 + 캡차 인증) |
| DB | SQLite + Spring Data JPA + Flyway |
| PDF 처리 | Apache PDFBox 3.0 |
| AI 인식 | OpenAI GPT-4o mini |
| 템플릿 | Thymeleaf |
| 프론트엔드 | Tailwind CSS, PDF.js, Font Awesome |

## 주요 기능

### 뷰어 (일반 사용자)
- 건담프라 등급(HG / RG / MG / PG / ETC) 및 제품명·형번으로 메뉴얼 검색
- PDF 메뉴얼 뷰어 (페이지 이동, 확대/축소, 썸네일 스트립)
- 썸네일 스트립: 서버에서 사전 생성된 PNG 이미지로 즉시 표시
- PDF 페이지 위에 데칼 마커 오버레이 표시 (원 / 사각형)
- 데칼 번호 목록 사이드바 (검색 가능)
- Base62 단축 URL로 메뉴얼 직접 공유 (예: `/4S`)

### 관리자 (`/admin`)
- 관리자 계정 로그인 (캡차 인증 포함)
- PDF 메뉴얼 등록 / 수정 / 삭제
  - 파일 업로드 또는 URL 지정 (최대 50MB)
  - 등록 시 각 페이지 썸네일 자동 생성 (높이 68px PNG)
  - 시작 시 썸네일 누락 메뉴얼 자동 복구
- 메뉴얼 공개 / 비공개 토글
- 외부 링크 등록 (https:// 필수)
- PDF 페이지를 클릭하여 데칼 위치 마킹
  - 데칼 번호, 배경색(HEX 코드), 도형(원/사각형) 지정
  - 데칼 마커 수정 / 삭제
- AI(OpenAI GPT-4o mini)를 이용한 데칼 번호 자동 인식

## 실행 방법

### 사전 요구사항
- JDK 17 이상

### 개발 환경 실행

```bash
./gradlew bootRun
```

서버가 시작되면 http://localhost:8080 으로 접속합니다.  
데이터베이스 파일(`gunpla.db`)과 업로드 디렉터리(`uploads/`)는 실행 위치에 자동 생성됩니다.  
시작 시 PDF는 있으나 썸네일이 없는 메뉴얼은 자동으로 썸네일을 생성합니다.

### 초기 관리자 계정

최초 실행 시 Flyway 마이그레이션으로 아래 계정이 자동 생성됩니다.

| 항목 | 값 |
|------|----|
| 아이디 | `admin` |
| 비밀번호 | `admin` |

> **주의:** 운영 환경에서는 반드시 비밀번호를 변경하세요.

### 빌드

```bash
./gradlew clean build
```

## 설정

`application.yml` 또는 환경변수로 주입합니다.

| 환경변수 | 설명 | 기본값 |
|---------|------|--------|
| `DB_PATH` | SQLite 데이터베이스 파일 경로 | `./gunpla.db` |
| `UPLOAD_DIR` | PDF·썸네일 업로드 저장 경로 | `./uploads` |
| `OPENAI_KEY` | OpenAI API 키 (AI 인식 기능 사용 시) | — |

### 프로덕션 프로파일 (`prd`)

```bash
SPRING_PROFILES_ACTIVE=prd ./gradlew bootRun
```

| 항목 | 값 |
|------|-----|
| 포트 | `10001` |
| 컨텍스트 경로 | `/gunpla` |

## API

### 공통 파일 API (인증 불필요)

| 메서드 | 경로 | 설명 |
|-------|------|------|
| `GET` | `/manuals/{id}/pdf` | PDF 파일 스트리밍 |
| `GET` | `/manuals/{id}/thumbnails` | 썸네일 URL 목록 반환 |
| `GET` | `/manuals/{id}/thumbnails/{page}` | 특정 페이지 썸네일 PNG |

### 뷰어 API

| 메서드 | 경로 | 설명 |
|-------|------|------|
| `GET` | `/api/manuals` | 메뉴얼 목록 조회 (`?q=검색어`, 공개만) |
| `GET` | `/api/manuals/{id}` | 메뉴얼 상세 + 데칼 목록 (공개만) |
| `GET` | `/api/manuals/b/{b62id}` | 메뉴얼 상세 — Base62 단축 ID (공개만) |

### 관리자 API

| 메서드 | 경로 | 설명 |
|-------|------|------|
| `GET` | `/api/admin/manuals` | 메뉴얼 전체 목록 (미공개 포함) |
| `POST` | `/api/admin/manuals` | 메뉴얼 등록 (multipart: grade, modelNumber, productName, pdf/pdfUrl, link) |
| `GET` | `/api/admin/manuals/{id}` | 메뉴얼 단건 조회 (미공개 포함) |
| `PUT` | `/api/admin/manuals/{id}` | 메뉴얼 정보 수정 |
| `PATCH` | `/api/admin/manuals/{id}/published` | 공개 여부 토글 |
| `DELETE` | `/api/admin/manuals/{id}` | 메뉴얼 삭제 (데칼 + PDF + 썸네일 파일 포함) |
| `POST` | `/api/admin/manuals/{manualId}/decals` | 데칼 추가 |
| `PUT` | `/api/admin/manuals/{manualId}/decals/{decalId}` | 데칼 수정 |
| `DELETE` | `/api/admin/manuals/{manualId}/decals/{decalId}` | 데칼 삭제 |
| `POST` | `/api/admin/ai/recognize` | AI 데칼 번호 인식 |
| `GET` | `/api/admin/ai/available` | OpenAI 사용 가능 여부 확인 |

## 데이터베이스

SQLite를 사용하며, Flyway가 애플리케이션 시작 시 자동으로 마이그레이션을 적용합니다.

```
manual (메뉴얼)
├── id           INTEGER PK AUTOINCREMENT
├── grade        TEXT              -- HG / RG / MG / PG / ETC
├── model_number TEXT              -- 형번 (예: RX-78-2)
├── product_name TEXT
├── pdf_path     TEXT              -- 업로드된 PDF 파일 경로
├── link         TEXT NULL         -- 외부 링크 (https:// 필수, 선택)
├── published    INTEGER           -- 공개 여부 (0/1)
├── created_at   DATETIME
└── updated_at   DATETIME

manual_thumbnail (메뉴얼 페이지 썸네일)
├── id           INTEGER PK AUTOINCREMENT
├── manual_id    INTEGER FK → manual.id (ON DELETE CASCADE)
├── page_number  INTEGER           -- 페이지 번호 (1부터 시작)
└── file_path    TEXT              -- PNG 파일 경로 ({uuid}.{page:02d}.png)

decal (데칼 위치)
├── id           INTEGER PK AUTOINCREMENT
├── manual_id    INTEGER FK → manual.id (ON DELETE CASCADE)
├── page_number  INTEGER
├── decal_number TEXT              -- 데칼 번호 (숫자 1~3자리, 영문 대문자, 히라가나/카타카나)
├── x            REAL              -- 페이지 내 X 좌표 비율 (0~100)
├── y            REAL              -- 페이지 내 Y 좌표 비율 (0~100)
├── color        TEXT              -- HEX 색상 코드 (예: #ffffff)
├── shape        TEXT              -- CIRCLE / RECTANGLE
└── created_at   DATETIME

admin (관리자 계정)
├── id           INTEGER PK AUTOINCREMENT
├── username     TEXT UNIQUE
├── password     TEXT              -- Spring Security DelegatingPasswordEncoder 형식
└── created_at   DATETIME
```
