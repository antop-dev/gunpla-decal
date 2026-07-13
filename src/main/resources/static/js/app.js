/* ── 상태 (사용자 페이지 전용) ── */
let currentManual      = null;  // 현재 선택된 메뉴얼 객체
let allDecals          = [];    // 현재 메뉴얼의 전체 데칼 목록
let allManuals         = [];    // 서버에서 로드한 메뉴얼 목록 (검색 결과 포함)
let manualLoading      = false; // PDF 로드 중 중복 선택 방지 플래그
let markersVisible     = true;  // 마커 보이기/숨기기 상태
let highlightedDecalId = null;  // 현재 하이라이트된 데칼 ID (줌 후 재적용용)
minZoomMult = 0.5;              // 사용자 페이지: fitScale의 50%까지 축소 허용

// 메뉴얼이 선택될 때 브라우저 title과 OG 메타 태그를 갱신한다 (SEO 무관, 브라우저/SNS 공유 용도).
function updatePageMeta({ grade, modelNumber, productName, id }) {
  const title = `${productName} | ${window.i18n.siteTitle}`;
  const desc  = `${grade} ${modelNumber} ${productName} ${window.i18n.siteDescSuffix}`;
  const url   = `${location.origin}${window.contextPath}/${id}`;
  document.title = title;
  document.querySelector('meta[name="description"]')?.setAttribute('content', desc);
  document.querySelector('meta[property="og:title"]')?.setAttribute('content', title);
  document.querySelector('meta[property="og:description"]')?.setAttribute('content', desc);
  document.querySelector('meta[property="og:url"]')?.setAttribute('content', url);
  const ldEl = document.getElementById('json-ld');
  if (ldEl) {
    ldEl.textContent = JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'Article',
      name: title,
      description: desc,
      url,
    });
  }
}

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
  const url = q ? `/api/user/manuals?q=${encodeURIComponent(q)}` : '/api/user/manuals';
  allManuals = await (await fetch(url)).json();

  // 텍스트 목록 렌더링
  const el = document.getElementById('manual-list');
  if (!allManuals.length) {
    el.innerHTML = q
      ? `<p class="text-gray-500 text-xs p-2">${window.i18n.manualSearchEmpty}</p>`
      : `<p class="text-gray-500 text-xs p-2">${window.i18n.manualEmpty}</p>`;
  } else {
    el.innerHTML = allManuals.map(m => `
      <div class="gtm-manual-select manual-item group px-2 py-1.5 rounded cursor-pointer hover:bg-gray-700 transition-colors"
           data-id="${m.id}"
           data-gtm-id="${m.id}" data-gtm-grade="${esc(m.grade)}" data-gtm-model="${esc(m.modelNumber)}" data-gtm-name="${esc(m.productName)}">
        <div class="flex items-center gap-1 mb-0.5">
          <span class="grade-badge grade-${esc(m.grade)}">${esc(m.grade)}</span>
          <span class="text-xs font-medium text-gray-200 leading-snug truncate flex-1">${esc(m.modelNumber)}</span>
          ${m.link ? `<a class="gtm-manual-ext-link manual-link-btn opacity-0 group-hover:opacity-100 flex-shrink-0 text-gray-500 hover:text-white w-5 h-5 flex items-center justify-center"
                  href="${esc(m.link)}" target="_blank" rel="noopener noreferrer" title="${window.i18n.manualLinkOpen}"
                  data-gtm-id="${m.id}" data-gtm-url="${esc(m.link)}">
            <i class="fas fa-link text-xs"></i>
          </a>` : ''}
          <button class="gtm-pdf-download pdf-dl-btn opacity-0 group-hover:opacity-100 flex-shrink-0 text-gray-500 hover:text-white w-5 h-5 flex items-center justify-center"
                  data-id="${m.id}"
                  data-filename="${esc(m.grade)}_${esc(m.modelNumber)}_${esc(m.productName)}.pdf"
                  data-gtm-id="${m.id}" data-gtm-grade="${esc(m.grade)}" data-gtm-model="${esc(m.modelNumber)}"
                  title="${window.i18n.manualPdfDownload}">
            <i class="fas fa-download text-xs"></i>
          </button>
        </div>
        <div class="manual-product-name text-xs text-gray-400 leading-snug truncate">${esc(m.productName)}</div>
      </div>`).join('');
    el.querySelectorAll('.manual-item').forEach(item =>
      item.addEventListener('click', e => {
        if (e.target.closest('.manual-link-btn') || e.target.closest('.pdf-dl-btn')) return;
        selectManual(item.dataset.id);
      }));

    const tip = document.getElementById('manual-item-tip');
    el.querySelectorAll('.manual-product-name').forEach(nameEl => {
      nameEl.addEventListener('mouseenter', () => {
        if (nameEl.scrollWidth <= nameEl.clientWidth) return;
        const r = nameEl.getBoundingClientRect();
        tip.textContent = nameEl.textContent;
        tip.style.left = (r.left + 4) + 'px';
        tip.style.top = (r.bottom + 4) + 'px';
        tip.style.transform = '';
        tip.style.display = 'block';
      });
      nameEl.addEventListener('mouseleave', () => { tip.style.display = 'none'; });
    });

    el.querySelectorAll('.pdf-dl-btn').forEach(btn =>
      btn.addEventListener('click', async e => {
        e.stopPropagation();
        const res = await fetch(`/resource/${btn.dataset.id}`);
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = btn.dataset.filename;
        a.click();
        URL.revokeObjectURL(url);
      }));
    // 검색/새로고침 후에도 이전 선택 상태 유지
    if (currentManual) {
      el.querySelector(`.manual-item[data-id="${currentManual.id}"]`)?.classList.add('bg-gray-600');
    }
  }

  // 접힌 사이드바용 아이콘 목록 렌더링
  const iconEl = document.getElementById('sb-icons');
  iconEl.innerHTML = allManuals.map(m => `
    <button class="gtm-manual-select manual-icon-item sb-icon-tip w-8 h-8 flex items-center justify-center rounded hover:bg-gray-700 text-gray-400 hover:text-white"
            data-id="${m.id}"
            data-tip="[${esc(m.grade)}] ${esc(m.modelNumber)} ${esc(m.productName)}"
            data-gtm-id="${m.id}" data-gtm-grade="${esc(m.grade)}" data-gtm-model="${esc(m.modelNumber)}" data-gtm-source="icon">
      <i class="fas fa-file-pdf text-sm"></i>
    </button>`).join('');
  iconEl.querySelectorAll('.manual-icon-item').forEach(icon =>
    icon.addEventListener('click', () => selectManual(icon.dataset.id)));

  // 아이콘 툴팁: sidebar overflow:hidden 회피를 위해 position:fixed 기반 JS 툴팁 사용
  const iconTip = document.getElementById('manual-item-tip');
  iconEl.querySelectorAll('.manual-icon-item').forEach(icon => {
    icon.addEventListener('mouseenter', () => {
      const r = icon.getBoundingClientRect();
      iconTip.textContent = icon.dataset.tip;
      iconTip.style.left = (r.right + 8) + 'px';
      iconTip.style.top = (r.top + r.height / 2) + 'px';
      iconTip.style.transform = 'translateY(-50%)';
      iconTip.style.display = 'block';
    });
    icon.addEventListener('mouseleave', () => { iconTip.style.display = 'none'; });
  });

  // 아이콘 목록에도 현재 선택 상태 반영
  if (currentManual) {
    const activeIcon = iconEl.querySelector(`.manual-icon-item[data-id="${currentManual.id}"]`);
    if (activeIcon) {
      activeIcon.classList.add('bg-gray-600');
      activeIcon.querySelector('i').className = 'fas fa-file-pdf text-sm text-white';
    }
  }
  window.dispatchEvent(new Event('resize'));
}

