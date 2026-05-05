/* ── 상태 (사용자 페이지 전용) ── */
let currentManual = null; // 현재 선택된 메뉴얼 객체
let allDecals     = [];   // 현재 메뉴얼의 전체 데칼 목록
let allManuals    = [];   // 서버에서 로드한 메뉴얼 목록 (검색 결과 포함)

// 드래그 상태 추적 (pdfScroll 패닝용)
let mouseDownOnContainer = false;
let wasDragging = false;
let lastMX = 0, lastMY = 0;

// 데칼 클릭 시 같은 번호+색상+도형 그룹 내 순환 인덱스: "decalNumber|color|shape" → 다음 인덱스
const decalCycleIndex = {};

/* ──────────── 드래그 패닝 ──────────── */
// mousedown: 왼쪽 버튼에서만 드래그 시작
container.addEventListener('mousedown', e => {
  if (e.button !== 0) return;
  mouseDownOnContainer = true;
  wasDragging = false;
  lastMX = e.clientX; lastMY = e.clientY;
});

// mousemove: 4px 이상 이동하면 드래그로 판정, pdfScroll 스크롤 조정
window.addEventListener('mousemove', e => {
  if (!mouseDownOnContainer) return;
  const dx = e.clientX - lastMX, dy = e.clientY - lastMY;
  if (!wasDragging && Math.abs(dx) + Math.abs(dy) > 4) {
    wasDragging = true;
    container.classList.add('dragging');
  }
  if (wasDragging) {
    pdfScroll.scrollLeft -= dx;
    pdfScroll.scrollTop  -= dy;
  }
  lastMX = e.clientX; lastMY = e.clientY;
});

window.addEventListener('mouseup', () => {
  mouseDownOnContainer = false;
  container.classList.remove('dragging');
});

/* ──────────── 메뉴얼 목록 ──────────── */

// 서버에서 메뉴얼 목록 로드. q가 있으면 서버 사이드 검색 수행
async function loadManuals(q = '') {
  const url = q ? `/api/manuals?q=${encodeURIComponent(q)}` : '/api/manuals';
  allManuals = await (await fetch(url)).json();

  // 텍스트 목록 렌더링
  const el = document.getElementById('manual-list');
  if (!allManuals.length) {
    el.innerHTML = q
      ? '<p class="text-gray-500 text-xs p-2">검색 결과 없음</p>'
      : '<p class="text-gray-500 text-xs p-2">등록된 메뉴얼이 없습니다</p>';
  } else {
    el.innerHTML = allManuals.map(m => `
      <div class="manual-item px-2 py-1.5 rounded cursor-pointer hover:bg-gray-700 transition-colors" data-id="${m.id}">
        <div class="flex items-center gap-1 mb-0.5">
          <span class="grade-badge grade-${esc(m.grade)}">${esc(m.grade)}</span>
          <span class="text-xs font-medium text-gray-200 leading-snug truncate">${esc(m.modelNumber)}</span>
        </div>
        <div class="text-xs text-gray-400 leading-snug truncate">${esc(m.productName)}</div>
      </div>`).join('');
    el.querySelectorAll('.manual-item').forEach(item =>
      item.addEventListener('click', () => selectManual(+item.dataset.id)));
    // 검색/새로고침 후에도 이전 선택 상태 유지
    if (currentManual) {
      el.querySelector(`.manual-item[data-id="${currentManual.id}"]`)?.classList.add('bg-gray-600');
    }
  }

  // 접힌 사이드바용 아이콘 목록 렌더링
  const iconEl = document.getElementById('sb-icons');
  iconEl.innerHTML = allManuals.map(m => `
    <button class="manual-icon-item sb-icon-tip w-8 h-8 flex items-center justify-center rounded hover:bg-gray-700 text-gray-400 hover:text-white"
            data-id="${m.id}"
            data-tip="[${esc(m.grade)}] ${esc(m.modelNumber)} ${esc(m.productName)}">
      <i class="fas fa-file-pdf text-sm"></i>
    </button>`).join('');
  iconEl.querySelectorAll('.manual-icon-item').forEach(icon =>
    icon.addEventListener('click', () => selectManual(+icon.dataset.id)));
  // 아이콘 목록에도 현재 선택 상태 반영
  if (currentManual) {
    const activeIcon = iconEl.querySelector(`.manual-icon-item[data-id="${currentManual.id}"]`);
    if (activeIcon) {
      activeIcon.classList.add('bg-gray-600');
      activeIcon.querySelector('i').className = 'fas fa-file-pdf text-sm text-white';
    }
  }
}

