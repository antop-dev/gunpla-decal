/* ── fetch 인터셉터: 403 응답 시 로그인 페이지로 이동 ── */
const _fetch = window.fetch.bind(window);
window.fetch = async (...args) => {
  const res = await _fetch(...args);
  if (res.status === 403) {
    window.location.replace(window.contextPath + '/login');
    return new Promise(() => {});
  }
  return res;
};

/* ── 상태 (관리자 페이지 전용) ── */
let currentManual   = null;  // 현재 선택된 메뉴얼 객체 { id, grade, modelNumber, productName, ... }
let allDecals       = [];    // 현재 메뉴얼의 전체 데칼 목록
let manualList      = [];    // 로드된 메뉴얼 목록 캐시 (삭제 confirm에 사용)
let editingManualId = null;  // 수정 중인 메뉴얼 ID
let lastManualGrade = 'RG'; // 마지막으로 선택한 메뉴얼 등급
let manualLoading   = false; // PDF 로드 중 중복 선택 방지 플래그

// 드래그 상태 추적 (pdfScroll 패닝 + 단순 클릭 구분)
let mouseDown   = false;
let wasDragging = false;
let lastMX = 0, lastMY = 0;
let clickStartX = 0, clickStartY = 0;

// 마커 툴팁 및 모달 상태
let markersVisible  = true;  // 마커 보이기/숨기기 상태
let tooltipDecalId  = null; // 현재 툴팁이 표시된 데칼 ID
let pendingPos      = null; // 데칼 등록 모달에서 사용할 클릭 위치 {x, y, page}
let editingDecalId  = null; // 수정 모달에서 편집 중인 데칼 ID

// 데칼 인식용 크롭 반경(pt)·출력 해상도(px) — 서버가 예전에 scale=3.0 렌더 기준으로 쓰던
// crop 반경(20~25px)을 pt로 환산한 값(20/3≈7, 25/3≈9)이라 인식 품질이 기존과 비슷하다.
const CROP_RADIUS_ONNX_PT   = 7;
const CROP_OUTPUT_ONNX_PX   = 224; // ONNX 모델 입력 크기와 동일
const CROP_RADIUS_NUMBER_PT = 7;
const CROP_OUTPUT_NUMBER_PX = 160;
const CROP_RADIUS_COLOR_PT  = 9;
const CROP_OUTPUT_COLOR_PX  = 160;
let lastDecalStyle  = { color: '#ffffff', shape: 'CIRCLE', num: '' }; // 마지막으로 사용한 데칼 스타일

// 일본어 문자 선택기 상태
const JP_CHARS = 'あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん' +
                 'アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン';
let jpPickerTarget = null; // 현재 일본어 선택기가 값을 채울 input 요소

/* ── 관리자 전용 DOM 요소 ── */
const chH     = document.getElementById('ch-h');
const chV     = document.getElementById('ch-v');
const tooltip = document.getElementById('marker-tooltip');

/* ──────────── 드래그 패닝 & 데칼 등록 클릭 ──────────── */

// mousedown: 마커·툴팁·모달·줌 오버레이 영역 외에서만 드래그/클릭 시작
container.addEventListener('mousedown', e => {
  if (e.button !== 0) return;
  if (e.target.closest('.decal-marker') || e.target.closest('#marker-tooltip')) return;
  if (e.target.closest('#zoom-overlay')) return;
  if (!document.getElementById('decal-modal').classList.contains('hidden')) return;
  if (!document.getElementById('edit-modal').classList.contains('hidden')) return;
  mouseDown = true;
  wasDragging = false;
  clickStartX = lastMX = e.clientX;
  clickStartY = lastMY = e.clientY;
  hideTooltip();
});

// mousemove: 5px 이상 이동 시 드래그로 판정, pdfScroll 패닝
window.addEventListener('mousemove', e => {
  if (!mouseDown) return;
  const dx = e.clientX - lastMX, dy = e.clientY - lastMY;
  if (!wasDragging && Math.hypot(e.clientX - clickStartX, e.clientY - clickStartY) > 5) {
    wasDragging = true;
    container.classList.add('dragging');
  }
  if (wasDragging) {
    pdfScroll.scrollLeft -= dx;
    pdfScroll.scrollTop  -= dy;
  }
  lastMX = e.clientX; lastMY = e.clientY;
});

// mouseup: 드래그 아닌 단순 클릭이면 해당 위치에 데칼 등록 모달 열기
window.addEventListener('mouseup', e => {
  if (!mouseDown || e.button !== 0) return;
  mouseDown = false;
  container.classList.remove('dragging');
  if (!wasDragging && pdfDoc
      && !e.target.closest('.decal-marker')
      && !e.target.closest('#marker-tooltip')
      && !e.target.closest('#zoom-overlay')) {
    // 클릭 좌표를 PDF 캔버스 기준 백분율(%)로 변환
    const rect = pdfScroll.getBoundingClientRect();
    const contentX = e.clientX - rect.left + pdfScroll.scrollLeft;
    const contentY = e.clientY - rect.top  + pdfScroll.scrollTop;
    const x = parseFloat((contentX / scale / basePdfWidth  * 100).toFixed(2));
    const y = parseFloat((contentY / scale / basePdfHeight * 100).toFixed(2));
    if (x >= 0 && x <= 100 && y >= 0 && y <= 100) openDecalModal(x, y, e.clientX, e.clientY);
  }
  wasDragging = false;
});

// pdf 스크롤 시 툴팁 위치 재계산
pdfScroll.addEventListener('scroll', repositionTooltip);

/* ──────────── 십자선 가이드 ──────────── */

// 마우스가 PDF 영역 위에 있을 때 십자선 표시
container.addEventListener('mousemove', e => {
  if (!pdfDoc) return;
  const rect = container.getBoundingClientRect();
  chH.style.top  = (e.clientY - rect.top)  + 'px';
  chV.style.left = (e.clientX - rect.left) + 'px';
  chH.style.display = chV.style.display = 'block';
});
container.addEventListener('mouseleave', () => {
  chH.style.display = chV.style.display = 'none';
});

/* ──────────── 메뉴얼 목록 그리드 ──────────── */

// ISO LocalDateTime 문자열("2026-08-14T10:48:00")을 "2026-08-14 10:48"로 변환
function fmtDateTime(v) {
  if (!v) return '';
  return v.slice(0, 16).replace('T', ' ');
}

/* ── Day.js 초기화 ── */
// 서버가 내려주는 LocalDateTime에는 오프셋이 없어 브라우저 타임존으로 오해석되므로 KST로 고정 해석한다
const SERVER_TZ = 'Asia/Seoul';

dayjs.extend(dayjs_plugin_utc);
dayjs.extend(dayjs_plugin_timezone);
dayjs.extend(dayjs_plugin_relativeTime);
dayjs.locale('ko');

// 서버 시각을 "1일 전" 형태의 상대시간으로 변환
function fmtRelative(v) {
  if (!v) return '';
  return dayjs.tz(v, SERVER_TZ).fromNow();
}

// 메뉴얼 공개 URL을 클립보드에 복사하고 버튼에 잠시 체크 아이콘을 표시
async function copyManualLink(id, btn) {
  await navigator.clipboard.writeText(`${location.origin}${window.contextPath}/${id}`);
  if (!btn) return;
  btn.innerHTML = '<i class="fas fa-check"></i> 복사';
  setTimeout(() => { btn.innerHTML = '<i class="fas fa-link"></i> 복사'; }, 1500);
}

