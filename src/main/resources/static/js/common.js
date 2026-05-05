/* ── PDF.js 워커 초기화 ── */
// CDN에서 로드한 pdf.min.js 전역 객체 참조
const pdfjsLib = window['pdfjs-dist/build/pdf'];
pdfjsLib.GlobalWorkerOptions.workerSrc = '/vendor/pdfjs/pdf.worker.min.js';

/* ── 공유 상태 ── */
let pdfDoc      = null; // 현재 로드된 PDF 문서 객체 (PDFDocumentProxy)
let currentPage = 1;    // 현재 표시 중인 페이지 번호 (1-based)
let scale    = 1;       // 현재 확대/축소 배율
let fitScale = 1;       // 컨테이너에 꽉 차는 기본 배율 (줌 최솟값으로도 사용)

/* ── 공유 DOM 요소 ── */
const container  = document.getElementById('pdf-container');
const pdfScroll  = document.getElementById('pdf-scroll');
const canvas     = document.getElementById('pdf-canvas');
const overlay    = document.getElementById('decal-overlay');
const noPdf      = document.getElementById('no-pdf');
const thumbStrip = document.getElementById('thumb-strip');

/* ──────────── 줌 ──────────── */

// canvas 표시 크기에 현재 배율 반영, 줌 레이블·슬라이더 동기화
function applyTransform() {
  const w = canvas.width  * scale;
  const h = canvas.height * scale;
  canvas.style.width  = w + 'px';
  canvas.style.height = h + 'px';
  const pct = Math.round(scale / fitScale * 100);
  const labelEl = document.getElementById('zoom-label');
  if (labelEl) labelEl.textContent = pct + '%';
  const sliderEl = document.getElementById('zoom-slider');
  if (sliderEl) sliderEl.value = pct;
}

// 마우스 포인터 위치(cx, cy)를 중심으로 factor 배율만큼 확대/축소
function zoomAt(cx, cy, factor) {
  const rect = pdfScroll.getBoundingClientRect();
  const mx = cx - rect.left, my = cy - rect.top;
  const canvasX = (pdfScroll.scrollLeft + mx) / scale;
  const canvasY = (pdfScroll.scrollTop  + my) / scale;
  const ns = Math.max(fitScale, Math.min(fitScale * 4, scale * factor));
  scale = ns;
  applyTransform();
  pdfScroll.scrollLeft = canvasX * ns - mx;
  pdfScroll.scrollTop  = canvasY * ns - my;
}

// 컨테이너에 페이지가 꽉 차도록 배율 초기화, 초기 줌은 fitScale × 1.5
function fitToContainer() {
  fitScale = Math.min(pdfScroll.clientWidth  / canvas.width,
                      pdfScroll.clientHeight / canvas.height);
  scale = fitScale * 1.5;
  applyTransform();
  pdfScroll.scrollLeft = (canvas.width  * scale - pdfScroll.clientWidth)  / 2;
  pdfScroll.scrollTop  = (canvas.height * scale - pdfScroll.clientHeight) / 2;
}

// 슬라이더 입력값(100~400)을 백분율로 해석해 배율 적용
function applyZoomPreset(pct) {
  scale = Math.max(fitScale, Math.min(fitScale * 4, fitScale * (pct / 100)));
  applyTransform();
  pdfScroll.scrollLeft = (canvas.width  * scale - pdfScroll.clientWidth)  / 2;
  pdfScroll.scrollTop  = (canvas.height * scale - pdfScroll.clientHeight) / 2;
}

// ctrlKey → 핀치줌, 큰 deltaY(>40 & 수평 없음) → 마우스 휠 줌, 나머지 → 네이티브 스크롤
pdfScroll.addEventListener('wheel', e => {
  if (e.ctrlKey) {
    e.preventDefault();
    zoomAt(e.clientX, e.clientY, 1 - e.deltaY * 0.008);
  } else if (e.deltaMode !== 0 || (Math.abs(e.deltaX) < 5 && Math.abs(e.deltaY) > 40)) {
    e.preventDefault();
    zoomAt(e.clientX, e.clientY, e.deltaY < 0 ? 1.12 : 0.88);
  }
}, { passive: false });

/* ──────────── 페이지 렌더링 ──────────── */