// 메뉴얼 선택: 목록 하이라이트 업데이트 후 PDF·데칼 로드
async function selectManual(id) {
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

  const data = await (await fetch(`/api/manuals/${id}`)).json();
  currentManual = data;
  allDecals = data.decals;
  // 순환 인덱스 초기화 (이전 메뉴얼의 상태 잔류 방지)
  Object.keys(decalCycleIndex).forEach(k => delete decalCycleIndex[k]);
  noPdf.style.display = 'none';
  pdfScroll.style.display = '';
  document.getElementById('zoom-overlay').style.display = 'flex';
  // 데칼이 있을 때만 오른쪽 사이드바 표시
  document.getElementById('right-sidebar').style.display = allDecals.length > 0 ? 'flex' : 'none';
  pdfDoc = await pdfjsLib.getDocument(`/api/manuals/${id}/pdf`).promise;
  currentPage = 1;
  await renderPage(currentPage, true);
  renderThumbnails();
  renderDecalList();
}

/* ──────────── 데칼 오버레이 ──────────── */

// 현재 페이지의 데칼 마커를 오버레이에 렌더링 (common.js의 renderPage에서 호출)
function renderOverlay() {
  overlay.innerHTML = allDecals.filter(d => d.page === currentPage).map(d => `
    <div class="decal-marker" data-id="${d.id}"
         style="left:${d.x}%;top:${d.y}%;transform:translate(-50%,-50%);${decalMarkerStyle(d.color, d.shape)}"
         title="${esc(d.decalNumber)}">
      ${esc(d.decalNumber.slice(0, 3))}
    </div>`).join('');
  overlay.querySelectorAll('.decal-marker').forEach(m =>
    m.addEventListener('click', e => {
      e.stopPropagation();
      const decal = allDecals.find(d => d.id === +m.dataset.id);
      if (decal) navigateToDecalByKey(`${decal.decalNumber}|${decal.color ?? 'WHITE'}|${decal.shape ?? 'CIRCLE'}`);
    }));
}

/* ──────────── 데칼 정렬 ──────────── */

// 번호 정렬: 숫자 시작 → 영어 시작 → 일본어/기타 순 (같은 타입 내에선 locale 정렬)
function sortDecalNumber(a, b) {
  const rank = s => /^\d/.test(s) ? 0 : /^[A-Za-z]/.test(s) ? 1 : 2;
  const ra = rank(a), rb = rank(b);
  if (ra !== rb) return ra - rb;
  return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });
}

// 데칼 쌍 정렬: 동그라미 → 네모, 같은 도형 내에서는 흰색 → 검은색, 같은 색상 내에서는 번호 오름차순
function sortDecalPairs(a, b) {
  if (a.shape !== b.shape) return a.shape === 'CIRCLE' ? -1 : 1;
  if (a.color !== b.color) return a.color === 'WHITE' ? -1 : 1;
  return sortDecalNumber(a.num, b.num);
}

// 위치 기반 정렬 (페이지 → y → x): 데칼 그룹 순환 탐색에 사용
function sortDecalsByPosition(decals) {
  return [...decals].sort((a, b) =>
    a.page !== b.page ? a.page - b.page :
    a.y    !== b.y    ? a.y    - b.y    :
    a.x    - b.x
  );
}

