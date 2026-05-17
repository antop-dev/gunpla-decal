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
let tooltipDecalId = null; // 현재 툴팁이 표시된 데칼 ID
let pendingPos     = null; // 데칼 등록 모달에서 사용할 클릭 위치 {x, y, page}
let editingDecalId = null; // 수정 모달에서 편집 중인 데칼 ID

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

// 관리자 API에서 메뉴얼 목록 로드 (수정·삭제 버튼 포함)
async function loadManuals() {
  manualList   = await (await fetch('/api/admin/manuals')).json();
  const el     = document.getElementById('manual-list');
  const iconEl = document.getElementById('sb-icons');

  if (!manualList.length) {
    el.innerHTML = '<p class="text-gray-500 text-xs p-2">등록된 메뉴얼이 없습니다</p>';
    iconEl.querySelectorAll('.manual-icon-item').forEach(e => e.remove());
    return;
  }

  el.innerHTML = manualList.map(m => `
    <div class="manual-item group px-2 py-1.5 rounded cursor-pointer hover:bg-gray-700 transition-colors" data-id="${m.id}">
      <div class="flex items-center gap-1 mb-0.5">
        <span class="grade-badge grade-${esc(m.grade)}">${esc(m.grade)}</span>
        <span class="text-xs font-medium text-gray-200 leading-snug truncate flex-1">${esc(m.modelNumber)}</span>
        <button class="btn-edit opacity-0 group-hover:opacity-100 flex-shrink-0 text-gray-500 hover:text-blue-400 w-5 h-5 flex items-center justify-center" data-id="${m.id}" title="수정">
          <i class="fas fa-pen text-xs"></i>
        </button>
        <button class="btn-del opacity-0 group-hover:opacity-100 flex-shrink-0 text-gray-500 hover:text-red-400 w-5 h-5 flex items-center justify-center" data-id="${m.id}" title="삭제">
          <i class="fas fa-trash text-xs"></i>
        </button>
      </div>
      <div class="text-xs text-gray-400 leading-snug truncate">${esc(m.productName)}</div>
    </div>`).join('');

  // 아이콘 목록: 기존 항목 제거 후 재생성
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

  el.querySelectorAll('.manual-item').forEach(item =>
    item.addEventListener('click', e => {
      if (e.target.closest('.btn-edit') || e.target.closest('.btn-del')) return;
      selectManual(+item.dataset.id);
    }));
  el.querySelectorAll('.btn-edit').forEach(btn =>
    btn.addEventListener('click', () => openManualEditModal(+btn.dataset.id)));
  el.querySelectorAll('.btn-del').forEach(btn =>
    btn.addEventListener('click', () => deleteManual(+btn.dataset.id)));
  window.dispatchEvent(new Event('resize'));
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

    const data = await (await fetch(`/api/manuals/${id}`)).json();
    currentManual = data; allDecals = data.decals;

    pdfDoc = await pdfjsLib.getDocument(`${window.contextPath}/api/manuals/${id}/pdf`).promise;
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
// 클릭 시 툴팁(편집·삭제) 표시
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
}

/* ──────────── 마커 툴팁 ──────────── */

// 마커 클릭 시 편집·삭제 툴팁을 마커 우하단에 표시
function showTooltip(decalId) {
  tooltipDecalId = decalId;
  tooltip.style.display = 'flex';
  repositionTooltip();
}

// 스크롤·줌 변경 시 툴팁 위치 재계산. 마커가 화면 밖이면 자동 숨김
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

// Esc로 툴팁 닫기
document.addEventListener('keydown', e => { if (e.key === 'Escape') hideTooltip(); });

// 툴팁 내 mousedown/up은 컨테이너 이벤트로 전파 방지 (드래그 오판 방지)
tooltip.addEventListener('mousedown', e => e.stopPropagation());
tooltip.addEventListener('mouseup',   e => e.stopPropagation());

