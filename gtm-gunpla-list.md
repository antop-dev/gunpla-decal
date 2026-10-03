# GTM 설정 가이드 — 건담 목록

GTM 컨테이너에 등록해야 하는 **변수 → 트리거 → 태그** 순서로 기술합니다.  
모든 태그는 **GA4 이벤트** 유형 기준입니다.

---

## 1. 내장 변수 활성화

`변수` → `내장 변수` → `구성` 에서 아래 항목을 체크합니다.

| 변수명 | 용도 |
|---|---|
| Click Classes | gtm-* 클래스 매칭 트리거에 사용 |
| Click Element | data-gtm-* 속성 읽기 JS 변수에 사용 |
| Click Text | 버튼·링크 텍스트 수집 |
| Click URL | 외부 링크 URL 수집 |

---

## 2. 사용자 정의 변수

### 2-1. data-gtm-* 속성 읽기 — JavaScript 변수

AG Grid 셀은 중첩 DOM을 생성하므로 클릭된 요소에서 위로 올라가며 속성을 탐색하는 공통 헬퍼를 먼저 만듭니다.

> `변수` → `새로 만들기` → **맞춤 JavaScript**

---

#### `var_gtm_product_id`

```javascript
function() {
  var el = {{Click Element}};
  while (el) {
    if (el.dataset && el.dataset.gtmProductId !== undefined) {
      return el.dataset.gtmProductId;
    }
    el = el.parentElement;
  }
  return undefined;
}
```

---

#### `var_gtm_product_name`

```javascript
function() {
  var el = {{Click Element}};
  while (el) {
    if (el.dataset && el.dataset.gtmProductName !== undefined) {
      return el.dataset.gtmProductName;
    }
    el = el.parentElement;
  }
  return undefined;
}
```

---

#### `var_gtm_grade`

```javascript
function() {
  var el = {{Click Element}};
  while (el) {
    if (el.dataset && el.dataset.gtmGrade !== undefined) {
      return el.dataset.gtmGrade || '전체';
    }
    el = el.parentElement;
  }
  return undefined;
}
```

---

#### `var_gtm_owned`

```javascript
function() {
  var el = {{Click Element}};
  while (el) {
    if (el.dataset && el.dataset.gtmOwned !== undefined) {
      return el.dataset.gtmOwned;
    }
    el = el.parentElement;
  }
  return undefined;
}
```

---

#### `var_gtm_assembled`

```javascript
function() {
  var el = {{Click Element}};
  while (el) {
    if (el.dataset && el.dataset.gtmAssembled !== undefined) {
      return el.dataset.gtmAssembled;
    }
    el = el.parentElement;
  }
  return undefined;
}
```

---

#### `var_gtm_decal_attached`

```javascript
function() {
  var el = {{Click Element}};
  while (el) {
    if (el.dataset && el.dataset.gtmDecalAttached !== undefined) {
      return el.dataset.gtmDecalAttached;
    }
    el = el.parentElement;
  }
  return undefined;
}
```

---

## 3. 트리거

모든 트리거는 `트리거` → `새로 만들기` → **클릭 - 모든 요소** 유형입니다.  
조건은 특별한 표기가 없으면 **`Click Classes` 포함 `{클래스명}`** 입니다.

### 3-1. 헤더

| 트리거명 | 조건 |
|---|---|
| `trigger_header_view_normal` | Click Classes 포함 `gtm-header-view-normal` |
| `trigger_header_view_tab` | Click Classes 포함 `gtm-header-view-tab` |
| `trigger_header_github` | Click Classes 포함 `gtm-header-github` |
| `trigger_header_email` | Click Classes 포함 `gtm-header-email` |
| `trigger_header_donate` | Click Classes 포함 `gtm-header-donate` |
| `trigger_header_login` | Click Classes 포함 `gtm-header-login` |
| `trigger_header_logout` | Click Classes 포함 `gtm-header-logout` |

### 3-2. 툴바

| 트리거명 | 조건 |
|---|---|
| `trigger_toolbar_search` | Click Classes 포함 `gtm-toolbar-search` |
| `trigger_toolbar_clear` | Click Classes 포함 `gtm-toolbar-clear` |

### 3-3. 등급 탭

| 트리거명 | 조건 |
|---|---|
| `trigger_grade_tab` | Click Classes 포함 `gtm-grade-tab` |

