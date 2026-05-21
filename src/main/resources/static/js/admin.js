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
let lastDecalStyle  = { color: '#ffffff', shape: 'CIRCLE' }; // 마지막으로 사용한 데칼 스타일

// 일본어 문자 선택기 상태
let japaneseCharUsages = []; // [{character, count}, ...] — 초기 로드 후 재사용
let jpPickerTarget     = null; // 현재 일본어 선택기가 값을 채울 input 요소

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

/* ──────────── 메뉴얼 목록 ──────────── */

// 관리자 API에서 메뉴얼 목록 로드 후 현재 검색어로 필터링
async function loadManuals() {
  const [manualsRes, jpRes] = await Promise.all([
    fetch('/api/admin/manuals'),
    japaneseCharUsages.length === 0 ? fetch('/api/admin/manuals/japanese-chars') : Promise.resolve(null),
  ]);
  manualList = await manualsRes.json();
  if (jpRes) japaneseCharUsages = await jpRes.json();

  // 아이콘 목록: 전체 표시
  const iconEl = document.getElementById('sb-icons');
  iconEl.querySelectorAll('.manual-icon-item').forEach(e => e.remove());
  manualList.forEach(m => {
    const btn = document.createElement('button');
    btn.className = 'manual-icon-item sb-icon-tip w-8 h-8 flex items-center justify-center rounded hover:bg-gray-700 text-gray-400 hover:text-white';
    btn.dataset.id  = m.id;
    btn.dataset.tip = `[${m.grade}] ${m.modelNumber} ${m.productName}`;
    btn.innerHTML   = '<i class="fas fa-file-pdf text-sm"></i>';
    btn.addEventListener('click', () => selectManual(+btn.dataset.id));
    iconEl.appendChild(btn);
  });

  // 텍스트 목록: 현재 검색어 적용 (서버 사이드)
  await searchManuals();

  window.dispatchEvent(new Event('resize'));
}

// 현재 검색어로 서버에 요청해 텍스트 목록 렌더링 (manualList 캐시는 변경하지 않음)
async function searchManuals() {
  const q = (document.getElementById('manual-search')?.value ?? '').trim();
  const list = q
    ? await (await fetch(`/api/admin/manuals?q=${encodeURIComponent(q)}`)).json()
    : manualList;
  renderManualItems(list);
}

// 메뉴얼 목록 DOM 렌더링 및 이벤트 연결
function renderManualItems(list) {
  const el = document.getElementById('manual-list');
  if (!list.length) {
    const q = (document.getElementById('manual-search')?.value ?? '').trim();
    el.innerHTML = q
      ? '<p class="text-gray-500 text-xs p-2">검색 결과 없음</p>'
      : '<p class="text-gray-500 text-xs p-2">등록된 메뉴얼이 없습니다</p>';
    return;
  }
  el.innerHTML = list.map(m => `
    <div class="manual-item group px-2 py-1.5 rounded cursor-pointer hover:bg-gray-700 transition-colors" data-id="${m.id}">
      <div class="flex items-center gap-1 mb-0.5">
        <span class="grade-badge grade-${esc(m.grade)}">${esc(m.grade)}</span>
        <span class="text-xs font-medium text-gray-200 leading-snug truncate flex-1">${esc(m.modelNumber)}</span>
        <button class="btn-pub opacity-0 group-hover:opacity-100 flex-shrink-0 w-5 h-5 flex items-center justify-center" data-id="${m.id}" title="${m.published ? '공개중' : '미공개'}">
          <i class="fas fa-${m.published ? 'eye text-green-400' : 'eye-slash text-gray-500'} text-xs"></i>
        </button>
        <button class="btn-edit opacity-0 group-hover:opacity-100 flex-shrink-0 text-gray-500 hover:text-blue-400 w-5 h-5 flex items-center justify-center" data-id="${m.id}" title="수정">
          <i class="fas fa-pen text-xs"></i>
        </button>
        <button class="btn-del opacity-0 group-hover:opacity-100 flex-shrink-0 text-gray-500 hover:text-red-400 w-5 h-5 flex items-center justify-center" data-id="${m.id}" title="삭제">
          <i class="fas fa-trash text-xs"></i>
        </button>
      </div>
      <div class="text-xs text-gray-400 leading-snug truncate">${esc(m.productName)}</div>
    </div>`).join('');

  if (currentManual) {
    el.querySelector(`.manual-item[data-id="${currentManual.id}"]`)?.classList.add('bg-gray-600');
  }

  el.querySelectorAll('.manual-item').forEach(item =>
    item.addEventListener('click', e => {
      if (e.target.closest('.btn-pub') || e.target.closest('.btn-edit') || e.target.closest('.btn-del')) return;
      selectManual(+item.dataset.id);
    }));
  el.querySelectorAll('.btn-pub').forEach(btn =>
    btn.addEventListener('click', async e => {
      e.stopPropagation();
      const updated = await (await fetch(`/api/admin/manuals/${btn.dataset.id}/published`, { method: 'PATCH' })).json();
      const cached = manualList.find(x => x.id === +btn.dataset.id);
      if (cached) cached.published = updated.published;
      const icon = btn.querySelector('i');
      icon.className = `fas fa-${updated.published ? 'eye text-green-400' : 'eye-slash text-gray-500'} text-xs`;
      btn.title = updated.published ? '공개중' : '미공개';
    }));
  el.querySelectorAll('.btn-edit').forEach(btn =>
    btn.addEventListener('click', () => openManualEditModal(+btn.dataset.id)));
  el.querySelectorAll('.btn-del').forEach(btn =>
    btn.addEventListener('click', () => deleteManual(+btn.dataset.id)));
}

