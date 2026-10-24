// Kaydırma koreografisi: tabletin pozu, kaydırma konumunun saf bir fonksiyonudur.
//
// Her bölümde görünmez bir "yuva" (.slot[data-slot]) vardır; yerini ve boyunu CSS belirler (masaüstü/telefon ayrı).
// Duraklar (anchor) "şu bölümün şu noktasında tablet şu yuvada, şu açıda" der. İki durak arasında
// yuvaların o anki konumları ve açılar yumuşakça karıştırılır. Böylece hızlı ya da geri kaydırmada,
// boyut değişiminde ve mobil araç çubuğu oynadığında sonuç hep tutarlıdır; ayrı bir animasyon zaman çizelgesi yoktur.
import { docTop, viewport } from "../core/scroll";
import { DIM } from "./tablet";

export interface PoseSpec {
  /** Yuva adı; "view" = ekranın ortası; null = komşu duraktan konumu devral. */
  slot: string | null;
  dx?: number; dy?: number; // ekran genişliği/yüksekliği oranında kaydırma
  scale?: number;
  rx?: number; ry?: number; rz?: number;
  orient?: 0 | 1;
  screen?: string;
  glow?: number;
  vis?: number;
}

interface Anchor { sel: string; k: number; pose: PoseSpec }

/** Hesaplanmış poz (CSS pikseli; ölçek = model biriminin piksel karşılığı). */
export interface Pose { cx: number; cy: number; px: number; rx: number; ry: number; rz: number; orient: number; glow: number; vis: number; screen: string }

const S = (side: 1 | -1, narrow: boolean) => side * (narrow ? 0.14 : 0.3);

function anchors(narrow: boolean): Anchor[] {
  const feat = (id: string, side: 1 | -1, screen: string, orient: 0 | 1 = 0): Anchor[] => {
    const p: PoseSpec = { slot: id, ry: S(side, narrow) * (orient ? 0.8 : 1), rx: 0.05, rz: side * -0.015, screen, orient, glow: 0.6 };
    return [{ sel: `#${id}`, k: 0, pose: p }, { sel: `#${id}`, k: 1, pose: { ...p, ry: (p.ry ?? 0) * 0.55, rx: 0.02 } }];
  };
  return [
    { sel: "#top", k: 0, pose: { slot: "hero", rx: 0.07, ry: narrow ? -0.24 : -0.36, rz: 0.03, screen: "home", glow: 0.75 } },
    { sel: "#hikaye", k: 0.1, pose: { slot: "problem", scale: 0.92, rx: 0.14, ry: -2.45, rz: 0.1, screen: "home", glow: 0.3 } },
    { sel: "#hikaye", k: 0.8, pose: { slot: "problem", scale: 0.92, rx: 0.04, ry: -3.0, rz: -0.05, screen: "home", glow: 0.25 } },
    { sel: "#karar", k: 0.5, pose: { slot: "view", dx: 1.05, dy: -0.05, scale: 0.6, rx: 0.1, ry: -3.7, rz: 0.2, screen: "onboarding", glow: 0 } },
    { sel: "#sonuc", k: 0.12, pose: { slot: "solution", rx: 0.03, ry: 0, rz: 0, screen: "onboarding", glow: 0.95 } },
    { sel: "#sonuc", k: 0.88, pose: { slot: "solution", rx: -0.02, ry: 0.14, rz: 0, screen: "onboarding", glow: 0.95 } },
    { sel: "#gelistirici", k: 0.5, pose: { slot: "view", dx: -1.05, scale: 0.7, rx: 0.1, ry: 1.2, rz: 0.1, screen: "notebook", glow: 0 } },
    ...feat("f-kalem", 1, "notebook"),
    ...feat("f-dersler", -1, "courses-land", 1),
    ...feat("f-sinavlar", 1, "exams"),
    ...feat("f-writing", -1, "writing"),
    ...feat("f-ortalama", 1, "grades"),
    ...feat("f-gunluk", -1, "week"),
    { sel: "#dahasi", k: 0.5, pose: { slot: "view", dy: -1.1, scale: 0.8, rx: -0.4, ry: -0.6, screen: "week", glow: 0, vis: 1 } },
    { sel: "#dene", k: 0, pose: { slot: null, screen: "splash", vis: 0, glow: 0 } },
    { sel: "#karsilastirma", k: 1, pose: { slot: "finale", dy: 0.9, scale: 0.8, rx: 0.5, ry: 0.4, screen: "splash", vis: 0, glow: 0 } },
    { sel: ".finale__pin", k: 0.5, pose: { slot: "finale", rx: 0.06, ry: narrow ? -0.18 : 0.32, rz: narrow ? 0 : -0.04, screen: "splash", glow: 1.25, vis: 1 } },
    { sel: ".install", k: 0.4, pose: { slot: "finale", rx: 0.02, ry: narrow ? -0.05 : 0.18, screen: "splash", glow: 0.6, vis: 1 } },
    { sel: ".faq", k: 0.5, pose: { slot: null, screen: "splash", glow: 0, vis: 0 } },
  ];
}

