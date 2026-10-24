// Tek zamanlayıcı + tek kaydırma kaynağı.
// Bütün kaydırmaya bağlı hareketler (DOM'daki --p değişkenleri ve 3B tabletin pozu) bu döngüden okunur;
// böylece aynı özelliğe iki ayrı animasyon yazmaz, ileri/geri ve hızlı kaydırmada sonuç hep aynıdır.

type FrameFn = (dt: number, time: number) => void;

const frameFns = new Set<FrameFn>();
const progressEls: { el: HTMLElement; mode: "sticky" | "pass"; last: number }[] = [];
let last = performance.now();
let running = false;
let reduced = false;

export const viewport = { w: innerWidth, h: innerHeight, y: scrollY };

export function setReducedProgress(v: boolean): void {
  reduced = v;
  for (const p of progressEls) {
    p.last = -1;
    if (v) p.el.style.setProperty("--p", "1");
  }
}

export function onFrame(fn: FrameFn): () => void {
  frameFns.add(fn);
  start();
  return () => frameFns.delete(fn);
}

export function registerProgress(root: ParentNode = document): void {
  root.querySelectorAll<HTMLElement>("[data-progress]").forEach((el) => {
    progressEls.push({ el, mode: el.dataset.progress === "sticky" ? "sticky" : "pass", last: -1 });
  });
}

let lastKey = "";
let layoutDirty = true;
/** Düzen değişti (yazı tipi yüklendi, demo açıldı...): bir sonraki karede ilerlemeler yeniden ölçülür. */
export function markLayoutDirty(): void {
  layoutDirty = true;
}

function updateProgress(): void {
  if (reduced) return;
  const key = `${viewport.y}|${viewport.w}|${viewport.h}`;
  if (key === lastKey && !layoutDirty) return;
  lastKey = key;
  layoutDirty = false;
  const vh = viewport.h;
  for (const p of progressEls) {
    const r = p.el.getBoundingClientRect();
    if (r.bottom < -vh || r.top > 2 * vh) continue;
    const raw = p.mode === "sticky" ? -r.top / Math.max(1, r.height - vh) : (vh - r.top) / (r.height + vh);
    const v = Math.min(1, Math.max(0, raw));
    if (Math.abs(v - p.last) > 0.0005) {
      p.last = v;
      p.el.style.setProperty("--p", v.toFixed(4));
    }
  }
}

function tick(now: number): void {
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  viewport.w = innerWidth;
  viewport.h = innerHeight;
  viewport.y = scrollY;
  updateProgress();
  frameFns.forEach((fn) => fn(dt, now / 1000));
  requestAnimationFrame(tick);
}

function start(): void {
  if (running) return;
  running = true;
  last = performance.now();
  requestAnimationFrame(tick);
}

/** Sayfadaki bir öğenin belge içindeki dikey konumu (kaydırmadan bağımsız). */
export function docTop(el: Element): number {
  return el.getBoundingClientRect().top + scrollY;
}