// PDF 페이지를 canvas에 렌더링.
// resetZoom=true 이면 컨테이너 맞춤 배율로 초기화, false 이면 현재 배율 유지.
// renderOverlay()는 각 페이지(app.js / admin.js)에서 별도 구현.
async function renderPage(num, resetZoom = false) {
  const page = await pdfDoc.getPage(num);
  const vp = page.getViewport({ scale: 1.5 });
  canvas.width = vp.width; canvas.height = vp.height;
  await page.render({ canvasContext: canvas.getContext('2d'), viewport: vp }).promise;
  if (resetZoom) {
    fitToContainer();
  } else {
    fitScale = Math.min(pdfScroll.clientWidth / canvas.width, pdfScroll.clientHeight / canvas.height);
    scale = Math.max(fitScale, Math.min(fitScale * 4, scale));
    applyTransform();
    pdfScroll.scrollLeft = (canvas.width  * scale - pdfScroll.clientWidth)  / 2;
    pdfScroll.scrollTop  = (canvas.height * scale - pdfScroll.clientHeight) / 2;
  }
  renderOverlay();
  updateActiveThumbnail();
}

/* ──────────── 썸네일 스트립 ──────────── */

// PDF 전체 페이지의 썸네일을 상단 스트립에 순서대로 렌더링
async function renderThumbnails() {
  const inner = document.createElement('div');
  inner.className = 'strip-inner';
  thumbStrip.innerHTML = '';
  thumbStrip.appendChild(inner);
  const TH = 68; // 썸네일 높이 px
  for (let i = 1; i <= pdfDoc.numPages; i++) {
    const page = await pdfDoc.getPage(i);
    const raw = page.getViewport({ scale: 1 });
    const tvp = page.getViewport({ scale: TH / raw.height });
    const wrap = document.createElement('div');
    wrap.className = 'thumb-item' + (i === currentPage ? ' active' : '');
    wrap.dataset.page = i;
    const tc = document.createElement('canvas');
    tc.width = tvp.width; tc.height = tvp.height;
    await page.render({ canvasContext: tc.getContext('2d'), viewport: tvp }).promise;
    const lbl = document.createElement('div');
    lbl.className = 'thumb-label'; lbl.textContent = i;
    wrap.append(tc, lbl);
    inner.appendChild(wrap);
    wrap.addEventListener('click', async () => { currentPage = i; await renderPage(i); });
  }
}

// 현재 페이지 썸네일에 active 클래스 적용 및 스트립 내 스크롤 포커스
function updateActiveThumbnail() {
  thumbStrip.querySelectorAll('.thumb-item').forEach(t =>
    t.classList.toggle('active', +t.dataset.page === currentPage));
  thumbStrip.querySelector('.thumb-item.active')
    ?.scrollIntoView({ behavior: 'smooth', inline: 'nearest', block: 'nearest' });
}

/* ──────────── 데칼 마커 스타일 ──────────── */

// 데칼 배경색(WHITE/BLACK)과 도형(CIRCLE/SQUARE)에 따른 마커 인라인 스타일 반환
function decalMarkerStyle(color, shape) {
  const colorStyle = color === 'BLACK'
    ? 'background:rgba(17,17,17,0.92);color:#fff;border:2px solid rgba(160,160,160,0.8);'
    : 'background:rgba(255,255,255,0.92);color:#111;border:2px solid rgba(80,80,80,0.7);';
  const shapeStyle = shape !== 'SQUARE' ? 'border-radius:50%;' : '';
  return colorStyle + shapeStyle;
}

/* ──────────── 왼쪽 사이드바 토글 ──────────── */

let sbOpen = true; // 왼쪽 사이드바 펼침(true) / 접힘(false) 상태

// 사이드바를 접거나 펼침. 접힌 상태에서는 아이콘 목록만 표시
function toggleSidebar() {
  sbOpen = !sbOpen;
  const h = document.getElementById('sb-header');
  document.getElementById('sidebar').style.width = sbOpen ? '220px' : '44px';
  document.getElementById('sb-content').style.display = sbOpen ? '' : 'none';
  document.getElementById('sb-icons').style.display   = sbOpen ? 'none' : 'flex';
  document.getElementById('sb-title').style.display   = sbOpen ? '' : 'none';
  document.getElementById('sb-refresh').style.display = sbOpen ? '' : 'none';
  document.getElementById('sb-toggle-icon').className =
    sbOpen ? 'fas fa-angles-left text-sm' : 'fas fa-angles-right text-sm';
  h.style.justifyContent = sbOpen ? '' : 'center';
  h.style.paddingLeft    = sbOpen ? '' : '0';
  h.style.paddingRight   = sbOpen ? '' : '0';
  h.style.gap            = sbOpen ? '' : '0';
}
document.getElementById('sb-toggle').addEventListener('click', toggleSidebar);

/* ──────────── 줌 슬라이더 ──────────── */
document.getElementById('zoom-slider')?.addEventListener('input', e => {
  if (pdfDoc) applyZoomPreset(+e.target.value);
});

/* ──────────── 유틸 ──────────── */

// HTML 특수문자 이스케이프 (innerHTML 삽입 시 XSS 방지)
function esc(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