// 메뉴얼 선택: 목록 하이라이트 업데이트 후 PDF·데칼 로드.
// push=false이면 history.pushState를 호출하지 않음 (popstate/초기 진입 시).
async function selectManual(b62id, push = true) {
  if (manualLoading) return;
  manualLoading = true;
  try {
    document.querySelectorAll('.manual-item').forEach(e => e.classList.remove('bg-gray-600'));
    document.querySelector(`.manual-item[data-id="${b62id}"]`)?.classList.add('bg-gray-600');

    document.querySelectorAll('.manual-icon-item').forEach(e => {
      e.classList.remove('bg-gray-600');
      e.querySelector('i').className = 'fas fa-file-pdf text-sm';
    });
    const activeIcon = document.querySelector(`.manual-icon-item[data-id="${b62id}"]`);
    if (activeIcon) {
      activeIcon.classList.add('bg-gray-600');
      activeIcon.querySelector('i').className = 'fas fa-file-pdf text-sm text-white';
    }

    if (push) history.pushState({ b62id }, '', `${window.contextPath}/${b62id}`);

    // PDF 스켈레톤 표시 (pdfScroll은 visible — fitToContainer 치수 계산에 필요)
    noPdf.style.display = 'none';
    pdfScroll.style.display = '';
    document.getElementById('zoom-overlay').style.display = 'none';
    document.getElementById('pdf-loading').style.display = 'flex';
    thumbStrip.innerHTML = `<div class="strip-inner"><span class="text-gray-500 text-xs select-none">${window.i18n.pdfLoading}</span></div>`;

    // 데칼 사이드바 스켈레톤 표시
    document.getElementById('right-sidebar').style.display = 'flex';
    document.getElementById('decal-list').innerHTML =
      `<div class="grid gap-1" style="grid-template-columns:repeat(5,minmax(0,1fr));">${
        Array(20).fill('<div class="decal-skel" style="height:32px;"></div>').join('')
      }</div>`;
    document.getElementById('rs-icons').innerHTML =
      Array(10).fill('<div class="decal-skel flex-shrink-0 mx-auto" style="width:32px;height:32px;"></div>').join('');
    window.dispatchEvent(new Event('resize'));

    const res = await fetch(`/api/user/${b62id}`);
    if (!res.ok) {
      document.getElementById('pdf-loading').style.display = '';
      pdfScroll.style.display = 'none';
      document.getElementById('zoom-overlay').style.display = 'none';
      document.getElementById('right-sidebar').style.display = 'none';
      thumbStrip.innerHTML = `<div class="strip-inner"><span class="text-gray-500 text-xs select-none">${window.i18n.manualSelect}</span></div>`;
      noPdf.innerHTML = `<div class="text-center">
        <i class="fas fa-file-pdf text-5xl mb-3 opacity-40"></i>
        <p class="text-sm">${window.i18n.manualNotFound}</p>
      </div>`;
      noPdf.style.display = 'flex';
      return;
    }
    const data = await res.json();
    currentManual = data;
    allDecals = data.decals;
    updatePageMeta(data);
    // 순환 인덱스 초기화 (이전 메뉴얼의 상태 잔류 방지)
    Object.keys(decalCycleIndex).forEach(k => delete decalCycleIndex[k]);
    // 데칼 없으면 사이드바 숨김 (resize로 pdfScroll 폭 재계산)
    if (!allDecals.length) {
      document.getElementById('right-sidebar').style.display = 'none';
      window.dispatchEvent(new Event('resize'));
    }

    pdfDoc = await pdfjsLib.getDocument(`${window.contextPath}/resource/${currentManual.id}`).promise;

    // 데칼이 가장 많은 페이지로 이동
    if (allDecals.length) {
      const cnt = {};
      allDecals.forEach(d => { cnt[d.page] = (cnt[d.page] || 0) + 1; });
      currentPage = +Object.keys(cnt).reduce((a, b) => cnt[+a] >= cnt[+b] ? a : b);
    } else {
      currentPage = 1;
    }

    await renderPage(currentPage, true);

    // 초기 줌을 fitScale (컨테이너 한 방향에 꽉 차는 배율)로 재설정
    scale = fitScale;
    applyTransform();
    pdfScroll.scrollLeft = Math.max(0, (basePdfWidth  * scale - pdfScroll.clientWidth)  / 2);
    pdfScroll.scrollTop  = Math.max(0, (basePdfHeight * scale - pdfScroll.clientHeight) / 2);

    // 스켈레톤 숨기고 PDF 공개
    document.getElementById('pdf-loading').style.display = '';
    document.getElementById('zoom-overlay').style.display = 'flex';

    renderThumbnails(data.thumbnails);
    renderDecalList();
  } finally {
    manualLoading = false;
  }
}