// 메뉴얼 선택: 목록 하이라이트 업데이트 후 PDF 로드
async function selectManual(id) {
  if (manualLoading) return;
  manualLoading = true;
  try {
    document.querySelectorAll('.manual-item').forEach(e => e.classList.remove('bg-gray-600'));
    document.querySelector(`.manual-item[data-id="${id}"]`)?.classList.add('bg-gray-600');

    document.querySelectorAll('.manual-icon-item').forEach(e => {
      e.classList.remove('bg-gray-600');
      e.querySelector('i').className = 'fas fa-file-pdf text-sm';
    });
    const activeIcon = document.querySelector(`.manual-icon-item[data-id="${id}"]`);
    if (activeIcon) {
      activeIcon.classList.add('bg-gray-600');
      activeIcon.querySelector('i').className = 'fas fa-file-pdf text-sm text-white';
    }

    // 스켈레톤 표시 (pdfScroll은 visible — fitToContainer 치수 계산에 필요)
    noPdf.style.display = 'none';
    pdfScroll.style.display = '';
    document.getElementById('zoom-overlay').style.display = 'none';
    document.getElementById('pdf-loading').style.display = 'flex';
    thumbStrip.innerHTML = '<div class="strip-inner"><span class="text-gray-500 text-xs select-none">로딩 중…</span></div>';

    const data = await (await fetch(`/api/admin/manuals/${id}`)).json();
    currentManual = data; allDecals = data.decals;
    lastDecalStyle = { color: '#ffffff', shape: 'CIRCLE' };

    pdfDoc = await pdfjsLib.getDocument(`${window.contextPath}/api/admin/manuals/${id}/pdf`).promise;
    currentPage = 1;
    await renderPage(currentPage, true);

    // 스켈레톤 숨기고 PDF 공개
    document.getElementById('pdf-loading').style.display = '';
    document.getElementById('zoom-overlay').style.display = 'flex';

    renderThumbnails();
  } finally {
    manualLoading = false;
  }
}

/* ──────────── 데칼 오버레이 ──────────── */