const gridColumnDefs = [
  {
    headerName: '등급', field: 'grade', width: 80,
    headerClass: 'header-center', cellClass: 'cell-center',
    cellRenderer: p => `<span class="grade-badge grade-${esc(p.value)}">${esc(p.value)}</span>`,
  },
  {
    headerName: '편집', width: 80, sortable: false,
    headerClass: 'header-center', cellClass: 'cell-center cell-action',
    cellRenderer: () => '<button class="grid-btn" title="데칼 편집"><i class="fas fa-pen-to-square"></i> 편집</button>',
    onCellClicked: p => openEditor(p.data.id),
  },
  {
    headerName: '게시', field: 'published', width: 80,
    headerClass: 'header-center', cellClass: 'cell-center cell-action',
    cellRenderer: p => p.value
      ? '<span class="pub-badge pub-on"><i class="fas fa-eye"></i> 게시</span>'
      : '<span class="pub-badge pub-off"><i class="fas fa-eye-slash"></i> 미게시</span>',
    onCellClicked: p => togglePublishedRow(p.data.id),
  },
  { headerName: '형식번호', field: 'modelNumber', width: 160 },
  { headerName: '제품명', field: 'productName', flex: 1, minWidth: 220, cellClass: 'cell-ellipsis', tooltipField: 'productName' },
  {
    headerName: '링크', width: 80, sortable: false,
    headerClass: 'header-center', cellClass: 'cell-center cell-action',
    cellRenderer: () => '<button class="grid-btn grid-btn-plain" title="메뉴얼 링크 복사"><i class="fas fa-link"></i> 복사</button>',
    onCellClicked: p => copyManualLink(p.data.id, p.event.target.closest('button')),
  },
  {
    headerName: '다운로드', width: 110, sortable: false,
    headerClass: 'header-center', cellClass: 'cell-center',
    // 파일명은 서버가 Content-Disposition으로 지정한다
    cellRenderer: p =>
      `<a class="grid-btn grid-btn-plain" href="${window.contextPath}/api/admin/manuals/${p.data.id}/download"` +
      ` title="PDF 다운로드"><i class="fas fa-download"></i> 다운로드</a>`,
  },
  {
    headerName: '참조', field: 'link', width: 70, sortable: false,
    headerClass: 'header-center', cellClass: 'cell-center',
    cellRenderer: p => p.value
      ? `<a href="${esc(p.value)}" target="_blank" rel="noopener" class="grid-link" title="${esc(p.value)}"><i class="fas fa-arrow-up-right-from-square"></i></a>`
      : '',
  },
  {
    headerName: '등록일시', field: 'createdAt', width: 110,
    valueFormatter: p => fmtRelative(p.value),
    tooltipValueGetter: p => fmtDateTime(p.value),
  },
  {
    headerName: '수정일시', field: 'updatedAt', width: 110,
    valueFormatter: p => fmtRelative(p.value),
    tooltipValueGetter: p => fmtDateTime(p.value),
  },
  {
    headerName: '관리', width: 150, sortable: false,
    headerClass: 'header-center', cellClass: 'cell-center',
    cellRenderer: () =>
      '<button class="grid-btn grid-btn-plain" data-act="edit" title="메뉴얼 정보 수정"><i class="fas fa-pen"></i> 수정</button>' +
      '<button class="grid-btn grid-btn-danger" data-act="del" title="삭제"><i class="fas fa-trash"></i> 삭제</button>',
    onCellClicked: p => {
      const act = p.event.target.closest('[data-act]')?.dataset.act;
      if (act === 'edit') openManualEditModal(p.data.id);
      if (act === 'del')  deleteManual(p.data.id);
    },
  },
];

const gridApi = agGrid.createGrid(document.getElementById('manual-grid'), {
  theme: agGrid.themeQuartz.withPart(agGrid.colorSchemeDarkBlue),
  columnDefs: gridColumnDefs,
  rowData: [],
  getRowId: p => p.data.id,
  rowHeight: 34,
  headerHeight: 36,
  defaultColDef: { resizable: true, sortable: true },
  // 셀 텍스트를 드래그로 선택·복사할 수 있게 한다 (ensureDomOrder는 선택 순서가 화면 순서와 일치하도록 보장)
  enableCellTextSelection: true,
  ensureDomOrder: true,
  // 기본 지연이 2초라 일시 툴팁이 안 뜨는 것처럼 보인다
  tooltipShowDelay: 300,
  loading: true,
  overlayNoRowsTemplate: '<span style="color:#9ca3af;font-size:12px;">표시할 메뉴얼이 없습니다</span>',
  overlayLoadingTemplate: '<span style="color:#9ca3af;font-size:12px;">불러오는 중…</span>',
});

// 그리드에 목록을 반영
function renderGrid(list) {
  manualList = list;
  gridApi.setGridOption('rowData', list);
  gridApi.setGridOption('loading', false);
}

// 검색 영역의 입력 요소 (전부 빈 값이면 조건 없음)
const searchInputIds = ['f-grade', 'f-published', 'f-model', 'f-name'];

// 검색 조건이 하나라도 지정되어 있는지 여부
function hasSearchFilter() {
  return searchInputIds.some(id => document.getElementById(id).value.trim());
}

// 검색 조건을 쿼리스트링으로 만들어 항상 서버에서 조회
async function loadManuals() {
  const params = new URLSearchParams();
  const grade       = document.getElementById('f-grade').value;
  const published   = document.getElementById('f-published').value;
  const modelNumber = document.getElementById('f-model').value.trim();
  const productName = document.getElementById('f-name').value.trim();
  if (grade)       params.set('grade', grade);
  if (published)   params.set('published', published);
  if (modelNumber) params.set('modelNumber', modelNumber);
  if (productName) params.set('productName', productName);
  const qs = params.toString();
  renderGrid(await (await fetch(`/api/admin/manuals${qs ? '?' + qs : ''}`)).json());
}

// 검색 조건을 모두 비우고 전체 목록을 다시 조회
function resetSearch() {
  searchInputIds.forEach(id => { document.getElementById(id).value = ''; });
  loadManuals();
}

// 그리드의 특정 행 데이터를 부분 갱신
function updateGridRow(id, patch) {
  const row = gridApi.getRowNode(id);
  if (!row) return;
  Object.assign(row.data, patch);
  gridApi.applyTransaction({ update: [row.data] });
}

// SSE로 수신된 메뉴얼을 그리드 맨 위에 삽입 (검색 조건이 있으면 목록을 다시 조회)
function prependManualToList(manual) {
  if (hasSearchFilter()) { loadManuals(); return; }
  manualList.unshift(manual);
  gridApi.applyTransaction({ add: [manual], addIndex: 0 });
}

// 등록된 메뉴얼 행을 duration(ms) 동안 노란색으로 하이라이트
function highlightManual(id, duration) {
  gridApi.flashCells({ rowNodes: [gridApi.getRowNode(id)].filter(Boolean), flashDuration: duration });
}

// 화면 우하단에 빨간 토스트 메시지를 4.5초 표시
function showToast(message) {
  const toast = document.createElement('div');
  toast.className = 'fixed bottom-6 right-6 bg-red-600 text-white text-sm px-4 py-2.5 rounded-lg shadow-lg z-[9999] flex items-center gap-2';
  toast.innerHTML = `<i class="fas fa-exclamation-circle"></i><span>${esc(message)}</span>`;
  document.body.appendChild(toast);
  setTimeout(() => {
    toast.style.transition = 'opacity 0.5s';
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 500);
  }, 4000);
}

/* ──────────── 편집 화면 (풀 팝업) ──────────── */

// 편집 팝업을 열고 해당 메뉴얼의 PDF·데칼을 로드
async function openEditor(id) {
  if (manualLoading) return;
  manualLoading = true;
  try {
    // 팝업을 먼저 표시해야 fitToContainer가 컨테이너 치수를 계산할 수 있다
    document.getElementById('editor-view').classList.remove('hidden');

    // 스켈레톤 표시 (pdfScroll은 visible — fitToContainer 치수 계산에 필요)
    noPdf.style.display = 'none';
    pdfScroll.style.display = '';
    document.getElementById('zoom-overlay').style.display = 'none';
    document.getElementById('pdf-loading').style.display = 'flex';
    thumbStrip.innerHTML = '<div class="strip-inner"><span class="text-gray-500 text-xs select-none">로딩 중…</span></div>';

    const data = await (await fetch(`/api/admin/manuals/${id}`)).json();
    currentManual = data; allDecals = data.decals;
    updatePdfTitle(data);
    lastDecalStyle = { color: '#ffffff', shape: 'CIRCLE', num: '' };

    pdfDoc = await pdfjsLib.getDocument(`${window.contextPath}/resource/${id}`).promise;
    currentPage = 1;
    await renderPage(currentPage, true);

    // 스켈레톤 숨기고 PDF 공개
    document.getElementById('pdf-loading').style.display = '';
    document.getElementById('zoom-overlay').style.display = 'flex';

    renderThumbnails(data.thumbnails);
  } finally {
    manualLoading = false;
  }
}

