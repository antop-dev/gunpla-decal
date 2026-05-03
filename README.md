# 건담 메뉴얼 데칼 관리 시스템

건담프라 조립 메뉴얼 PDF를 업로드하고, 데칼 위치를 직접 마킹하여 관리할 수 있는 웹 애플리케이션입니다.

## 주요 기능

### 뷰어 (일반 사용자)
- 건담프라 등급(HG/RG/MG/PG) 및 제품명으로 메뉴얼 검색
- PDF 메뉴얼 뷰어 (페이지 이동, 확대/축소, 썸네일 스트립)
- PDF 페이지 위에 데칼 마커 오버레이 표시
- 데칼 번호 목록 사이드바 (검색 가능)

### 관리자 (`/admin`)
- PDF 메뉴얼 등록 / 수정 / 삭제
- PDF 페이지를 클릭하여 데칼 위치 마킹
- 데칼 번호 및 배경색(흰색/검정) 지정
- 데칼 마커 수정 / 삭제

## 실행 방법

### 사전 요구사항
- JDK 17 이상

### 개발 환경 실행 (H2 인메모리 DB)

```bash
./gradlew bootRun
```

기본 프로파일은 `local-h2`입니다. 서버가 시작되면 http://localhost:8080 으로 접속합니다.

### 개발 환경 실행 (MariaDB)

로컬에 MariaDB가 설치되어 있어야 합니다. 데이터베이스와 사용자를 미리 생성하고 `application-local-mariadb.yml`의 접속 정보를 수정한 후 실행합니다.

```bash
SPRING_PROFILES_ACTIVE=local-mariadb ./gradlew bootRun
```

### 빌드

```bash
./gradlew clean build
```

## 설정

`application.yml` 또는 환경변수로 주입합니다.

| 환경변수 | 설명 | 기본값 |
|---------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | `local-h2` |
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

## 데이터베이스 스키마

Flyway가 애플리케이션 시작 시 자동으로 마이그레이션을 적용합니다.

```
manual (메뉴얼)
├── id           BIGINT PK
├── grade        VARCHAR(10)   -- HG / RG / MG / PG / ETC
├── product_name VARCHAR(255)
├── pdf_filename VARCHAR(255)
├── created_at   DATETIME
└── updated_at   DATETIME

decal (데칼 위치)
├── id           BIGINT PK
├── manual_id    BIGINT FK → manual.id
├── page_number  INT
├── decal_number VARCHAR(50)   -- 데칼 번호 (예: 1, A, ア)
├── x            DOUBLE        -- 페이지 내 X 좌표 비율 (0~1)
├── y            DOUBLE        -- 페이지 내 Y 좌표 비율 (0~1)
├── color        VARCHAR(10)   -- WHITE / BLACK
└── created_at   DATETIME
```