// 현재 페이지의 데칼 마커를 오버레이에 렌더링 (common.js의 renderPage에서 호출)
function renderOverlay() {
  overlay.innerHTML = allDecals.filter(d => d.page === currentPage).map(d => `
    <div class="decal-marker" data-id="${d.id}"
         style="left:${d.x}%;top:${d.y}%;transform:translate(-50%,-50%);${decalMarkerStyle(d.color, d.shape)}"
         title="${esc(d.decalNumber)}">
      ${esc(d.decalNumber.slice(0, 4))}
    </div>`).join('');

  overlay.querySelectorAll('.decal-marker').forEach(m =>
    m.addEventListener('click', e => {
      e.stopPropagation();
      showTooltip(+m.dataset.id);
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

// 편집 버튼: 툴팁 위치 근처에 수정 모달 열기
document.getElementById('tt-edit').addEventListener('click', e => {
  e.stopPropagation();
  if (!tooltipDecalId) return;
  const d = allDecals.find(x => x.id === tooltipDecalId);
  if (!d) return;
  editingDecalId = tooltipDecalId;
  document.getElementById('inp-edit-num').value   = d.decalNumber;
  const ec = d.color ?? '#ffffff';
  document.getElementById('inp-edit-hex').value   = ec.slice(1).toUpperCase();
  document.getElementById('inp-edit-color').value = ec;
  document.getElementById('inp-edit-color').dispatchEvent(new Event('input'));
  const shapeRadio = document.querySelector(`input[name="edit-shape"][value="${d.shape ?? 'CIRCLE'}"]`);
  if (shapeRadio) shapeRadio.checked = true;
  hideTooltip();
  openEditModal(e.clientX, e.clientY);
});

// 삭제 버튼: 서버에서 데칼 삭제 후 오버레이 갱신
document.getElementById('tt-delete').addEventListener('click', async e => {
  e.stopPropagation();
  if (!tooltipDecalId) return;
  const id = tooltipDecalId;
  hideTooltip();
  await fetch(`/api/admin/manuals/${currentManual.id}/decals/${id}`, { method: 'DELETE' });
  allDecals = allDecals.filter(d => d.id !== id);
  renderOverlay();
});

/* ──────────── 색상 프리셋 버튼 ──────────── */

document.addEventListener('click', e => {
  const btn = e.target.closest('.color-preset-btn');
  if (!btn) return;
  const hexId   = btn.dataset.hexTarget;
  const colorId = btn.dataset.colorTarget;
  const color   = btn.dataset.color;
  if (hexId)   document.getElementById(hexId).value   = color.slice(1).toUpperCase();
  if (colorId) {
    document.getElementById(colorId).value = color;
    document.getElementById(colorId).dispatchEvent(new Event('input'));
  }
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
  if (/^[A-Za-z]/.test(first)) return val.replace(/[^A-Za-z]/g, '').slice(0, 1).toUpperCase();
  if (/^[぀-ゟ゠-ヿ]/.test(first))
    return val.replace(/[^぀-ゟ゠-ヿ]/g, '').slice(0, 1);
  return val.slice(0, 1);
}

function applyDecalNumValidation(inputEl) {
  let composing = false;
  inputEl.addEventListener('compositionstart', () => { composing = true; });
  inputEl.addEventListener('compositionend', () => { composing = false; inputEl.value = sanitizeDecalNum(inputEl.value); });
  inputEl.addEventListener('input', () => { if (!composing) inputEl.value = sanitizeDecalNum(inputEl.value); });
}

/* ──────────── 일본어 문자 선택기 ──────────── */

// jp-grid에 문자 버튼 렌더링 후 팝업 위치 지정·표시
function openJpPicker(targetInput, anchorEl) {
  jpPickerTarget = targetInput;
  const grid = document.getElementById('jp-grid');
  grid.innerHTML = '';
  japaneseCharUsages.forEach(({ character }) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = character;
    btn.className = 'text-sm rounded hover:bg-blue-100 py-1';
    btn.style.cssText = 'font-size:14px; line-height:1.4;';
    btn.addEventListener('click', e => {
      e.stopPropagation();
      const target = jpPickerTarget;
      if (target) target.value = character;
      if (target) target.dispatchEvent(new Event('input', { bubbles: true }));
      closeJpPicker();
      if (target) target.focus();
    });
    grid.appendChild(btn);
  });

  const picker = document.getElementById('jp-picker');
  picker.classList.remove('hidden');
  const rect = anchorEl.getBoundingClientRect();
  const W = 200, H = picker.offsetHeight || 280;
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
document.getElementById('btn-decal-close').addEventListener('click', cancelDecalModal);
document.getElementById('btn-edit-close').addEventListener('click', cancelEditModal);

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

  const decalModal = document.getElementById('decal-modal');
  if (!decalModal.classList.contains('hidden') && !decalModal.contains(e.target)) cancelDecalModal();
  const editModal = document.getElementById('edit-modal');
  if (!editModal.classList.contains('hidden') && !editModal.contains(e.target)) cancelEditModal();
});

/* ──────────── 데칼 등록 모달 ──────────── */

function openDecalModal(x, y, clientX, clientY) {
  pendingPos = { x, y, page: currentPage };
  document.getElementById('inp-decal-num').value   = '';
  const dc = lastDecalStyle.color.startsWith('#') ? lastDecalStyle.color : '#ffffff';
  document.getElementById('inp-decal-hex').value   = dc.slice(1).toUpperCase();
  document.getElementById('inp-decal-color').value = dc;
  document.getElementById('inp-decal-color').dispatchEvent(new Event('input'));
  document.getElementById(lastDecalStyle.shape === 'SQUARE' ? 'inp-decal-shape-square' : 'inp-decal-shape-circle').checked = true;
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
  setTimeout(() => document.getElementById('inp-decal-num').focus(), 50);
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
  const hexVal = document.getElementById('inp-decal-hex').value.replace(/[^0-9a-fA-F]/g, '');
  const color  = '#' + (hexVal.length === 6 ? hexVal.toLowerCase() : 'ffffff');
  const shape = document.querySelector('input[name="decal-shape"]:checked')?.value ?? 'CIRCLE';
  const res = await fetch(`/api/admin/manuals/${currentManual.id}/decals`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ page: pendingPos.page, decalNumber: num, x: pendingPos.x, y: pendingPos.y, color, shape }),
  });
  if (res.ok) {
    allDecals.push(await res.json());
    lastDecalStyle = { color, shape };
    cancelDecalModal();
    renderOverlay();
  }
}