// 편집 팝업을 닫고 PDF 상태를 초기화
function closeEditor() {
  if (manualLoading) return;
  document.getElementById('editor-view').classList.add('hidden');
  currentManual = null; pdfDoc = null; currentPdfPage = null; allDecals = [];
  pdfScroll.style.display = 'none';
  noPdf.style.display = '';
  hideTooltip();
  updatePdfTitle(null);
  thumbStrip.innerHTML = '<div class="strip-inner"><span class="text-gray-500 text-xs select-none">메뉴얼을 선택하세요</span></div>';
}

document.getElementById('btn-editor-close').addEventListener('click', closeEditor);

/* ──────────── 데칼 오버레이 ──────────── */

// 현재 페이지의 데칼 마커를 오버레이에 렌더링 (common.js의 renderPage에서 호출)
function renderOverlay() {
  overlay.innerHTML = allDecals.filter(d => d.page === currentPage).map(d => buildDecalMarkerHtml(d, 4)).join('');

  overlay.querySelectorAll('.decal-marker').forEach(m =>
    m.addEventListener('click', e => {
      e.stopPropagation();
      const d = allDecals.find(x => x.id === +m.dataset.id);
      if (!d) return;
      editingDecalId = +m.dataset.id;
      document.getElementById('inp-edit-num').value   = d.decalNumber;
      const ec = d.color ?? '#ffffff';
      document.getElementById('inp-edit-hex').value   = ec.slice(1).toUpperCase();
      document.getElementById('inp-edit-color').value = ec;
      document.getElementById('inp-edit-color').dispatchEvent(new Event('input'));
      const shapeRadio = document.querySelector(`input[name="edit-shape"][value="${d.shape ?? 'CIRCLE'}"]`);
      if (shapeRadio) shapeRadio.checked = true;
      openEditModal(e.clientX, e.clientY);
    }));

  overlay.style.display = markersVisible ? '' : 'none';
}

/* ──────────── 마커 툴팁 ──────────── */

function showTooltip(decalId) {
  tooltipDecalId = decalId;
  tooltip.style.display = 'flex';
  repositionTooltip();
}

function repositionTooltip() {
  if (tooltip.style.display === 'none' || !tooltipDecalId) return;
  const d = allDecals.find(x => x.id === tooltipDecalId);
  if (!d) { hideTooltip(); return; }
  const tx = basePdfWidth  * (d.x / 100) * scale - pdfScroll.scrollLeft;
  const ty = basePdfHeight * (d.y / 100) * scale - pdfScroll.scrollTop;
  if (tx < 0 || ty < 0 || tx > container.clientWidth || ty > container.clientHeight) {
    hideTooltip();
    return;
  }
  const tipW = 80, tipH = 38;
  const left = Math.min(tx + 16, container.clientWidth  - tipW - 4);
  const top  = Math.min(ty + 16, container.clientHeight - tipH - 4);
  tooltip.style.left = left + 'px';
  tooltip.style.top  = top  + 'px';
}

function hideTooltip() {
  tooltip.style.display = 'none';
  tooltipDecalId = null;
}

document.addEventListener('keydown', e => { if (e.key === 'Escape') hideTooltip(); });

tooltip.addEventListener('mousedown', e => e.stopPropagation());
tooltip.addEventListener('mouseup',   e => e.stopPropagation());

// 삭제 버튼: 수정 폼에서 데칼 삭제 후 폼 닫기
document.getElementById('btn-edit-delete').addEventListener('click', async e => {
  e.stopPropagation();
  if (!editingDecalId) return;
  const id = editingDecalId;
  cancelEditModal();
  await fetch(`/api/admin/decals/${id}`, { method: 'DELETE' });
  allDecals = allDecals.filter(d => d.id !== id);
  await autoUnpublish();
  renderOverlay();
});

/* ──────────── 색상 프리셋 버튼 ──────────── */

function focusActiveDecalNum() {
  if (!document.getElementById('decal-modal').classList.contains('hidden')) {
    document.getElementById('inp-decal-num').focus();
  } else if (!document.getElementById('edit-modal').classList.contains('hidden')) {
    document.getElementById('inp-edit-num').focus();
  }
}

function applyWbToggle(hexInputId, colorInputId) {
  const hexEl = document.getElementById(hexInputId);
  const color = hexEl.value.toUpperCase() === 'FFFFFF' ? '#000000' : '#ffffff';
  hexEl.value = color.slice(1).toUpperCase();
  hexEl.dispatchEvent(new Event('input', { bubbles: true }));
  const colorEl = document.getElementById(colorInputId);
  colorEl.value = color;
  colorEl.dispatchEvent(new Event('input'));
  focusActiveDecalNum();
}

document.getElementById('btn-wb-decal').addEventListener('click', () => {
  applyWbToggle('inp-decal-hex', 'inp-decal-color');
});

document.getElementById('btn-wb-edit').addEventListener('click', () => {
  applyWbToggle('inp-edit-hex', 'inp-edit-color');
});

/* ──────────── 헥스 색상 입력 동기화 ──────────── */

function setupHexColorInput(hexId, colorId, wrapId, iconId) {
  const hexEl   = document.getElementById(hexId);
  const colorEl = document.getElementById(colorId);

  function syncPalette(hex) {
    const wrap = document.getElementById(wrapId);
    const icon = document.getElementById(iconId);
    if (!wrap || !icon) return;
    wrap.style.background = hex;
    icon.style.color = hexLuminance(hex) > 0.5 ? '#333' : '#fff';
  }

  hexEl.addEventListener('input', () => {
    hexEl.value = hexEl.value.replace(/[^0-9a-fA-F]/g, '').toUpperCase().slice(0, 6);
    if (hexEl.value.length === 6) {
      const hex = '#' + hexEl.value.toLowerCase();
      colorEl.value = hex;
      syncPalette(hex);
    }
  });
  hexEl.addEventListener('paste', e => {
    e.preventDefault();
    const text = (e.clipboardData || window.clipboardData).getData('text');
    hexEl.value = text.replace(/^#/, '').replace(/[^0-9a-fA-F]/g, '').toUpperCase().slice(0, 6);
    if (hexEl.value.length === 6) {
      const hex = '#' + hexEl.value.toLowerCase();
      colorEl.value = hex;
      syncPalette(hex);
    }
  });
  colorEl.addEventListener('input', () => {
    hexEl.value = colorEl.value.slice(1).toUpperCase();
    syncPalette(colorEl.value);
  });
}
setupHexColorInput('inp-decal-hex', 'inp-decal-color', 'wrap-decal-color', 'ico-decal-palette');
setupHexColorInput('inp-edit-hex',  'inp-edit-color',  'wrap-edit-color',  'ico-edit-palette');

/* ──────────── 커스텀 색상 선택기 ──────────── */

let cpHue = 0, cpSat = 1, cpVal = 1;
let cpTargetHexId = null, cpTargetColorId = null;
let cpDragTarget  = null; // 'sv' | 'hue'

const cpPopup  = document.getElementById('cp-popup');
const cpSvCvs  = document.getElementById('cp-sv');
const cpHueCvs = document.getElementById('cp-hue');
const cpSvCtx  = cpSvCvs.getContext('2d');
const cpHueCtx = cpHueCvs.getContext('2d');

function hsvToRgb(h, s, v) {
  const f = (n, k = (n + h / 60) % 6) => v - v * s * Math.max(Math.min(k, 4 - k, 1), 0);
  return [Math.round(f(5) * 255), Math.round(f(3) * 255), Math.round(f(1) * 255)];
}

function hexToHsv(hex) {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d) {
    if (max === r)      h = ((g - b) / d + 6) % 6 * 60;
    else if (max === g) h = ((b - r) / d + 2) * 60;
    else                h = ((r - g) / d + 4) * 60;
  }
  return [h, max ? d / max : 0, max];
}