### 3-4. 그리드 셀

| 트리거명 | 조건 |
|---|---|
| `trigger_grid_boxart` | Click Classes 포함 `gtm-grid-boxart` |
| `trigger_grid_name` | Click Classes 포함 `gtm-grid-name` |
| `trigger_grid_owned` | Click Classes 포함 `gtm-grid-owned` |
| `trigger_grid_assembled` | Click Classes 포함 `gtm-grid-assembled` |
| `trigger_grid_decal_attached` | Click Classes 포함 `gtm-grid-decal-attached` |
| `trigger_grid_manual` | Click Classes 포함 `gtm-grid-manual` |
| `trigger_grid_source` | Click Classes 포함 `gtm-grid-source` |
| `trigger_grid_purchase_date` | Click Classes 포함 `gtm-grid-purchase-date` |
| `trigger_grid_purchase_place` | Click Classes 포함 `gtm-grid-purchase-place` |
| `trigger_grid_purchase_price` | Click Classes 포함 `gtm-grid-purchase-price` |
| `trigger_grid_decal` | Click Classes 포함 `gtm-grid-decal` |

### 3-5. 상세 팝업

| 트리거명 | 조건 |
|---|---|
| `trigger_modal_detail_close` | Click Classes 포함 `gtm-modal-detail-close` |
| `trigger_modal_detail_backdrop` | Click Classes 포함 `gtm-modal-detail-backdrop` |
| `trigger_modal_detail_manual` | Click Classes 포함 `gtm-modal-detail-manual` |

### 3-6. 라이트박스

| 트리거명 | 조건 |
|---|---|
| `trigger_lightbox_close` | Click Classes 포함 `gtm-lightbox-close` |

---

## 4. 태그

모든 태그는 `태그` → `새로 만들기` → **Google 애널리틱스: GA4 이벤트** 유형입니다.  
측정 ID는 GA4 스트림의 `G-XXXXXXXXXX` 값을 입력합니다.

---

### 4-1. 헤더 — 뷰 전환

#### `tag_header_view_normal`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `view_mode_change` |
| 트리거 | `trigger_header_view_normal` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `mode` | `normal` |

---

#### `tag_header_view_tab`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `view_mode_change` |
| 트리거 | `trigger_header_view_tab` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `mode` | `tab` |

---

### 4-2. 헤더 — 외부 링크

#### `tag_header_github`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `click_external_link` |
| 트리거 | `trigger_header_github` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `link_type` | `github` |
| `link_url` | `{{Click URL}}` |

---

#### `tag_header_email`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `click_external_link` |
| 트리거 | `trigger_header_email` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `link_type` | `email` |
| `link_url` | `{{Click URL}}` |

---

#### `tag_header_donate`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `click_external_link` |
| 트리거 | `trigger_header_donate` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `link_type` | `donate` |
| `link_url` | `{{Click URL}}` |

---

### 4-3. 헤더 — 인증

#### `tag_header_login`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `login_click` |
| 트리거 | `trigger_header_login` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `method` | `google` |

---

#### `tag_header_logout`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `logout_click` |
| 트리거 | `trigger_header_logout` |

---

### 4-4. 툴바 — 검색

#### `tag_toolbar_search`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `search` |
| 트리거 | `trigger_toolbar_search` |

> 검색 조건 값(등급·구분·키워드 등)이 필요하면 GTM 미리보기 모드로 DOM 값을 확인한 뒤 별도 JS 변수를 추가합니다.

---

#### `tag_toolbar_clear`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `search_reset` |
| 트리거 | `trigger_toolbar_clear` |

---

### 4-5. 등급 탭

#### `tag_grade_tab`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `grade_tab_click` |
| 트리거 | `trigger_grade_tab` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `grade` | `{{var_gtm_grade}}` |

---

### 4-6. 그리드 — 제품 조회

#### `tag_grid_boxart`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_boxart_click` |
| 트리거 | `trigger_grid_boxart` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `product_id` | `{{var_gtm_product_id}}` |
| `product_name` | `{{var_gtm_product_name}}` |

---

#### `tag_grid_name`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_detail_open` |
| 트리거 | `trigger_grid_name` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `product_id` | `{{var_gtm_product_id}}` |
| `product_name` | `{{var_gtm_product_name}}` |