/* ──────────── 데칼 목록 ──────────── */

// 오른쪽 사이드바 데칼 그리드 렌더링.
// (decalNumber, color) 쌍을 unique 키로 중복 제거 후 sortDecalPairs 순으로 표시
function renderDecalList() {
  const kw     = (document.getElementById('decal-search')?.value ?? '').toLowerCase();
  const list   = document.getElementById('decal-list');
  const iconEl = document.getElementById('rs-icons');

  const seen = new Set();
  const pairs = [];
  allDecals.forEach(d => {
    const key = `${d.decalNumber}|${d.color ?? 'WHITE'}|${d.shape ?? 'CIRCLE'}`;
    if (!seen.has(key)) { seen.add(key); pairs.push({ key, num: d.decalNumber, color: d.color ?? 'WHITE', shape: d.shape ?? 'CIRCLE' }); }
  });
  pairs.sort(sortDecalPairs);
  const filtered = kw ? pairs.filter(p => p.num.toLowerCase().includes(kw)) : pairs;

  if (!allDecals.length) {
    list.innerHTML = '<span class="text-xs text-gray-400 p-1 block">데칼 없음</span>';
    iconEl.innerHTML = '';
    return;
  }
  if (!filtered.length) {
    list.innerHTML = '<span class="text-xs text-gray-400 p-1 block">검색 결과 없음</span>';
    iconEl.innerHTML = '';
    return;
  }

  // 5열 그리드, 같은 번호가 여러 위치에 있으면 우하단에 개수 표시
  // 개수 뱃지는 div wrapper에 배치 (버튼의 overflow:hidden + border-radius:50% 에 잘리지 않도록)
  list.innerHTML = `<div class="grid gap-1" style="grid-template-columns:repeat(5,minmax(0,1fr));">
    ${filtered.map(({ key, num, color, shape }) => {
      const cnt = allDecals.filter(d => d.decalNumber === num && (d.color ?? 'WHITE') === color && (d.shape ?? 'CIRCLE') === shape).length;
      const isBlack = color === 'BLACK';
      const cls = isBlack
        ? 'bg-gray-900 text-white border-gray-700 hover:bg-gray-700'
        : 'bg-white text-gray-900 border-gray-300 hover:bg-gray-100';
      const borderRadius = shape === 'SQUARE' ? '' : 'border-radius:50%;';
      return `
      <div class="relative">
        <button class="decal-btn w-full h-8 flex items-center justify-center text-xs font-medium
                       ${cls} border truncate px-0.5"
                style="${borderRadius}" data-key="${esc(key)}" title="${esc(num)}">
          ${esc(num)}
        </button>
        ${cnt > 1 ? `<span class="pointer-events-none" style="position:absolute;bottom:1px;right:2px;font-size:8px;font-weight:700;line-height:1;color:${isBlack ? '#fff' : '#111'};opacity:0.75;">${cnt}</span>` : ''}
      </div>`;
    }).join('')}
  </div>`;

  // 접힌 사이드바용 아이콘 버튼 목록
  iconEl.innerHTML = pairs.map(({ key, num, color, shape }) => {
    const isBlack = color === 'BLACK';
    const cls = isBlack
      ? 'bg-gray-900 text-white hover:bg-gray-700 border border-gray-700'
      : 'text-gray-900 hover:bg-gray-100 border border-gray-300';
    const borderRadius = shape === 'SQUARE' ? '' : 'border-radius:50%;';
    return `
    <button class="decal-icon-btn rs-icon-tip w-8 h-8 flex items-center justify-center
                   ${cls} text-xs font-bold flex-shrink-0"
            style="${borderRadius}" data-key="${esc(key)}" data-tip="${esc(num)}">
      ${esc(num.slice(0, 3))}
    </button>`;
  }).join('');

  list.querySelectorAll('.decal-btn').forEach(btn =>
    btn.addEventListener('click', () => navigateToDecalByKey(btn.dataset.key)));
  iconEl.querySelectorAll('.decal-icon-btn').forEach(btn =>
    btn.addEventListener('click', () => navigateToDecalByKey(btn.dataset.key)));
}