function drawCpHue() {
  const w = cpHueCvs.width, h = cpHueCvs.height;
  const g = cpHueCtx.createLinearGradient(0, 0, w, 0);
  for (let i = 0; i <= 6; i++) g.addColorStop(i / 6, `hsl(${i * 60},100%,50%)`);
  cpHueCtx.fillStyle = g;
  cpHueCtx.fillRect(0, 0, w, h);
  const x = Math.max(6, Math.min(w - 6, Math.round(cpHue / 360 * w)));
  cpHueCtx.save();
  cpHueCtx.strokeStyle = '#fff';
  cpHueCtx.lineWidth = 2;
  cpHueCtx.beginPath(); cpHueCtx.arc(x, h / 2, 5, 0, Math.PI * 2); cpHueCtx.stroke();
  cpHueCtx.restore();
}

function drawCpSV() {
  const w = cpSvCvs.width, h = cpSvCvs.height;
  const gS = cpSvCtx.createLinearGradient(0, 0, w, 0);
  gS.addColorStop(0, '#fff'); gS.addColorStop(1, `hsl(${cpHue},100%,50%)`);
  cpSvCtx.fillStyle = gS; cpSvCtx.fillRect(0, 0, w, h);
  const gV = cpSvCtx.createLinearGradient(0, 0, 0, h);
  gV.addColorStop(0, 'rgba(0,0,0,0)'); gV.addColorStop(1, '#000');
  cpSvCtx.fillStyle = gV; cpSvCtx.fillRect(0, 0, w, h);
  const x = Math.max(5, Math.min(w - 5, Math.round(cpSat * w)));
  const y = Math.max(5, Math.min(h - 5, Math.round((1 - cpVal) * h)));
  cpSvCtx.save();
  cpSvCtx.strokeStyle = '#fff'; cpSvCtx.lineWidth = 2;
  cpSvCtx.beginPath(); cpSvCtx.arc(x, y, 5, 0, Math.PI * 2); cpSvCtx.stroke();
  cpSvCtx.strokeStyle = 'rgba(0,0,0,0.4)'; cpSvCtx.lineWidth = 1;
  cpSvCtx.beginPath(); cpSvCtx.arc(x, y, 5, 0, Math.PI * 2); cpSvCtx.stroke();
  cpSvCtx.restore();
}

function cpOutput() {
  const [r, g, b] = hsvToRgb(cpHue, cpSat, cpVal);
  const hex = '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('');
  if (cpTargetColorId) {
    document.getElementById(cpTargetColorId).value = hex;
    document.getElementById(cpTargetColorId).dispatchEvent(new Event('input'));
  }
}

cpSvCvs.addEventListener('mousedown', e => { e.preventDefault(); cpDragTarget = 'sv'; cpUpdateSV(e); });
cpHueCvs.addEventListener('mousedown', e => { e.preventDefault(); cpDragTarget = 'hue'; cpUpdateHue(e); });

function cpUpdateSV(e) {
  const rect = cpSvCvs.getBoundingClientRect();
  cpSat = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
  cpVal = Math.max(0, Math.min(1, 1 - (e.clientY - rect.top) / rect.height));
  drawCpSV(); cpOutput();
}
function cpUpdateHue(e) {
  const rect = cpHueCvs.getBoundingClientRect();
  cpHue = Math.max(0, Math.min(360, (e.clientX - rect.left) / rect.width * 360));
  drawCpHue(); drawCpSV(); cpOutput();
}

window.addEventListener('mousemove', e => {
  if (cpDragTarget === 'sv') cpUpdateSV(e);
  else if (cpDragTarget === 'hue') cpUpdateHue(e);
});
window.addEventListener('mouseup', () => { cpDragTarget = null; });

function openColorPicker(hexInputId, colorInputId, anchorEl) {
  cpTargetHexId   = hexInputId;
  cpTargetColorId = colorInputId;
  const raw = document.getElementById(hexInputId).value;
  [cpHue, cpSat, cpVal] = hexToHsv(raw.length === 6 ? '#' + raw : '#ffffff');
  cpPopup.classList.remove('hidden');
  drawCpHue(); drawCpSV();
  const rect = anchorEl.getBoundingClientRect();
  const W = cpPopup.offsetWidth || 190, H = cpPopup.offsetHeight || 180;
  const vw = window.innerWidth, vh = window.innerHeight;
  let left = rect.right + 2, top = rect.top;
  if (left + W > vw) left = rect.left - W - 2;
  if (left < 4) left = 4;
  if (top + H > vh) top = vh - H - 4;
  if (top < 4) top = 4;
  cpPopup.style.left = left + 'px';
  cpPopup.style.top  = top  + 'px';
}

function closeColorPicker() {
  cpPopup.classList.add('hidden');
  cpTargetHexId = cpTargetColorId = null;
  focusActiveDecalNum();
}

document.getElementById('wrap-decal-color').addEventListener('click', e => {
  e.stopPropagation();
  if (!cpPopup.classList.contains('hidden') && cpTargetHexId === 'inp-decal-hex') closeColorPicker();
  else openColorPicker('inp-decal-hex', 'inp-decal-color', e.currentTarget);
});
document.getElementById('wrap-edit-color').addEventListener('click', e => {
  e.stopPropagation();
  if (!cpPopup.classList.contains('hidden') && cpTargetHexId === 'inp-edit-hex') closeColorPicker();
  else openColorPicker('inp-edit-hex', 'inp-edit-color', e.currentTarget);
});

/* ──────────── 데칼 번호 입력 유효성 ──────────── */

function sanitizeDecalNum(val) {
  if (!val) return '';
  const first = val[0];
  if (/^\d/.test(first)) return val.replace(/\D/g, '').slice(0, 3);
  if (/^[A-Za-z]/.test(first)) return val.replace(/[^A-Za-z]/g, '').slice(0, 1);
  if (/^[぀-ゟ゠-ヿ]/.test(first))
    return val.replace(/[^぀-ゟ゠-ヿ]/g, '').slice(0, 1);
  return val.slice(0, 1);
}

function isValidDecalNum(val) {
  return /^\d{1,3}$/.test(val) || /^[A-Za-z]$/.test(val) || /^[぀-ゟ゠-ヿ]$/.test(val);
}

function applyDecalNumValidation(inputEl) {
  let composing = false;
  inputEl.addEventListener('compositionstart', () => { composing = true; });
  inputEl.addEventListener('compositionend', () => { composing = false; inputEl.value = sanitizeDecalNum(inputEl.value); });
  inputEl.addEventListener('input', () => { if (!composing) inputEl.value = sanitizeDecalNum(inputEl.value); });
}

/* ──────────── 일본어 문자 선택기 ──────────── */

// jp-grid에 문자 버튼 렌더링 후 팝업 위치 지정·표시
async function openJpPicker(targetInput, anchorEl) {
  jpPickerTarget = targetInput;

  let top20 = [];
  try {
    const res = await fetch('/api/admin/japanese-usage/top20');
    if (res.ok) top20 = await res.json();
  } catch {}
  const top10Set = new Set(top20.slice(0, 10));
  const top20Set = new Set(top20.slice(10, 20));

  const grid = document.getElementById('jp-grid');
  grid.innerHTML = '';
  for (const character of JP_CHARS) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = character;
    btn.className = 'text-sm rounded hover:bg-blue-100 py-1';
    if (top10Set.has(character)) {
      btn.style.cssText = 'font-size:14px; line-height:1.4; text-decoration:underline; color:#dc2626;';
    } else if (top20Set.has(character)) {
      btn.style.cssText = 'font-size:14px; line-height:1.4; color:#dc2626;';
    } else {
      btn.style.cssText = 'font-size:14px; line-height:1.4;';
    }
    btn.addEventListener('click', e => {
      e.stopPropagation();
      const target = jpPickerTarget;
      if (target) target.value = character;
      if (target) target.dispatchEvent(new Event('input', { bubbles: true }));
      closeJpPicker();
      if (target) target.focus();
    });
    grid.appendChild(btn);
  }

  const picker = document.getElementById('jp-picker');
  picker.classList.remove('hidden');
  const rect = anchorEl.getBoundingClientRect();
  const W = 240, H = picker.offsetHeight || 280;
  const vw = window.innerWidth, vh = window.innerHeight;
  let left = rect.right + 4;
  let top  = rect.top;
  if (left + W > vw) left = rect.left - W - 4;
  if (left < 4) left = 4;
  if (top + H > vh) top = vh - H - 4;
  if (top < 4) top = 4;
  picker.style.left = left + 'px';
  picker.style.top  = top  + 'px';
}

