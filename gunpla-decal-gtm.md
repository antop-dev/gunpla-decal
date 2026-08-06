# Google Tag Manager 설정 가이드

## 개요

사용자 페이지의 클릭 이벤트를 GTM으로 추적합니다.  
추적 방식: CSS 클래스 셀렉터(`gtm-*`) + `data-gtm-*` 속성으로 추가 데이터 전달.

---

## 변수 (Variables)

GTM > 변수 > 사용자 정의 변수에서 아래 **자동 이벤트 변수**를 생성합니다.

| 변수명 | 유형 | 속성 이름 |
|--------|------|-----------|
| `gtm_id` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-id` |
| `gtm_grade` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-grade` |
| `gtm_model` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-model` |
| `gtm_name` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-name` |
| `gtm_url` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-url` |
| `gtm_num` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-num` |
| `gtm_color` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-color` |
| `gtm_shape` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-shape` |
| `gtm_source` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-source` |
| `gtm_page` | 자동 이벤트 변수 > 요소 속성 | `data-gtm-page` |

---

## 트리거 (Triggers)

GTM > 트리거 > 새로 만들기 > **클릭 - 일부 링크** 또는 **클릭 - 모든 요소**

### 1. `trigger_sidebar_toggle` — 사이드바 토글
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-sidebar-toggle`

### 2. `trigger_manual_refresh` — 메뉴얼 목록 새로고침
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-manual-refresh`

### 3. `trigger_manual_select` — 메뉴얼 선택 (목록 & 아이콘)
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-manual-select`

### 4. `trigger_manual_ext_link` — 메뉴얼 외부 링크
- 유형: 클릭 - 일부 링크
- 조건: `Click Classes` **포함** `gtm-manual-ext-link`

### 5. `trigger_pdf_download` — PDF 다운로드
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-pdf-download`

### 6. `trigger_fit_width` — 가로 맞춤
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-fit-width`

### 7. `trigger_fit_height` — 세로 맞춤
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-fit-height`

### 8. `trigger_fullscreen` — 전체화면
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-fullscreen`

### 9. `trigger_marker_toggle` — 마커 보이기/숨기기
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-marker-toggle`

### 10. `trigger_shortcut` — 단축키 안내
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-shortcut`

### 11. `trigger_right_sidebar_toggle` — 오른쪽 사이드바 토글
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-right-sidebar-toggle`

### 12. `trigger_github_link` — GitHub 링크
- 유형: 클릭 - 일부 링크
- 조건: `Click Classes` **포함** `gtm-github-link`

### 13. `trigger_email_link` — 이메일 링크
- 유형: 클릭 - 일부 링크
- 조건: `Click Classes` **포함** `gtm-email-link`

### 14. `trigger_kofi_link` — Ko-fi 링크
- 유형: 클릭 - 일부 링크
- 조건: `Click Classes` **포함** `gtm-kofi-link`

### 15. `trigger_thumb_page` — 썸네일 페이지 클릭
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-thumb-page`