// 편집 버튼: 툴팁 위치 근처에 수정 모달 열기
document.getElementById('tt-edit').addEventListener('click', e => {
  e.stopPropagation();
  if (!tooltipDecalId) return;
  const d = allDecals.find(x => x.id === tooltipDecalId);
  if (!d) return;
  editingDecalId = tooltipDecalId;
  document.getElementById('inp-edit-num').value = d.decalNumber;
  const colorRadio = document.querySelector(`input[name="edit-color"][value="${d.color ?? 'WHITE'}"]`);
  if (colorRadio) colorRadio.checked = true;
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

/* ──────────── 데칼 번호 입력 유효성 ──────────── */

// 입력값을 규칙에 맞게 정제: 숫자(3자리) / 영문 대문자(1자) / 일본어(1자)
function sanitizeDecalNum(val) {
  if (!val) return '';
  const first = val[0];
  if (/^\d/.test(first)) return val.replace(/\D/g, '').slice(0, 3);
  if (/^[A-Za-z]/.test(first)) return val.replace(/[^A-Za-z]/g, '').slice(0, 1).toUpperCase();
  if (/^[\u3040-\u309F\u30A0-\u30FF]/.test(first))
    return val.replace(/[^\u3040-\u309F\u30A0-\u30FF]/g, '').slice(0, 1);
  return val.slice(0, 1);
}

// 입력 필드에 데칼 번호 유효성 검사 연결 (IME 조합 중에는 검사 보류)
function applyDecalNumValidation(inputEl) {
  let composing = false;
  inputEl.addEventListener('compositionstart', () => { composing = true; });
  inputEl.addEventListener('compositionend', () => { composing = false; inputEl.value = sanitizeDecalNum(inputEl.value); });
  inputEl.addEventListener('input', () => { if (!composing) inputEl.value = sanitizeDecalNum(inputEl.value); });
}

/* ──────────── 데칼 등록 모달 ──────────── */

// 클릭 위치 근처에 데칼 번호 입력 팝업 표시 (뷰포트 경계 안으로 클램핑)
function openDecalModal(x, y, clientX, clientY) {
  pendingPos = { x, y, page: currentPage };
  document.getElementById('inp-decal-num').value = '';
  document.getElementById('inp-decal-color-white').checked = true;
  document.getElementById('inp-decal-shape-circle').checked = true;
  const modal = document.getElementById('decal-modal');
  modal.classList.remove('hidden');
  const W = 230, H = 170;
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

// 모달 외부 클릭 시 닫기 (등록·수정 모달 공통)
document.addEventListener('mousedown', e => {
  const decalModal = document.getElementById('decal-modal');
  if (!decalModal.classList.contains('hidden') && !decalModal.contains(e.target)) cancelDecalModal();
  const editModal = document.getElementById('edit-modal');
  if (!editModal.classList.contains('hidden') && !editModal.contains(e.target)) cancelEditModal();
});

// 데칼 등록 서버 요청 후 오버레이에 즉시 반영
async function saveNewDecal() {
  const num = document.getElementById('inp-decal-num').value.trim();
  if (!num || !pendingPos) return;
  const color = document.querySelector('input[name="decal-color"]:checked')?.value ?? 'WHITE';
  const shape = document.querySelector('input[name="decal-shape"]:checked')?.value ?? 'CIRCLE';
  const res = await fetch(`/api/admin/manuals/${currentManual.id}/decals`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ page: pendingPos.page, decalNumber: num, x: pendingPos.x, y: pendingPos.y, color, shape }),
  });
  if (res.ok) {
    allDecals.push(await res.json());
    cancelDecalModal();
    renderOverlay();
  }
}

function cancelDecalModal() {
  pendingPos = null;
  const modal = document.getElementById('decal-modal');
  modal.classList.add('hidden');
  modal.style.left = '';
  modal.style.top  = '';
}

/* ──────────── 데칼 수정 모달 ──────────── */

// 클릭 위치 근처에 수정 팝업 표시 (뷰포트 경계 안으로 클램핑)
function openEditModal(clientX, clientY) {
  const modal = document.getElementById('edit-modal');
  modal.classList.remove('hidden');
  const W = 230, H = 170;
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
  const color = document.querySelector('input[name="edit-color"]:checked')?.value ?? 'WHITE';
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

// 수정 모달 열기: manualList 캐시에서 현재 값을 읽어 폼에 채움
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

// 수정 폼 제출: PUT /api/admin/manuals/{id} 후 목록 갱신
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
    // manualList 캐시 갱신
    const cached = manualList.find(x => x.id === editingManualId);
    if (cached) { cached.grade = grade; cached.modelNumber = modelNumber; cached.productName = productName; cached.link = link; }
    // 현재 선택된 메뉴얼이면 상태도 갱신
    if (currentManual?.id === editingManualId) {
      currentManual = { ...currentManual, grade, modelNumber, productName, link };
    }
    // 목록 아이템 DOM 직접 갱신
    const item = document.querySelector(`.manual-item[data-id="${editingManualId}"]`);
    if (item) {
      const gradeEl = item.querySelector('.grade-badge');
      if (gradeEl) { gradeEl.className = `grade-badge grade-${grade}`; gradeEl.textContent = grade; }
      const modelEl = item.querySelector('.text-gray-200.font-medium');
      if (modelEl) modelEl.textContent = modelNumber;
      const nameEl = item.querySelector('.text-xs.text-gray-400');
      if (nameEl) nameEl.textContent = productName;
    }
    // 접힌 사이드바 아이콘 툴팁 갱신
    const icon = document.querySelector(`.manual-icon-item[data-id="${editingManualId}"]`);
    if (icon) icon.dataset.tip = `[${grade}] ${modelNumber} ${productName}`;
    closeManualEditModal();
  } else {
    alert('수정에 실패했습니다.');
  }
});