function closeJpPicker() {
  document.getElementById('jp-picker').classList.add('hidden');
  jpPickerTarget = null;
}

document.getElementById('btn-jp-decal').addEventListener('click', e => {
  e.stopPropagation();
  openJpPicker(document.getElementById('inp-decal-num'), e.currentTarget);
});

document.getElementById('btn-jp-edit').addEventListener('click', e => {
  e.stopPropagation();
  openJpPicker(document.getElementById('inp-edit-num'), e.currentTarget);
});

document.getElementById('btn-jp-close').addEventListener('click', closeJpPicker);

/* ──────────── AI 인식 ──────────── */

let aiSearching = false;
let aiTipTimer  = null;

function showAiTip(anchorEl, message, success) {
  clearTimeout(aiTipTimer);
  const tip   = document.getElementById('ai-tip');
  const inner = document.getElementById('ai-tip-inner');
  inner.textContent = message;
  inner.className = 'text-xs rounded px-2 py-1.5 border shadow-md ' + (success
    ? 'bg-green-50 text-green-700 border-green-200'
    : 'bg-red-50 text-red-600 border-red-200');
  tip.classList.remove('hidden');
  const rect = anchorEl.getBoundingClientRect();
  const W = inner.offsetWidth || 120, H = inner.offsetHeight || 32;
  const vw = window.innerWidth, vh = window.innerHeight;
  let left = rect.right + 4;
  let top  = rect.top + (rect.height - H) / 2;
  if (left + W > vw) left = rect.left - W - 4;
  if (left < 4) left = 4;
  if (top + H > vh) top = vh - H - 4;
  if (top < 4) top = 4;
  tip.style.left = left + 'px';
  tip.style.top  = top  + 'px';
  aiTipTimer = setTimeout(hideAiTip, 3000);
  tip.onmouseleave = hideAiTip;
}

function hideAiTip() {
  clearTimeout(aiTipTimer);
  document.getElementById('ai-tip').classList.add('hidden');
}