### 16. `trigger_decal_click` — 데칼 번호 클릭 (목록 & 아이콘)
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-decal-click`

### 17. `trigger_decal_marker` — PDF 위 데칼 마커 클릭
- 유형: 클릭 - 모든 요소
- 조건: `Click Classes` **포함** `gtm-decal-marker`

---

## 태그 (Tags)

각 태그는 GA4 이벤트 태그(Google Analytics: GA4 이벤트) 유형으로 생성합니다.  
측정 ID는 GA4 속성에서 확인한 값을 사용합니다.

### 1. `tag_sidebar_toggle`
- 이벤트 이름: `sidebar_toggle`
- 트리거: `trigger_sidebar_toggle`
- 매개변수: 없음

### 2. `tag_manual_refresh`
- 이벤트 이름: `manual_refresh`
- 트리거: `trigger_manual_refresh`
- 매개변수: 없음

### 3. `tag_manual_select`
- 이벤트 이름: `manual_select`
- 트리거: `trigger_manual_select`
- 이벤트 매개변수:

| 매개변수 키 | 값 |
|------------|-----|
| `manual_id` | `{{gtm_id}}` |
| `manual_grade` | `{{gtm_grade}}` |
| `manual_model` | `{{gtm_model}}` |
| `manual_name` | `{{gtm_name}}` |
| `source` | `{{gtm_source}}` |

> `source`: `"list"` = 텍스트 목록, `"icon"` = 접힌 사이드바 아이콘

### 4. `tag_manual_ext_link`
- 이벤트 이름: `manual_ext_link`
- 트리거: `trigger_manual_ext_link`
- 이벤트 매개변수:

| 매개변수 키 | 값 |
|------------|-----|
| `manual_id` | `{{gtm_id}}` |
| `link_url` | `{{gtm_url}}` |

### 5. `tag_pdf_download`
- 이벤트 이름: `pdf_download`
- 트리거: `trigger_pdf_download`
- 이벤트 매개변수:

| 매개변수 키 | 값 |
|------------|-----|
| `manual_id` | `{{gtm_id}}` |
| `manual_grade` | `{{gtm_grade}}` |
| `manual_model` | `{{gtm_model}}` |

### 6. `tag_fit_width`
- 이벤트 이름: `fit_width`
- 트리거: `trigger_fit_width`
- 매개변수: 없음

### 7. `tag_fit_height`
- 이벤트 이름: `fit_height`
- 트리거: `trigger_fit_height`
- 매개변수: 없음

### 8. `tag_fullscreen`
- 이벤트 이름: `fullscreen`
- 트리거: `trigger_fullscreen`
- 매개변수: 없음

### 9. `tag_marker_toggle`
- 이벤트 이름: `marker_toggle`
- 트리거: `trigger_marker_toggle`
- 매개변수: 없음

### 10. `tag_shortcut`
- 이벤트 이름: `shortcut_open`
- 트리거: `trigger_shortcut`
- 매개변수: 없음

### 11. `tag_right_sidebar_toggle`
- 이벤트 이름: `right_sidebar_toggle`
- 트리거: `trigger_right_sidebar_toggle`
- 매개변수: 없음

### 12. `tag_github_link`
- 이벤트 이름: `github_link`
- 트리거: `trigger_github_link`
- 매개변수: 없음

### 13. `tag_email_link`
- 이벤트 이름: `email_link`
- 트리거: `trigger_email_link`
- 매개변수: 없음

### 14. `tag_kofi_link`
- 이벤트 이름: `kofi_link`
- 트리거: `trigger_kofi_link`
- 매개변수: 없음

### 15. `tag_thumb_page`
- 이벤트 이름: `thumb_page_click`
- 트리거: `trigger_thumb_page`
- 이벤트 매개변수:

| 매개변수 키 | 값 |
|------------|-----|
| `page_number` | `{{gtm_page}}` |

### 16. `tag_decal_click`
- 이벤트 이름: `decal_click`
- 트리거: `trigger_decal_click`
- 이벤트 매개변수:

| 매개변수 키 | 값 |
|------------|-----|
| `decal_num` | `{{gtm_num}}` |
| `decal_color` | `{{gtm_color}}` |
| `decal_shape` | `{{gtm_shape}}` |
| `source` | `{{gtm_source}}` |

> `source`: `"grid"` = 5열 그리드, `"icon"` = 접힌 사이드바 아이콘

### 17. `tag_decal_marker`
- 이벤트 이름: `decal_marker_click`
- 트리거: `trigger_decal_marker`
- 이벤트 매개변수:

| 매개변수 키 | 값 |
|------------|-----|
| `decal_num` | `{{gtm_num}}` |
| `decal_page` | `{{gtm_page}}` |
| `decal_color` | `{{gtm_color}}` |
| `decal_shape` | `{{gtm_shape}}` |

---

## 추적 포인트 요약

| CSS 클래스                    | 위치                                  | 이벤트 이름                 | 추가 데이터                         |
|----------------------------|-------------------------------------|------------------------|--------------------------------|
| `gtm-sidebar-toggle`       | `#sb-toggle`                        | `sidebar_toggle`       | —                              |
| `gtm-manual-refresh`       | `#sb-refresh`                       | `manual_refresh`       | —                              |
| `gtm-manual-select`        | `.manual-item`, `.manual-icon-item` | `manual_select`        | id, grade, model, name, source |
| `gtm-manual-ext-link`      | `.manual-link-btn`                  | `manual_ext_link`      | id, url                        |
| `gtm-pdf-download`         | `.pdf-dl-btn`                       | `pdf_download`         | id, grade, model               |
| `gtm-fit-width`            | `#fit-width-btn`                    | `fit_width`            | —                              |
| `gtm-fit-height`           | `#fit-height-btn`                   | `fit_height`           | —                              |
| `gtm-fullscreen`           | `#fullscreen-btn`                   | `fullscreen`           | —                              |
| `gtm-marker-toggle`        | `label#marker-toggle`               | `marker_toggle`        | —                              |
| `gtm-shortcut`             | `#shortcut-btn`                     | `shortcut_open`        | —                              |
| `gtm-right-sidebar-toggle` | `#rs-toggle`                        | `right_sidebar_toggle` | —                              |
| `gtm-github-link`          | `.sb-github` (펼침/접힘)                | `github_link`          | —                              |
| `gtm-email-link`           | `.sb-email` (펼침/접힘)                 | `email_link`           | —                              |
| `gtm-kofi-link`            | `.sb-kofi` (펼침/접힘)                  | `kofi_link`            | —                              |
| `gtm-thumb-page`           | `.thumb-item`                       | `thumb_page_click`     | page                           |
| `gtm-decal-click`          | `.decal-btn`, `.decal-icon-btn`     | `decal_click`          | num, color, shape, source      |
| `gtm-decal-marker`         | `.decal-marker`                     | `decal_marker_click`   | num, page, color, shape        |