---

### 4-7. 그리드 — 보유 현황 토글

#### `tag_grid_owned`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_owned_toggle` |
| 트리거 | `trigger_grid_owned` |

**이벤트 매개변수**

| 매개변수명 | 값 | 비고 |
|---|---|---|
| `product_id` | `{{var_gtm_product_id}}` | |
| `previous_value` | `{{var_gtm_owned}}` | 클릭 전 값 (`true`/`false`) |

---

#### `tag_grid_assembled`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_assembled_toggle` |
| 트리거 | `trigger_grid_assembled` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `product_id` | `{{var_gtm_product_id}}` |
| `previous_value` | `{{var_gtm_assembled}}` |

---

#### `tag_grid_decal_attached`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_decal_toggle` |
| 트리거 | `trigger_grid_decal_attached` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `product_id` | `{{var_gtm_product_id}}` |
| `previous_value` | `{{var_gtm_decal_attached}}` |

---

### 4-8. 그리드 — 외부 링크

#### `tag_grid_manual`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_manual_click` |
| 트리거 | `trigger_grid_manual` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `product_id` | `{{var_gtm_product_id}}` |
| `link_url` | `{{Click URL}}` |

---

#### `tag_grid_source`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_source_click` |
| 트리거 | `trigger_grid_source` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `product_id` | `{{var_gtm_product_id}}` |
| `link_url` | `{{Click URL}}` |

---

### 4-9. 그리드 — 구매 정보 편집

편집 시작(셀 클릭)을 추적합니다. 저장 성공 여부는 별도 `dataLayer.push()`가 필요합니다.

#### `tag_grid_purchase_date`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_purchase_date_edit` |
| 트리거 | `trigger_grid_purchase_date` |

---

#### `tag_grid_purchase_place`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_purchase_place_edit` |
| 트리거 | `trigger_grid_purchase_place` |

---

#### `tag_grid_purchase_price`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_purchase_price_edit` |
| 트리거 | `trigger_grid_purchase_price` |

---

#### `tag_grid_decal`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_decal_brand_edit` |
| 트리거 | `trigger_grid_decal` |

---

### 4-10. 상세 팝업

#### `tag_modal_detail_close`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_detail_close` |
| 트리거 | `trigger_modal_detail_close` |

---

#### `tag_modal_detail_backdrop`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_detail_close` |
| 트리거 | `trigger_modal_detail_backdrop` |

> 닫기 버튼과 배경 클릭을 별도 트리거로 두었으므로 이벤트 이름은 같게 유지하되, 구분이 필요하면 `close_method` 매개변수(`button` / `backdrop`)를 추가합니다.

---

#### `tag_modal_detail_manual`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `product_manual_click` |
| 트리거 | `trigger_modal_detail_manual` |

**이벤트 매개변수**

| 매개변수명 | 값 |
|---|---|
| `product_id` | `{{var_gtm_product_id}}` |
| `link_url` | `{{Click URL}}` |
| `source` | `detail_modal` |

---

### 4-11. 라이트박스

#### `tag_lightbox_close`

| 항목 | 값 |
|---|---|
| 이벤트 이름 | `lightbox_close` |
| 트리거 | `trigger_lightbox_close` |

---

## 5. GA4 맞춤 측정기준 등록

GA4 `관리` → `맞춤 정의` → `맞춤 측정기준` 에 이벤트 매개변수를 등록해야 보고서에서 사용할 수 있습니다.

| 측정기준 이름 | 범위 | 이벤트 매개변수 |
|---|---|---|
| 뷰 모드 | 이벤트 | `mode` |
| 링크 유형 | 이벤트 | `link_type` |
| 등급 | 이벤트 | `grade` |
| 제품 ID | 이벤트 | `product_id` |
| 제품명 | 이벤트 | `product_name` |
| 이전 보유 값 | 이벤트 | `previous_value` |
| 클릭 출처 | 이벤트 | `source` |

---

## 6. 검증 절차

1. GTM 미리보기 모드 활성화
2. 사이트에서 각 요소 클릭
3. GTM 미리보기 패널에서 트리거 발동 및 변수 값 확인
4. GA4 `DebugView` (`관리` → `DebugView`)에서 이벤트 수신 확인
5. 이상 없으면 컨테이너 **게시**