/* ──────────── 메뉴얼 삭제 ──────────── */

// 확인 다이얼로그 후 메뉴얼 삭제. 현재 열린 메뉴얼이면 PDF 뷰어 초기화
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

let selectedFile = null; // 업로드 대기 중인 PDF 파일

// 드롭존 초기화: 이전 이벤트 리스너 제거를 위해 cloneNode로 DOM 재생성
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

// 파일 크기를 사람이 읽기 좋은 단위로 포맷
function fmtSize(b) {
  if (b < 1024) return b + ' B';
  if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
  return (b / 1048576).toFixed(1) + ' MB';
}

// 업로드 진행 중 폼 입력 비활성화 처리
function setFormLoading(loading) {
  ['inp-grade', 'inp-model', 'inp-name', 'inp-link', 'btn-upload-cancel', 'file-input'].forEach(id => {
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
  setFormLoading(false);
  setTimeout(() => document.getElementById('inp-grade').focus(), 50);
}

function closeUploadModal() {
  document.getElementById('upload-modal').classList.add('hidden');
  selectedFile = null;
  setFormLoading(false);
}

document.getElementById('btn-upload').addEventListener('click', openUploadModal);
document.getElementById('btn-upload-icon').addEventListener('click', openUploadModal);
document.getElementById('btn-upload-cancel').addEventListener('click', closeUploadModal);

// 메뉴얼 등록: multipart/form-data로 PDF + 메타데이터 업로드
document.getElementById('upload-form').addEventListener('submit', async e => {
  e.preventDefault();
  if (!selectedFile) { alert('PDF 파일을 선택해주세요.'); return; }
  const grade       = document.getElementById('inp-grade').value;
  const modelNumber = document.getElementById('inp-model').value.trim();
  const productName = document.getElementById('inp-name').value.trim();
  const link        = document.getElementById('inp-link').value.trim();
  if (!grade)       { alert('등급을 선택해주세요.'); return; }
  if (!productName) { alert('제품명을 입력해주세요.'); return; }
  if (link && !link.startsWith('https://')) { alert('링크는 https://로 시작해야 합니다.'); return; }

  setFormLoading(true);
  try {
    const fd = new FormData();
    fd.append('grade', grade);
    fd.append('modelNumber', modelNumber);
    fd.append('productName', productName);
    if (link) fd.append('link', link);
    fd.append('pdf', selectedFile);
    const res = await fetch('/api/admin/manuals', { method: 'POST', body: fd });
    if (res.ok) {
      const created = await res.json();
      closeUploadModal();
      await loadManuals();
      await selectManual(created.id);
    } else {
      alert('등록에 실패했습니다.');
      setFormLoading(false);
    }
  } catch {
    alert('등록 중 오류가 발생했습니다.');
    setFormLoading(false);
  }
});


/* ──────────── 새로고침 ──────────── */
document.getElementById('sb-refresh').addEventListener('click', loadManuals);

// 사이드바 접을 때: common.js가 sb-refresh를 숨기므로 다시 보이게 하고 로그아웃은 숨김
document.getElementById('sb-toggle').addEventListener('click', () => {
  document.getElementById('sb-refresh').style.display = '';
  document.getElementById('sb-logout').style.display = sbOpen ? '' : 'none';
});

/* ──────────── 형식번호 유효성 검사 ──────────── */

// 영문자·숫자·하이픈·슬래시만 허용하도록 실시간 정제
function sanitizeModelNumber(val) {
  return val.replace(/[^A-Za-z0-9\-/]/g, '');
}

// 형식번호 입력 필드에 유효성 검사 연결
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
// 데칼 번호 입력 필드에 유효성 검사 적용
applyDecalNumValidation(document.getElementById('inp-decal-num'));
applyDecalNumValidation(document.getElementById('inp-edit-num'));
// 형식번호 입력 필드에 유효성 검사 적용
applyModelNumValidation(document.getElementById('inp-model'));
applyModelNumValidation(document.getElementById('edit-inp-model'));

PrettyScroll('#manual-list', { barWidth: 6, barColor: 'rgba(156,163,175,0.5)', right: 2, autoHide: true });

loadManuals();