async function doAiRecognize(numInputId, btnEl, page, x, y) {
  if (aiSearching || !currentManual) return;
  aiSearching = true;
  hideAiTip();
  const icon = btnEl.querySelector('i');
  const origClass = icon.className;
  icon.className = 'fas fa-spinner fa-spin text-xs';
  btnEl.disabled = true;
  try {
    const image = await captureCrop(page, x, y, CROP_RADIUS_NUMBER_PT, CROP_OUTPUT_NUMBER_PX);
    // manualId는 경로 변수로 전달하므로 body에서 제외
    const res = await fetch(`/api/admin/manuals/${currentManual.id}/recognize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image }),
    });
    if (!res.ok) { showAiTip(btnEl, '오류가 발생했습니다', false); return; }
    const data = await res.json();
    if (data.found && data.character) {
      const input = document.getElementById(numInputId);
      input.value = data.character;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.focus();
      showAiTip(btnEl, '"' + data.character + '" 인식됨', true);
    } else {
      showAiTip(btnEl, '찾을 수 없습니다', false);
    }
  } catch {
    showAiTip(btnEl, '오류가 발생했습니다', false);
  } finally {
    icon.className = origClass;
    btnEl.disabled = false;
    aiSearching = false;
    focusActiveDecalNum();
  }
}

function resetOnnxBtn() {
  const btn = document.getElementById('btn-onnx-decal');
  if (!btn) return;
  btn.innerHTML = '<i class="fas fa-microchip text-xs"></i>';
  btn.disabled = false;
  btn.style.boxShadow = '';
  delete btn.dataset.onnxValue;
}

async function doOnnxRecognize(btnEl, page, x, y) {
  if (!currentManual || !window.onnxAvailable) return;
  btnEl.innerHTML = '<i class="fas fa-spinner fa-spin text-xs"></i>';
  btnEl.disabled = true;
  let recognizedValue = null;
  try {
    const image = await captureCrop(page, x, y, CROP_RADIUS_ONNX_PT, CROP_OUTPUT_ONNX_PX);
    const res = await fetch(`/api/admin/manuals/${currentManual.id}/recognize-onnx`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image }),
    });
    if (res.ok && !document.getElementById('decal-modal').classList.contains('hidden')) {
      const data = await res.json();
      if (data.found && data.character) recognizedValue = data.character;
    }
  } catch {
    // 인식 실패는 조용히 무시
  } finally {
    if (document.getElementById('decal-modal').classList.contains('hidden')) return;
    if (recognizedValue) {
      btnEl.textContent = recognizedValue;
      btnEl.dataset.onnxValue = recognizedValue;
      btnEl.disabled = false;
      btnEl.style.boxShadow = '0 0 0 1px #22c55e';
    } else {
      btnEl.innerHTML = '<i class="fas fa-question text-xs" style="color:#d1d5db;"></i>';
      btnEl.disabled = false;
      btnEl.style.boxShadow = '';
    }
  }
}

document.getElementById('btn-onnx-decal').addEventListener('click', e => {
  e.stopPropagation();
  const btn = e.currentTarget;
  const input = document.getElementById('inp-decal-num');
  if (!btn.dataset.onnxValue) {
    input.focus();
    return;
  }
  input.value = btn.dataset.onnxValue;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  saveNewDecal();
});

document.getElementById('btn-ai-decal').addEventListener('click', e => {
  e.stopPropagation();
  if (!pendingPos) return;
  doAiRecognize('inp-decal-num', e.currentTarget, pendingPos.page, pendingPos.x, pendingPos.y);
});

document.getElementById('btn-ai-edit').addEventListener('click', e => {
  e.stopPropagation();
  if (!editingDecalId) return;
  const d = allDecals.find(x => x.id === editingDecalId);
  if (!d) return;
  doAiRecognize('inp-edit-num', e.currentTarget, d.page, d.x, d.y);
});

async function doAiColorRecognize(hexInputId, colorInputId, numInputId, btnEl, page, x, y) {
  if (aiSearching || !currentManual) return;
  aiSearching = true;
  hideAiTip();
  const icon = btnEl.querySelector('i');
  const origClass = icon.className;
  icon.className = 'fas fa-spinner fa-spin text-xs';
  btnEl.disabled = true;
  try {
    const image = await captureCrop(page, x, y, CROP_RADIUS_COLOR_PT, CROP_OUTPUT_COLOR_PX);
    const res = await fetch(`/api/admin/manuals/${currentManual.id}/recognize-color`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image }),
    });
    if (!res.ok) { showAiTip(btnEl, '오류가 발생했습니다', false); return; }
    const data = await res.json();
    if (data.found && data.hex) {
      const hexEl = document.getElementById(hexInputId);
      const colorEl = document.getElementById(colorInputId);
      hexEl.value = data.hex;
      hexEl.dispatchEvent(new Event('input', { bubbles: true }));
      colorEl.value = '#' + data.hex;
      colorEl.dispatchEvent(new Event('input'));
      document.getElementById(numInputId).focus();
      showAiTip(btnEl, '#' + data.hex + ' 인식됨', true);
    } else {
      showAiTip(btnEl, '찾을 수 없습니다', false);
    }
  } catch {
    showAiTip(btnEl, '오류가 발생했습니다', false);
  } finally {
    icon.className = origClass;
    btnEl.disabled = false;
    aiSearching = false;
  }
}

document.getElementById('btn-ai-color-decal').addEventListener('click', e => {
  e.stopPropagation();
  if (!pendingPos) return;
  doAiColorRecognize('inp-decal-hex', 'inp-decal-color', 'inp-decal-num', e.currentTarget, pendingPos.page, pendingPos.x, pendingPos.y);
});

document.getElementById('btn-ai-color-edit').addEventListener('click', e => {
  e.stopPropagation();
  if (!editingDecalId) return;
  const d = allDecals.find(x => x.id === editingDecalId);
  if (!d) return;
  doAiColorRecognize('inp-edit-hex', 'inp-edit-color', 'inp-edit-num', e.currentTarget, d.page, d.x, d.y);
});

document.getElementById('btn-decal-close').addEventListener('click', cancelDecalModal);
document.getElementById('btn-edit-close').addEventListener('click', cancelEditModal);

// ESC 키로 열린 모달 닫기
document.addEventListener('keydown', e => {
  if (e.key !== 'Escape') return;
  if (!document.getElementById('decal-modal').classList.contains('hidden')) { cancelDecalModal(); return; }
  if (!document.getElementById('edit-modal').classList.contains('hidden')) { cancelEditModal(); return; }
  if (!document.getElementById('upload-modal').classList.contains('hidden')) { closeUploadModal(); return; }
  if (!document.getElementById('manual-edit-modal').classList.contains('hidden')) { closeManualEditModal(); return; }
});

// 팝업 외부 클릭 시 닫기
document.addEventListener('mousedown', e => {
  // 컬러 피커 외부 클릭 시 닫기
  if (!cpPopup.classList.contains('hidden') && !cpPopup.contains(e.target)
      && !e.target.closest('#wrap-decal-color') && !e.target.closest('#wrap-edit-color')) {
    closeColorPicker();
  }
  // 컬러 피커 내부 클릭 시 모달 닫기 건너뜀
  if (!cpPopup.classList.contains('hidden') && cpPopup.contains(e.target)) return;

  const picker = document.getElementById('jp-picker');
  if (!picker.classList.contains('hidden') && !picker.contains(e.target)
      && !e.target.closest('#btn-jp-decal') && !e.target.closest('#btn-jp-edit')) {
    closeJpPicker();
  }
  // jp-picker가 열려 있고 클릭이 그 안이면 모달 닫기 건너뜀
  if (!picker.classList.contains('hidden') && picker.contains(e.target)) return;

  // ai-tip 내부 클릭 시 모달 닫기 건너뜀
  const aiTipEl = document.getElementById('ai-tip');
  if (aiTipEl && !aiTipEl.classList.contains('hidden') && aiTipEl.contains(e.target)) return;

  const decalModal = document.getElementById('decal-modal');
  if (!decalModal.classList.contains('hidden') && !decalModal.contains(e.target)) cancelDecalModal();
  const editModal = document.getElementById('edit-modal');
  if (!editModal.classList.contains('hidden') && !editModal.contains(e.target)) cancelEditModal();
});

/* ──────────── 데칼 등록 모달 ──────────── */

function openDecalModal(x, y, clientX, clientY) {
  pendingPos = { x, y, page: currentPage };
  document.getElementById('inp-decal-num').value   = lastDecalStyle.num;
  const dc = lastDecalStyle.color.startsWith('#') ? lastDecalStyle.color : '#ffffff';
  document.getElementById('inp-decal-hex').value   = dc.slice(1).toUpperCase();
  document.getElementById('inp-decal-color').value = dc;
  document.getElementById('inp-decal-color').dispatchEvent(new Event('input'));
  const shapeIdMap = { SQUARE: 'inp-decal-shape-square', DIAMOND: 'inp-decal-shape-diamond' };
  document.getElementById(shapeIdMap[lastDecalStyle.shape] ?? 'inp-decal-shape-circle').checked = true;
  const btnOk = document.getElementById('btn-decal-ok');
  btnOk.disabled = false;
  btnOk.innerHTML = '<i class="fas fa-check text-xs"></i> 저장';
  const modal = document.getElementById('decal-modal');
  modal.classList.remove('hidden');
  const W = 240, H = 190;
  const vw = window.innerWidth, vh = window.innerHeight;
  let left = clientX + 8;
  let top  = clientY + 8;
  if (left + W > vw) left = clientX - W - 8;
  if (left < 4) left = 4;
  if (top + H > vh) top  = clientY - H - 8;
  if (top  < 4) top  = 4;
  modal.style.left = left + 'px';
  modal.style.top  = top  + 'px';
  const onnxBtn = document.getElementById('btn-onnx-decal');
  resetOnnxBtn();
  if (window.onnxAvailable) doOnnxRecognize(onnxBtn, currentPage, x, y);
  setTimeout(() => { const el = document.getElementById('inp-decal-num'); el.focus(); el.select(); }, 50);
}

document.getElementById('btn-decal-ok').addEventListener('click', saveNewDecal);
document.getElementById('inp-decal-num').addEventListener('keydown', e => {
  if (e.key === 'Enter') saveNewDecal();
  if (e.key === 'Escape') cancelDecalModal();
});
document.getElementById('btn-decal-cancel').addEventListener('click', cancelDecalModal);

// 데칼 등록 서버 요청 후 오버레이에 즉시 반영
async function saveNewDecal() {
  const num = document.getElementById('inp-decal-num').value.trim();
  if (!num || !pendingPos) return;
  if (!isValidDecalNum(num)) {
    showToast('데칼번호는 숫자(1~3자리), 영대문자(1자), 일본어 가나(1자)만 입력 가능합니다.');
    document.getElementById('inp-decal-num').focus();
    return;
  }
  const hexVal = document.getElementById('inp-decal-hex').value.replace(/[^0-9a-fA-F]/g, '');
  const color  = '#' + (hexVal.length === 6 ? hexVal.toLowerCase() : 'ffffff');
  const shape = document.querySelector('input[name="decal-shape"]:checked')?.value ?? 'CIRCLE';
  const btn = document.getElementById('btn-decal-ok');
  btn.disabled = true;
  btn.innerHTML = '<i class="fas fa-spinner fa-spin text-xs"></i> 저장';
  try {
    const res = await fetch(`/api/admin/manuals/${currentManual.id}/decals`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pageNumber: pendingPos.page, decalNumber: num, x: pendingPos.x, y: pendingPos.y, color, shape }),
    });
    if (res.ok) {
      allDecals.push(await res.json());
      lastDecalStyle = { color, shape, num };
      await autoUnpublish();
      cancelDecalModal();
      renderOverlay();
    } else {
      btn.disabled = false;
      btn.innerHTML = '<i class="fas fa-check text-xs"></i> 저장';
    }
  } catch {
    btn.disabled = false;
    btn.innerHTML = '<i class="fas fa-check text-xs"></i> 저장';
  }
}

function cancelDecalModal() {
  pendingPos = null;
  closeJpPicker();
  closeColorPicker();
  hideAiTip();
  const modal = document.getElementById('decal-modal');
  modal.classList.add('hidden');
  modal.style.left = '';
  modal.style.top  = '';
  resetOnnxBtn();
}

/* ──────────── 데칼 수정 모달 ──────────── */

function openEditModal(clientX, clientY) {
  const btnOk = document.getElementById('btn-edit-ok');
  btnOk.disabled = false;
  btnOk.innerHTML = '<i class="fas fa-check text-xs"></i> 저장';
  const modal = document.getElementById('edit-modal');
  modal.classList.remove('hidden');
  const W = 240, H = 190;
  const vw = window.innerWidth, vh = window.innerHeight;
  let left = clientX + 8;
  let top  = clientY + 8;
  if (left + W > vw) left = clientX - W - 8;
  if (left < 4) left = 4;
  if (top + H > vh) top  = clientY - H - 8;
  if (top  < 4) top  = 4;
  modal.style.left = left + 'px';
  modal.style.top  = top  + 'px';
  setTimeout(() => { const el = document.getElementById('inp-edit-num'); el.focus(); el.select(); }, 50);
}

function cancelEditModal() {
  editingDecalId = null;
  closeJpPicker();
  closeColorPicker();
  hideAiTip();
  const modal = document.getElementById('edit-modal');
  modal.classList.add('hidden');
  modal.style.left = '';
  modal.style.top  = '';
}

document.getElementById('btn-edit-ok').addEventListener('click', saveEditDecal);
document.getElementById('inp-edit-num').addEventListener('keydown', e => {
  if (e.key === 'Enter') saveEditDecal();
  if (e.key === 'Escape') cancelEditModal();
});
document.getElementById('btn-edit-cancel').addEventListener('click', cancelEditModal);

// 데칼 번호·색상 수정 서버 요청 후 오버레이 갱신
async function saveEditDecal() {
  const num = document.getElementById('inp-edit-num').value.trim();
  if (!num || !editingDecalId) return;
  if (!isValidDecalNum(num)) {
    showToast('데칼번호는 숫자(1~3자리), 영대문자(1자), 일본어 가나(1자)만 입력 가능합니다.');
    document.getElementById('inp-edit-num').focus();
    return;
  }
  const hexVal = document.getElementById('inp-edit-hex').value.replace(/[^0-9a-fA-F]/g, '');
  const color  = '#' + (hexVal.length === 6 ? hexVal.toLowerCase() : 'ffffff');
  const shape = document.querySelector('input[name="edit-shape"]:checked')?.value ?? 'CIRCLE';
  const btn = document.getElementById('btn-edit-ok');
  btn.disabled = true;
  btn.innerHTML = '<i class="fas fa-spinner fa-spin text-xs"></i> 저장';
  try {
    const res = await fetch(`/api/admin/decals/${editingDecalId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decalNumber: num, color, shape }),
    });
    if (res.ok) {
      const updated = await res.json();
      allDecals = allDecals.map(d => d.id === updated.id ? updated : d);
      lastDecalStyle = { ...lastDecalStyle, color: updated.color ?? '#ffffff', shape: updated.shape ?? 'CIRCLE' };
      await autoUnpublish();
      cancelEditModal();
      renderOverlay();
    } else {
      btn.disabled = false;
      btn.innerHTML = '<i class="fas fa-check text-xs"></i> 저장';
    }
  } catch {
    btn.disabled = false;
    btn.innerHTML = '<i class="fas fa-check text-xs"></i> 저장';
  }
}