const slotCache = new Map<string, Element | null>();
const slotEl = (name: string) => {
  if (!slotCache.has(name)) slotCache.set(name, document.querySelector(`[data-slot="${name}"]`));
  return slotCache.get(name) ?? null;
};
const elCache = new Map<string, HTMLElement | null>();
const el = (sel: string) => {
  if (!elCache.has(sel)) elCache.set(sel, document.querySelector<HTMLElement>(sel));
  return elCache.get(sel) ?? null;
};

/** Yuva kutusunu yönelime göre tablete sığdırır. */
export function fitPx(w: number, h: number, orient: number): number {
  const tw = DIM.w + (DIM.h - DIM.w) * orient;
  const th = DIM.h + (DIM.w - DIM.h) * orient;
  return Math.max(1, Math.min(w / tw, h / th));
}

function slotBox(name: string | null): { cx: number; cy: number; w: number; h: number } | null {
  const vw = viewport.w, vh = viewport.h;
  if (name === "view") return { cx: vw / 2, cy: vh / 2, w: vw * 0.8, h: vh * 0.72 };
  if (!name) return null;
  const e = slotEl(name);
  if (!e) return null;
  const r = e.getBoundingClientRect();
  if (r.width < 2 || r.height < 2) return null;
  return { cx: r.left + r.width / 2, cy: r.top + r.height / 2, w: r.width, h: r.height };
}

function resolve(p: PoseSpec): Pose & { has: boolean } {
  const box = slotBox(p.slot);
  const orient = p.orient ?? 0;
  const vw = viewport.w, vh = viewport.h;
  return {
    has: !!box,
    cx: (box?.cx ?? vw / 2) + (p.dx ?? 0) * vw,
    cy: (box?.cy ?? vh / 2) + (p.dy ?? 0) * vh,
    px: box ? fitPx(box.w, box.h, orient) * (p.scale ?? 1) : 0,
    rx: p.rx ?? 0, ry: p.ry ?? 0, rz: p.rz ?? 0, orient,
    glow: p.glow ?? 0.5, vis: p.vis ?? 1, screen: p.screen ?? "home",
  };
}

const smooth = (t: number) => t * t * (3 - 2 * t);

export class Choreography {
  private list: Anchor[] = [];
  private narrow = false;
  /** Son karede kullanılan durak çifti (hareket azaltıldığında geçiş tespiti için). */
  segment = -1;

  constructor() { this.refresh(); }

  refresh(): void {
    this.narrow = viewport.w < 920;
    this.list = anchors(this.narrow).filter((a) => el(a.sel));
  }

  evaluate(reduced: boolean): Pose {
    const narrow = viewport.w < 920;
    if (narrow !== this.narrow) this.refresh();
    const vh = viewport.h;
    const y = viewport.y;
    const pos = this.list.map((a) => {
      const e = el(a.sel)!;
      return docTop(e) + a.k * (e.offsetHeight - vh);
    });
    let i = 0;
    while (i < pos.length - 1 && y >= pos[i + 1]) i++;
    const a = this.list[i];
    const b = this.list[Math.min(i + 1, this.list.length - 1)];
    const span = pos[Math.min(i + 1, pos.length - 1)] - pos[i];
    let t = span > 0 ? Math.min(1, Math.max(0, (y - pos[i]) / span)) : 0;
    if (y < pos[0]) t = 0;
    if (reduced) t = t < 0.5 ? 0 : 1;
    this.segment = i * 2 + (t >= 0.5 ? 1 : 0);
    const te = smooth(t);
    const A = resolve(a.pose), B = resolve(b.pose);
    if (!A.has) { A.cx = B.cx; A.cy = B.cy; A.px = B.px; }
    if (!B.has) { B.cx = A.cx; B.cy = A.cy; B.px = A.px; }
    const L = (u: number, v: number) => u + (v - u) * te;
    const out: Pose = {
      cx: L(A.cx, B.cx), cy: L(A.cy, B.cy), px: L(A.px, B.px),
      rx: L(A.rx, B.rx), ry: L(A.ry, B.ry), rz: L(A.rz, B.rz),
      orient: L(A.orient, B.orient), glow: L(A.glow, B.glow), vis: L(A.vis, B.vis),
      screen: te < 0.5 ? A.screen : B.screen,
    };
    // Farklı yuvalar arasında geçerken hafif bir savrulma: tablet sahneden sahneye "uçar".
    if (!reduced && a.pose.slot !== b.pose.slot && A.has && B.has) {
      const arc = Math.sin(Math.PI * te);
      out.ry += arc * 0.32 * Math.sign(B.cx - A.cx || 1);
      out.rx += arc * 0.08;
      out.px *= 1 - arc * 0.08;
    }
    return out;
  }
}
