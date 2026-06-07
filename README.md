# 건담 메뉴얼 데칼 관리 시스템

건담프라 조립 메뉴얼 PDF를 업로드하고, 데칼 위치를 직접 마킹하여 관리할 수 있는 웹 애플리케이션입니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 | Kotlin · JVM 17 |
| 프레임워크 | Spring Boot 3.5 |
| 보안 | Spring Security (폼 로그인 + 캡차 인증) |
| DB | SQLite + Spring Data JPA + Flyway |
| PDF 처리 | Apache PDFBox 3.0 |
| AI 인식 | OpenAI GPT-4o mini |
| 템플릿 | Thymeleaf (다국어: 한국어 · 일본어 · 중국어 · 영어) |
| 프론트엔드 | Tailwind CSS, PDF.js, Font Awesome |
| SEO | sitemap.xml, robots.txt, RSS/Atom 피드, OG 태그, JSON-LD |
| 분석 | Google Analytics 4 (선택) |

## 주요 기능

### 뷰어 (일반 사용자)

- 건담프라 등급(HG / RG / MG / PG / ETC) 및 형식번호·제품명으로 메뉴얼 검색
- PDF 뷰어 — 페이지 이동, 확대/축소, 드래그 패닝
- 썸네일 스트립 — 서버에서 사전 생성된 PNG 이미지로 즉시 표시
- 데칼 마커 오버레이 — 원(CIRCLE) / 사각형(SQUARE) / 다이아(DIAMOND) 도형 표시
- 데칼 번호 사이드바 — 검색·클릭 시 해당 마커로 이동 (같은 번호 순환)
- Base62 단축 URL로 메뉴얼 직접 공유 (예: `/4S`)
- 다국어 UI (브라우저 언어 자동 감지)

#### 키보드 단축키

| 키 | 동작 |
|----|------|
| `1` ~ `5` | 확대 100% ~ 500% |
| `M` | 데칼 마커 보이기/숨기기 |

### 관리자 (`/admin`)

- 관리자 계정 로그인 (캡차 인증 포함)
- PDF 메뉴얼 등록 / 수정 / 삭제
  - 파일 직접 업로드 또는 URL 지정 (최대 50 MB)
  - 등록 시 각 페이지 썸네일 자동 생성 (높이 68 px PNG)
  - 시작 시 썸네일이 없는 메뉴얼 자동 복구
- 메뉴얼 공개 / 비공개 토글 (PDF 뷰어 상단 바에서 직접 전환, 공개 시 녹색 표시)
- 외부 링크 등록 (https:// 필수)
- PDF 페이지를 클릭하여 데칼 위치 마킹
  - 데칼 번호, 배경색(HEX), 도형(원/사각형/다이아) 지정
  - 데칼 마커 수정 / 삭제
  - 데칼 수정·삭제 시 공개 메뉴얼 자동 비공개 전환
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
| `UPLOAD_DIR` | PDF·썸네일 저장 디렉터리 | `./uploads` |
| `BASE_URL` | 사이트 기본 URL (sitemap·피드 URL 생성에 사용) | `http://localhost:8080` |
| `OPENAI_KEY` | OpenAI API 키 (AI 인식 기능, 미설정 시 비활성) | — |
| `GA4_ID` | Google Analytics 4 측정 ID (미설정 시 비활성) | — |

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
| `GET` | `/manuals/{id}/thumbnails/{page}` | 특정 페이지 썸네일 PNG |

### 뷰어 API (인증 불필요, 공개 메뉴얼만)

| 메서드 | 경로 | 설명 |
|-------|------|------|
| `GET` | `/api/manuals` | 메뉴얼 목록 (`?q=검색어`) |
| `GET` | `/api/manuals/{id}` | 메뉴얼 상세 + 데칼 목록 + 썸네일 URL 목록 |

### 관리자 API (로그인 필요)

| 메서드 | 경로 | 설명 |
|-------|------|------|
| `GET` | `/api/admin/manuals` | 메뉴얼 전체 목록 (미공개 포함) |
| `POST` | `/api/admin/manuals` | 메뉴얼 등록 (multipart: grade, modelNumber, productName, pdf/pdfUrl, link) |
| `GET` | `/api/admin/manuals/{id}` | 메뉴얼 단건 조회 (미공개 포함) |
| `PUT` | `/api/admin/manuals/{id}` | 메뉴얼 정보 수정 |
| `PATCH` | `/api/admin/manuals/{id}/published` | 공개 여부 토글 |
| `DELETE` | `/api/admin/manuals/{id}` | 메뉴얼 삭제 (데칼 + PDF + 썸네일 포함) |
| `POST` | `/api/admin/manuals/{manualId}/decals` | 데칼 추가 |
| `PUT` | `/api/admin/manuals/{manualId}/decals/{decalId}` | 데칼 수정 |
| `DELETE` | `/api/admin/manuals/{manualId}/decals/{decalId}` | 데칼 삭제 |
| `GET` | `/api/admin/ai/available` | OpenAI 사용 가능 여부 |
| `POST` | `/api/admin/ai/recognize` | AI 데칼 번호 인식 |

### SEO

| 경로 | 설명 |
|------|------|
| `/robots.txt` | 크롤러 접근 정책 |
| `/sitemap.xml` | 공개 메뉴얼 사이트맵 |
| `/rss.xml` | RSS 2.0 피드 |
| `/atom.xml` | Atom 1.0 피드 |

## 데이터베이스

SQLite를 사용하며, Flyway가 애플리케이션 시작 시 자동으로 마이그레이션을 적용합니다.

```
manual (메뉴얼)
├── id           INTEGER PK AUTOINCREMENT
├── grade        TEXT              -- HG / RG / MG / PG / ETC
├── model_number TEXT              -- 형식번호 (예: RX-78-2)
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
├── decal_number TEXT              -- 데칼 번호 (숫자·영문 대문자·히라가나·카타카나)
├── x            REAL              -- 페이지 내 X 좌표 비율 (0~100)
├── y            REAL              -- 페이지 내 Y 좌표 비율 (0~100)
├── color        TEXT              -- HEX 색상 코드 (예: #ffffff)
├── shape        TEXT              -- CIRCLE / SQUARE / DIAMOND
└── created_at   DATETIME

admin (관리자 계정)
├── id           INTEGER PK AUTOINCREMENT
├── username     TEXT UNIQUE
├── password     TEXT              -- Spring Security DelegatingPasswordEncoder 형식
└── created_at   DATETIME
```