/* ──────────── 데칼 오버레이 ──────────── */

// 현재 페이지의 데칼 마커를 오버레이에 렌더링 (common.js의 renderPage에서 호출)
function renderOverlay() {
  overlay.innerHTML = allDecals.filter(d => d.page === currentPage).map(d => buildDecalMarkerHtml(d, 3)).join('');
  overlay.querySelectorAll('.decal-marker').forEach(m =>
    m.addEventListener('click', e => {
      e.stopPropagation();
      const decal = allDecals.find(d => d.id === +m.dataset.id);
      if (decal) navigateToDecalByKey(`${decal.decalNumber}|${decal.color ?? '#ffffff'}|${decal.shape ?? 'CIRCLE'}`);
    }));
  if (highlightedDecalId) {
    overlay.querySelector(`.decal-marker[data-id="${highlightedDecalId}"]`)?.classList.add('highlight');
  }
  overlay.style.display = markersVisible ? '' : 'none';
}

/* ──────────── 데칼 정렬 ──────────── */

// 번호 정렬: 숫자 시작 → 영어 시작 → 일본어/기타 순 (같은 타입 내에선 locale 정렬)
function sortDecalNumber(a, b) {
  const rank = s => /^\d/.test(s) ? 0 : /^[A-Za-z]/.test(s) ? 1 : 2;
  const ra = rank(a), rb = rank(b);
  if (ra !== rb) return ra - rb;
  return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });
}

