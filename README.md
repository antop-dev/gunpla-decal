# 건담 메뉴얼 데칼 관리 시스템

건담프라 조립 메뉴얼 PDF를 업로드하고, 데칼 위치를 직접 마킹하여 관리할 수 있는 웹 애플리케이션입니다.

## 주요 기능

### 뷰어 (일반 사용자)
- 건담프라 등급(HG/RG/MG/PG) 및 제품명으로 메뉴얼 검색
- PDF 메뉴얼 뷰어 (페이지 이동, 확대/축소, 썸네일 스트립)
- PDF 페이지 위에 데칼 마커 오버레이 표시
- 데칼 번호 목록 사이드바 (검색 가능)

### 관리자 (`/admin`)
- 관리자 계정 로그인 (캡차 인증 포함)
- PDF 메뉴얼 등록 / 수정 / 삭제
- PDF 페이지를 클릭하여 데칼 위치 마킹
- 데칼 번호, 배경색(흰색/검정), 도형(원/사각형) 지정
- 데칼 마커 수정 / 삭제

## 실행 방법

### 사전 요구사항
- JDK 17 이상

### 개발 환경 실행

```bash
./gradlew bootRun
```

서버가 시작되면 http://localhost:8080 으로 접속합니다.  
데이터베이스 파일(`gunpla.db`)과 업로드 디렉터리(`uploads/`)는 실행 위치에 자동 생성됩니다.

### 빌드

```bash
./gradlew clean build
```

## 설정

`application.yml` 또는 환경변수로 주입합니다.

| 환경변수 | 설명 | 기본값 |
|---------|------|--------|
| `DB_PATH` | SQLite 데이터베이스 파일 경로 | `./gunpla.db` |
| `UPLOAD_DIR` | PDF 업로드 저장 경로 | `./uploads` |

## API

### 뷰어 API

| 메서드 | 경로 | 설명 |
|-------|------|------|
| `GET` | `/api/manuals` | 메뉴얼 목록 조회 (`?q=검색어`) |
| `GET` | `/api/manuals/{id}` | 메뉴얼 상세 + 데칼 목록 |
| `GET` | `/api/manuals/{id}/pdf` | PDF 파일 스트리밍 |

### 관리자 API

| 메서드 | 경로 | 설명 |
|-------|------|------|
| `GET` | `/api/admin/manuals` | 메뉴얼 전체 목록 |
| `POST` | `/api/admin/manuals` | 메뉴얼 등록 (multipart: grade, productName, pdf) |
| `PUT` | `/api/admin/manuals/{id}` | 메뉴얼 수정 |
| `DELETE` | `/api/admin/manuals/{id}` | 메뉴얼 삭제 (데칼 + PDF 파일 포함) |
| `POST` | `/api/admin/manuals/{manualId}/decals` | 데칼 추가 |
| `PUT` | `/api/admin/manuals/{manualId}/decals/{decalId}` | 데칼 수정 |
| `DELETE` | `/api/admin/manuals/{manualId}/decals/{decalId}` | 데칼 삭제 |

## 데이터베이스

SQLite를 사용하며, Flyway가 애플리케이션 시작 시 자동으로 마이그레이션을 적용합니다.

```
manual (메뉴얼)
├── id           INTEGER PK AUTOINCREMENT
├── grade        TEXT              -- HG / RG / MG / PG / ETC
├── model_number TEXT              -- 형번 (예: RX-78-2)
├── product_name TEXT
├── pdf_path     TEXT              -- 업로드된 PDF 파일 경로
├── link         TEXT NULL         -- 외부 링크 (선택)
├── created_at   DATETIME
└── updated_at   DATETIME

decal (데칼 위치)
├── id           INTEGER PK AUTOINCREMENT
├── manual_id    INTEGER FK → manual.id (ON DELETE CASCADE)
├── page_number  INTEGER
├── decal_number TEXT              -- 데칼 번호 (예: 1, A, ア)
├── x            REAL              -- 페이지 내 X 좌표 비율 (0~1)
├── y            REAL              -- 페이지 내 Y 좌표 비율 (0~1)
├── color        TEXT              -- WHITE / BLACK
├── shape        TEXT              -- CIRCLE / RECTANGLE
└── created_at   DATETIME

admin (관리자 계정)
├── id           INTEGER PK AUTOINCREMENT
├── username     TEXT UNIQUE
├── password     TEXT              -- Spring Security DelegatingPasswordEncoder 형식
└── created_at   DATETIME
```