// 데칼 버튼 클릭 시 해당 번호+색상+도형 그룹을 위치 순으로 순환하며 이동·하이라이트
// key = "decalNumber|color|shape" 형태
async function navigateToDecalByKey(key) {
  const [decalNumber, color, shape] = key.split('|');
  const group = sortDecalsByPosition(
    allDecals.filter(d => d.decalNumber === decalNumber && (d.color ?? 'WHITE') === color && (d.shape ?? 'CIRCLE') === shape)
  );
  if (!group.length) return;

  const idx = (decalCycleIndex[key] ?? 0) % group.length;
  decalCycleIndex[key] = (idx + 1) % group.length;

  const decal = group[idx];
  if (currentPage !== decal.page) { currentPage = decal.page; await renderPage(currentPage); }
  const marker = overlay.querySelector(`.decal-marker[data-id="${decal.id}"]`);
  if (marker) {
    overlay.querySelectorAll('.decal-marker').forEach(m => m.classList.remove('highlight'));
    marker.classList.add('highlight');
    // 마커가 뷰포트 중앙에 오도록 스크롤
    const targetX = canvas.width  * (decal.x / 100) * scale;
    const targetY = canvas.height * (decal.y / 100) * scale;
    pdfScroll.scrollLeft = targetX - pdfScroll.clientWidth  / 2;
    pdfScroll.scrollTop  = targetY - pdfScroll.clientHeight / 2;
  }
}

/* ──────────── 오른쪽 사이드바 토글 ──────────── */
let rsOpen = true; // 오른쪽 사이드바 펼침(true) / 접힘(false) 상태

// 오른쪽 사이드바를 접거나 펼침. 접힌 상태에서는 아이콘 1열 표시
function toggleRightSidebar() {
  rsOpen = !rsOpen;
  const h = document.getElementById('rs-header');
  document.getElementById('right-sidebar').style.width = rsOpen ? '210px' : '44px';
  document.getElementById('rs-content').style.display  = rsOpen ? '' : 'none';
  document.getElementById('rs-icons').style.display    = rsOpen ? 'none' : 'flex';
  document.getElementById('rs-title').style.display    = rsOpen ? '' : 'none';
  document.getElementById('rs-toggle-icon').className  =
    rsOpen ? 'fas fa-angles-right text-xs' : 'fas fa-angles-left text-xs';
  h.style.justifyContent = rsOpen ? '' : 'center';
  h.style.paddingLeft    = rsOpen ? '' : '0';
  h.style.paddingRight   = rsOpen ? '' : '0';
  h.style.gap            = rsOpen ? '' : '0';
}
document.getElementById('rs-toggle').addEventListener('click', toggleRightSidebar);

/* ──────────── 검색 ──────────── */

// 새로고침 버튼: 검색어 초기화 후 전체 목록 재로드
document.getElementById('sb-refresh').addEventListener('click', () => {
  document.getElementById('manual-search').value = '';
  loadManuals();
});

// 메뉴얼 검색: 엔터 → 즉시, 입력 → 0.5초 디바운스
let manualSearchTimer = null;
document.getElementById('manual-search').addEventListener('keydown', e => {
  if (e.key === 'Enter') {
    clearTimeout(manualSearchTimer);
    loadManuals(e.target.value.trim());
  }
});
document.getElementById('manual-search').addEventListener('input', e => {
  clearTimeout(manualSearchTimer);
  manualSearchTimer = setTimeout(() => loadManuals(e.target.value.trim()), 500);
});

// 데칼 검색: 입력 즉시 클라이언트 측 필터링
document.getElementById('decal-search').addEventListener('input', renderDecalList);

/* ──────────── 초기화 ── */
loadManuals();