/* ──────────── 메뉴얼 수정 모달 ──────────── */

function openManualEditModal(id) {
  const m = manualList.find(x => x.id === id);
  if (!m) return;
  editingManualId = id;
  document.getElementById('edit-inp-grade').value = m.grade;
  document.getElementById('edit-inp-model').value = m.modelNumber;
  document.getElementById('edit-inp-name').value  = m.productName;
  document.getElementById('edit-inp-link').value  = m.link ?? '';
  document.getElementById('manual-edit-modal').classList.remove('hidden');
  setTimeout(() => document.getElementById('edit-inp-model').focus(), 50);
}

function closeManualEditModal() {
  editingManualId = null;
  document.getElementById('manual-edit-modal').classList.add('hidden');
}

document.getElementById('btn-manual-edit-cancel').addEventListener('click', closeManualEditModal);

document.getElementById('manual-edit-form').addEventListener('submit', async e => {
  e.preventDefault();
  if (!editingManualId) return;
  const grade       = document.getElementById('edit-inp-grade').value;
  const modelNumber = document.getElementById('edit-inp-model').value.trim();
  const productName = document.getElementById('edit-inp-name').value.trim();
  const link        = document.getElementById('edit-inp-link').value.trim() || null;
  document.getElementById('edit-inp-model').value = modelNumber;
  document.getElementById('edit-inp-name').value  = productName;
  document.getElementById('edit-inp-link').value  = link ?? '';
  if (!productName) { alert('제품명을 입력해주세요.'); return; }
  if (link && !link.startsWith('https://')) { alert('링크는 https://로 시작해야 합니다.'); return; }

  const res = await fetch(`/api/admin/manuals/${editingManualId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ grade, modelNumber, productName, link }),
  });
  if (res.ok) {
    const cached = manualList.find(x => x.id === editingManualId);
    if (cached) { cached.grade = grade; cached.modelNumber = modelNumber; cached.productName = productName; cached.link = link; }
    if (currentManual?.id === editingManualId) {
      currentManual = { ...currentManual, grade, modelNumber, productName, link };
      updatePdfTitle(currentManual);
    }
    updateGridRow(editingManualId, { grade, modelNumber, productName, link });
    await autoUnpublish();
    closeManualEditModal();
  } else {
    alert('수정에 실패했습니다.');
  }
});

/* ──────────── 마커 보이기/숨기기 ──────────── */

document.getElementById('marker-visible').addEventListener('change', e => {
  markersVisible = e.target.checked;
  overlay.style.display = markersVisible ? '' : 'none';
  if (!markersVisible) tooltip.style.display = 'none';
});

/* ──────────── 게시 상태 ──────────── */

// 게시 상태를 서버에 반영하고 그리드 행을 갱신. 실패 시 false 반환
async function setPublished(id, published) {
  const res = await fetch(`/api/admin/${id}/published?published=${published}`, { method: 'PATCH' });
  if (!res.ok) return false;
  updateGridRow(id, { published });
  return true;
}

// 목록 그리드의 게시 여부 셀 클릭 토글
async function togglePublishedRow(id) {
  const row = gridApi.getRowNode(id);
  if (!row) return;
  const newPublished = !row.data.published;
  if (!(await setPublished(id, newPublished))) return;
  if (currentManual?.id === id) currentManual.published = newPublished;
}

// 데칼·메뉴얼 정보가 변경되면 게시 상태를 자동으로 해제
async function autoUnpublish() {
  if (!currentManual?.published) return;
  if (!(await setPublished(currentManual.id, false))) return;
  currentManual.published = false;
}

/* ──────────── 메뉴얼 삭제 ──────────── */

async function deleteManual(id) {
  const m = manualList.find(x => x.id === id);
  const label = m ? `[${m.grade}] ${m.modelNumber} ${m.productName}`.trim() : `ID ${id}`;
  if (!confirm(`"${label}" 메뉴얼을 삭제하시겠습니까?`)) return;
  await fetch(`/api/admin/manuals/${id}`, { method: 'DELETE' });
  if (currentManual?.id === id) closeEditor();
  loadManuals();
}

/* ──────────── 메뉴얼 등록 모달 ──────────── */

let selectedFile   = null;   // 업로드 대기 중인 PDF 파일
let pdfUploadMode  = 'file'; // 'file' | 'url' | 'number'

/* 메뉴얼 번호 태그 입력 (숫자·"_"만 허용, Enter·공백·콤마로 구분) */
const pdfNumberTagify = new Tagify(document.getElementById('inp-pdf-number'), {
  delimiters: ',| ',
  pattern: /^[0-9_]+$/,
  editTags: false,
  dropdown: { enabled: 0 },
  transformTag: tagData => {
    tagData.value = (tagData.value || '').replace(/[^0-9_]/g, '');
  },
});
pdfNumberTagify.on('add', e => {
  if (e.detail.data && !e.detail.data.value) pdfNumberTagify.removeTags(e.detail.tag);
});

function setPdfMode(mode) {
  pdfUploadMode = mode;
  document.getElementById('pdf-file-area').classList.toggle('hidden', mode !== 'file');
  document.getElementById('pdf-url-area').classList.toggle('hidden', mode !== 'url');
  document.getElementById('pdf-number-area').classList.toggle('hidden', mode !== 'number');
  const tabs = { file: 'pdf-tab-file', url: 'pdf-tab-url', number: 'pdf-tab-number' };
  Object.entries(tabs).forEach(([m, id]) => {
    const active = m === mode;
    document.getElementById(id).className =
      `flex-1 py-1.5 font-medium ${active ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-50'}`;
  });
  if (mode !== 'url') document.getElementById('inp-pdf-url').value = '';
  if (mode !== 'number') pdfNumberTagify.removeAllTags();
}

function initDropZone() {
  selectedFile = null;
  const zone = document.getElementById('drop-zone');

  const newZone  = zone.cloneNode(true);
  zone.parentNode.replaceChild(newZone, zone);
  const newInput  = newZone.querySelector('#file-input');
  const newRemove = newZone.querySelector('#drop-remove');
  const newPH     = newZone.querySelector('#drop-placeholder');
  const newFI     = newZone.querySelector('#drop-file-info');
  const newFname  = newZone.querySelector('#drop-filename');
  const newFsize  = newZone.querySelector('#drop-filesize');

  showPlaceholder(true);

  function showPlaceholder(show) {
    newPH.classList.toggle('hidden', !show);
    newFI.classList.toggle('hidden', show);
    newFI.classList.toggle('flex',  !show);
  }

  function setFile(file) {
    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
      alert('PDF 파일만 업로드할 수 있습니다.');
      return;
    }
    selectedFile = file;
    newFname.textContent = file.name;
    newFsize.textContent = fmtSize(file.size);
    showPlaceholder(false);
  }

  function clearFile() {
    selectedFile = null;
    newInput.value = '';
    showPlaceholder(true);
  }

  newZone.addEventListener('click', e => {
    if (newRemove.contains(e.target)) return;
    newInput.click();
  });
  newInput.addEventListener('change', () => {
    if (newInput.files[0]) setFile(newInput.files[0]);
  });
  newZone.addEventListener('dragover', e => {
    e.preventDefault();
    newZone.classList.add('drag-over');
  });
  newZone.addEventListener('dragleave', e => {
    if (!newZone.contains(e.relatedTarget)) newZone.classList.remove('drag-over');
  });
  newZone.addEventListener('drop', e => {
    e.preventDefault();
    newZone.classList.remove('drag-over');
    const file = e.dataTransfer.files[0];
    if (file) setFile(file);
  });
  newRemove.addEventListener('click', e => {
    e.stopPropagation();
    clearFile();
  });
}