function cancelDecalModal() {
  pendingPos = null;
  closeJpPicker();
  closeColorPicker();
  const modal = document.getElementById('decal-modal');
  modal.classList.add('hidden');
  modal.style.left = '';
  modal.style.top  = '';
}

/* ──────────── 데칼 수정 모달 ──────────── */

function openEditModal(clientX, clientY) {
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
  setTimeout(() => document.getElementById('inp-edit-num').focus(), 50);
}

function cancelEditModal() {
  editingDecalId = null;
  closeJpPicker();
  closeColorPicker();
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
  const hexVal = document.getElementById('inp-edit-hex').value.replace(/[^0-9a-fA-F]/g, '');
  const color  = '#' + (hexVal.length === 6 ? hexVal.toLowerCase() : 'ffffff');
  const shape = document.querySelector('input[name="edit-shape"]:checked')?.value ?? 'CIRCLE';
  const res = await fetch(`/api/admin/manuals/${currentManual.id}/decals/${editingDecalId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ decalNumber: num, color, shape }),
  });
  if (res.ok) {
    const updated = await res.json();
    allDecals = allDecals.map(d => d.id === updated.id ? updated : d);
    cancelEditModal();
    renderOverlay();
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
    }
    const item = document.querySelector(`.manual-item[data-id="${editingManualId}"]`);
    if (item) {
      const gradeEl = item.querySelector('.grade-badge');
      if (gradeEl) { gradeEl.className = `grade-badge grade-${grade}`; gradeEl.textContent = grade; }
      const modelEl = item.querySelector('.text-gray-200.font-medium');
      if (modelEl) modelEl.textContent = modelNumber;
      const nameEl = item.querySelector('.text-xs.text-gray-400');
      if (nameEl) nameEl.textContent = productName;
    }
    const icon = document.querySelector(`.manual-icon-item[data-id="${editingManualId}"]`);
    if (icon) icon.dataset.tip = `[${grade}] ${modelNumber} ${productName}`;
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

/* ──────────── 메뉴얼 삭제 ──────────── */

async function deleteManual(id) {
  const m = manualList.find(x => x.id === id);
  const label = m ? `[${m.grade}] ${m.modelNumber} ${m.productName}`.trim() : `ID ${id}`;
  if (!confirm(`"${label}" 메뉴얼을 삭제하시겠습니까?`)) return;
  await fetch(`/api/admin/manuals/${id}`, { method: 'DELETE' });
  if (currentManual?.id === id) {
    currentManual = null; pdfDoc = null; allDecals = [];
    pdfScroll.style.display = 'none';
    noPdf.style.display = '';
    thumbStrip.innerHTML = '<div class="strip-inner"><span class="text-gray-500 text-xs select-none">메뉴얼을 선택하세요</span></div>';
  }
  loadManuals();
}

/* ──────────── 메뉴얼 등록 모달 ──────────── */

let selectedFile   = null;   // 업로드 대기 중인 PDF 파일
let pdfUploadMode  = 'file'; // 'file' | 'url'

function setPdfMode(mode) {
  pdfUploadMode = mode;
  const isFile = mode === 'file';
  document.getElementById('pdf-file-area').classList.toggle('hidden', !isFile);
  document.getElementById('pdf-url-area').classList.toggle('hidden', isFile);
  const tabFile = document.getElementById('pdf-tab-file');
  const tabUrl  = document.getElementById('pdf-tab-url');
  tabFile.className = `flex-1 py-1.5 font-medium ${isFile  ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-50'}`;
  tabUrl.className  = `flex-1 py-1.5 font-medium ${!isFile ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-50'}`;
  if (!isFile) document.getElementById('inp-pdf-url').value = '';
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
  ['inp-grade', 'inp-model', 'inp-name', 'inp-link', 'btn-upload-cancel', 'file-input', 'inp-pdf-url', 'pdf-tab-file', 'pdf-tab-url'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.disabled = loading;
  });
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
  document.getElementById('inp-grade').value = 'RG';
  document.getElementById('inp-model').value = '';
  document.getElementById('inp-name').value  = '';
  document.getElementById('inp-link').value  = '';
  document.getElementById('upload-modal').classList.remove('hidden');
  initDropZone();
  setPdfMode('file');
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
document.getElementById('btn-upload-icon').addEventListener('click', openUploadModal);
document.getElementById('btn-upload-cancel').addEventListener('click', closeUploadModal);
document.getElementById('pdf-tab-file').addEventListener('click', () => setPdfMode('file'));
document.getElementById('pdf-tab-url').addEventListener('click',  () => setPdfMode('url'));

document.getElementById('upload-form').addEventListener('submit', async e => {
  e.preventDefault();
  const grade       = document.getElementById('inp-grade').value;
  const modelNumber = document.getElementById('inp-model').value.trim();
  const productName = document.getElementById('inp-name').value.trim();
  const link        = document.getElementById('inp-link').value.trim();
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
  } else {
    const pdfUrl = document.getElementById('inp-pdf-url').value.trim();
    if (!pdfUrl) { alert('PDF URL을 입력해주세요.'); return; }
    fd.append('pdfUrl', pdfUrl);
  }

  setFormLoading(true);
  try {
    const res = await fetch('/api/admin/manuals', { method: 'POST', body: fd });
    if (res.ok) {
      const created = await res.json();
      closeUploadModal();
      await loadManuals();
      await selectManual(created.id);
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


/* ──────────── 새로고침 ──────────── */
document.getElementById('sb-refresh').addEventListener('click', loadManuals);


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
applyModelNumValidation(document.getElementById('inp-model'));
applyModelNumValidation(document.getElementById('edit-inp-model'));

PrettyScroll('#manual-list', { barWidth: 6, barColor: 'rgba(156,163,175,0.5)', right: 2, autoHide: true });

let searchTimer = null;
document.getElementById('manual-search').addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(searchManuals, 150);
});

loadManuals();