// 데칼 쌍 정렬: 동그라미 → 네모 → 다이아, 같은 도형 내에서는 색상 오름차순, 같은 색상 내에서는 번호 오름차순
const SHAPE_ORDER = { CIRCLE: 0, SQUARE: 1, DIAMOND: 2 };
function sortDecalPairs(a, b) {
  const so = (SHAPE_ORDER[a.shape] ?? 99) - (SHAPE_ORDER[b.shape] ?? 99);
  if (so !== 0) return so;
  if (a.color !== b.color) return a.color < b.color ? -1 : 1;
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
    const key = `${d.decalNumber}|${d.color ?? '#ffffff'}|${d.shape ?? 'CIRCLE'}`;
    if (!seen.has(key)) { seen.add(key); pairs.push({ key, num: d.decalNumber, color: d.color ?? '#ffffff', shape: d.shape ?? 'CIRCLE' }); }
  });
  pairs.sort(sortDecalPairs);
  const filtered = kw ? pairs.filter(p => p.num.toLowerCase().includes(kw)) : pairs;

  if (!allDecals.length) {
    list.innerHTML = `<span class="text-xs text-gray-400 p-1 block">${window.i18n.decalEmpty}</span>`;
    iconEl.innerHTML = '';
    return;
  }
  if (!filtered.length) {
    list.innerHTML = `<span class="text-xs text-gray-400 p-1 block">${window.i18n.decalSearchEmpty}</span>`;
    iconEl.innerHTML = '';
    return;
  }

  // 5열 그리드, 같은 번호가 여러 위치에 있으면 우하단에 개수 표시
  list.innerHTML = `<div class="grid gap-1" style="grid-template-columns:repeat(5,minmax(0,1fr));">
    ${filtered.map(({ key, num, color, shape }) => {
      const cnt = allDecals.filter(d => d.decalNumber === num && (d.color ?? '#ffffff') === color && (d.shape ?? 'CIRCLE') === shape).length;
      const isHex = color.startsWith('#');
      const lum = isHex ? hexLuminance(color) : 0;
      const shapeIcon = shape === 'CIRCLE' ? '●' : shape === 'DIAMOND' ? '◆' : '■';
      const iconColor = isHex ? color : '#555';
      const iconStroke = (isHex && lum > 0.85) ? '-webkit-text-stroke:0.5px #aaa;' : '';
      return `
      <div class="relative">
        <button class="gtm-decal-click decal-btn w-full h-8 flex items-center justify-center text-xs font-medium
                       bg-white text-gray-900 border border-gray-300 hover:bg-gray-100 truncate px-0.5"
                data-key="${esc(key)}"
                data-gtm-num="${esc(num)}" data-gtm-color="${esc(color)}" data-gtm-shape="${esc(shape)}" data-gtm-source="grid">
          ${esc(num)}
        </button>
        <span class="pointer-events-none" style="position:absolute;top:1px;left:2px;font-size:9px;line-height:1;color:${iconColor};${iconStroke};z-index:1;">${shapeIcon}</span>
        ${cnt > 1 ? `<span class="pointer-events-none" style="position:absolute;bottom:1px;right:2px;font-size:8px;font-weight:700;line-height:1;color:#555;">${cnt}</span>` : ''}
      </div>`;
    }).join('')}
  </div>`;

  // 접힌 사이드바용 아이콘 버튼 목록
  iconEl.innerHTML = pairs.map(({ key, num, color, shape }) => {
    const isHex = color.startsWith('#');
    const lum = isHex ? hexLuminance(color) : 0;
    const shapeIcon = shape === 'CIRCLE' ? '●' : '■';
    const iconColor = isHex ? color : '#555';
    const iconStroke = (isHex && lum > 0.85) ? '-webkit-text-stroke:0.5px #aaa;' : '';
    return `
    <div class="relative flex-shrink-0">
      <button class="gtm-decal-click decal-icon-btn rs-icon-tip w-8 h-8 flex items-center justify-center
                     bg-white text-gray-900 border border-gray-300 hover:bg-gray-100 text-xs font-bold"
              data-key="${esc(key)}" data-tip="${esc(num)}"
              data-gtm-num="${esc(num)}" data-gtm-color="${esc(color)}" data-gtm-shape="${esc(shape)}" data-gtm-source="icon">
        ${esc(num.slice(0, 3))}
      </button>
      <span class="pointer-events-none" style="position:absolute;top:1px;left:2px;font-size:9px;line-height:1;color:${iconColor};${iconStroke};z-index:1;">${shapeIcon}</span>
    </div>`;
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
    allDecals.filter(d => d.decalNumber === decalNumber && (d.color ?? '#ffffff') === color && (d.shape ?? 'CIRCLE') === shape)
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
    highlightedDecalId = decal.id;
    // 마커가 뷰포트 중앙에 오도록 스크롤
    const targetX = basePdfWidth  * (decal.x / 100) * scale;
    const targetY = basePdfHeight * (decal.y / 100) * scale;
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
  setTimeout(() => window.dispatchEvent(new Event('resize')), 220);
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

document.getElementById('marker-visible').addEventListener('change', e => {
  markersVisible = e.target.checked;
  overlay.style.display = markersVisible ? '' : 'none';
});

const shortcutPopup = document.getElementById('shortcut-popup');
document.getElementById('shortcut-btn').addEventListener('click', e => {
  e.stopPropagation();
  shortcutPopup.style.display = shortcutPopup.style.display === 'flex' ? 'none' : 'flex';
});
document.addEventListener('click', () => { shortcutPopup.style.display = 'none'; });

/* ──────────── 전체화면 ──────────── */
document.getElementById('fullscreen-btn').addEventListener('click', () => {
  if (document.fullscreenElement) document.exitFullscreen();
  else container.requestFullscreen();
});

document.addEventListener('fullscreenchange', () => {
  const isFs = !!document.fullscreenElement;
  document.getElementById('fullscreen-icon').className = isFs ? 'fas fa-compress' : 'fas fa-expand';
  document.getElementById('fullscreen-label').textContent = isFs ? window.i18n.fullscreenExit : window.i18n.fullscreenEnter;
  document.getElementById('fullscreen-btn').title = isFs ? window.i18n.fullscreenExitTitle : window.i18n.fullscreenEnterTitle;
  if (pdfDoc) setTimeout(() => renderPage(currentPage), 50);
});

document.addEventListener('keydown', e => {
  if (e.target.matches('input, textarea, select')) return;
  if (e.key === 'f' || e.key === 'F') document.getElementById('fullscreen-btn').click();
});

/* ──────────── 초기화 ── */
const DARK_SCROLL  = { barWidth: 10, defaultWrapperWidth: 11, barColor: 'rgba(156,163,175,0.5)', right: 1, autoHide: true };
const LIGHT_SCROLL = { barWidth: 6, barColor: 'rgba(107,114,128,0.5)', right: 2, autoHide: true };
PrettyScroll('#manual-list', DARK_SCROLL);
PrettyScroll('#decal-list',  LIGHT_SCROLL);

(async () => {
  const initB62 = location.pathname.slice(window.contextPath.length + 1);
  await loadManuals();
  if (initB62) {
    selectManual(initB62, false);
  } else if (allManuals.length) {
    selectManual(allManuals[0].id, false);
  }
})();

window.addEventListener('popstate', e => {
  const b62id = e.state?.b62id ?? location.pathname.slice(window.contextPath.length + 1);
  if (b62id) selectManual(b62id, false);
});