function fmtSize(b) {
  if (b < 1024) return b + ' B';
  if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
  return (b / 1048576).toFixed(1) + ' MB';
}

function setFormLoading(loading) {
  ['inp-grade', 'inp-model', 'inp-name', 'inp-link', 'btn-upload-cancel', 'file-input', 'inp-pdf-url', 'pdf-tab-file', 'pdf-tab-url', 'pdf-tab-number'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.disabled = loading;
  });
  pdfNumberTagify.setDisabled(loading);
  const zone = document.getElementById('drop-zone');
  if (zone) zone.classList.toggle('disabled', loading);

  const btn = document.getElementById('btn-submit');
  if (btn) {
    btn.disabled = loading;
    btn.innerHTML = loading
      ? '<i class="fas fa-spinner fa-spin text-xs"></i> 등록 중…'
      : '<i class="fas fa-upload text-xs"></i> 등록';
  }
}

function openUploadModal() {
  document.getElementById('inp-grade').value = lastManualGrade;
  document.getElementById('inp-model').value = '';
  document.getElementById('inp-name').value  = '';
  document.getElementById('inp-link').value  = '';
  document.getElementById('inp-pdf-url').value = '';
  document.getElementById('upload-modal').classList.remove('hidden');
  initDropZone();
  pdfNumberTagify.removeAllTags();
  setPdfMode('url');
  setFormLoading(false);
  setTimeout(() => document.getElementById('inp-grade').focus(), 50);
}

function closeUploadModal() {
  document.getElementById('upload-modal').classList.add('hidden');
  selectedFile  = null;
  pdfUploadMode = 'file';
  setFormLoading(false);
}

document.getElementById('btn-upload').addEventListener('click', openUploadModal);
document.getElementById('btn-upload-cancel').addEventListener('click', closeUploadModal);
document.getElementById('pdf-tab-file').addEventListener('click',   () => setPdfMode('file'));
document.getElementById('pdf-tab-url').addEventListener('click',    () => setPdfMode('url'));
document.getElementById('pdf-tab-number').addEventListener('click', () => setPdfMode('number'));

document.getElementById('upload-form').addEventListener('submit', async e => {
  e.preventDefault();
  const grade       = document.getElementById('inp-grade').value;
  const modelNumber = document.getElementById('inp-model').value.trim();
  const productName = document.getElementById('inp-name').value.trim();
  const link        = document.getElementById('inp-link').value.trim();
  document.getElementById('inp-model').value = modelNumber;
  document.getElementById('inp-name').value  = productName;
  document.getElementById('inp-link').value  = link;
  if (!grade)       { alert('등급을 선택해주세요.'); return; }
  if (!productName) { alert('제품명을 입력해주세요.'); return; }
  if (link && !link.startsWith('https://')) { alert('링크는 https://로 시작해야 합니다.'); return; }

  const fd = new FormData();
  fd.append('grade', grade);
  fd.append('modelNumber', modelNumber);
  fd.append('productName', productName);
  if (link) fd.append('link', link);

  if (pdfUploadMode === 'file') {
    if (!selectedFile) { alert('PDF 파일을 선택해주세요.'); return; }
    fd.append('pdf', selectedFile);
  } else if (pdfUploadMode === 'url') {
    const pdfUrl = document.getElementById('inp-pdf-url').value.trim();
    document.getElementById('inp-pdf-url').value = pdfUrl;
    if (!pdfUrl) { alert('PDF URL을 입력해주세요.'); return; }
    fd.append('pdfUrl', pdfUrl);
  } else {
    const pdfNumbers = pdfNumberTagify.value.map(t => t.value).filter(Boolean);
    if (pdfNumbers.length === 0) { alert('메뉴얼 번호를 입력해주세요.'); return; }
    pdfNumbers.forEach(n => fd.append('pdfNumbers', n));
  }

  setFormLoading(true);
  try {
    const res = await fetch('/api/admin/manuals', { method: 'POST', body: fd });
    if (res.status === 202) {
      lastManualGrade = grade;
      closeUploadModal();
    } else {
      const body = await res.json().catch(() => ({}));
      alert(body.message || '등록에 실패했습니다.');
      setFormLoading(false);
    }
  } catch {
    alert('등록 중 오류가 발생했습니다.');
    setFormLoading(false);
  }
});


/* ──────────── 형식번호 유효성 검사 ──────────── */

function sanitizeModelNumber(val) {
  return val.replace(/[\x00-\x1F\x7F]/g, '');
}

function applyModelNumValidation(inputEl) {
  inputEl.addEventListener('input', () => {
    const pos = inputEl.selectionStart;
    const cleaned = sanitizeModelNumber(inputEl.value);
    if (cleaned !== inputEl.value) {
      inputEl.value = cleaned;
      inputEl.setSelectionRange(pos - 1, pos - 1);
    }
  });
}

/* ──────────── 초기화 ──────────── */
applyDecalNumValidation(document.getElementById('inp-decal-num'));
applyDecalNumValidation(document.getElementById('inp-edit-num'));
document.querySelectorAll('input[name="decal-shape"], input[name="edit-shape"]').forEach(radio =>
  radio.addEventListener('change', focusActiveDecalNum)
);
applyModelNumValidation(document.getElementById('inp-model'));
applyModelNumValidation(document.getElementById('edit-inp-model'));

// 검색은 항상 서버 조회. 텍스트 입력은 500ms 디바운스(Enter는 즉시), 셀렉트 변경은 즉시
let searchTimer = null;
['f-model', 'f-name'].forEach(id => {
  const el = document.getElementById(id);
  el.addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(loadManuals, 500);
  });
  el.addEventListener('keydown', e => {
    // IME 조합 확정용 Enter는 검색으로 처리하지 않는다
    if (e.key !== 'Enter' || e.isComposing) return;
    clearTimeout(searchTimer);
    loadManuals();
  });
});
['f-grade', 'f-published'].forEach(id =>
  document.getElementById(id).addEventListener('change', loadManuals));
document.getElementById('btn-reset').addEventListener('click', resetSearch);

loadManuals();

/* ──────────── SSE: 메뉴얼 등록 결과 수신 ──────────── */
const sseSource = new EventSource(`${window.contextPath}/api/admin/sse`);
sseSource.addEventListener('manual-created', e => {
  const manual = JSON.parse(e.data);
  prependManualToList(manual);
  highlightManual(manual.id, 3000);
});
sseSource.addEventListener('manual-failed', e => {
  const { message } = JSON.parse(e.data);
  showToast(message || '메뉴얼 등록에 실패했습니다.');
});
