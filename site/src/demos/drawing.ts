// Çizim denemesi: tarayıcıda SouNote kaleminin küçük bir tadımlığı (uygulamanın kendisi değildir).
// Çizgiler kâğıdın sol üst köşesine göre CSS pikseli olarak vektör tutulur; pencere boyu değişince
// keskin biçimde yeniden çizilir ve çizgili kâğıtla hizası bozulmaz. Geri al tam yeniden çizimle çalışır.
import "./drawing.css";

type Tool = "pen" | "eraser";
interface Stroke { tool: Tool; color: string; width: number; pressure: boolean; pts: number[] }
type Entry = Stroke | { clear: true };

const COLORS: [string, string][] = [
  ["#1a1f2e", "Siyah"], ["#2f5bd3", "Mavi"], ["#d64545", "Kırmızı"], ["#2e7d4f", "Yeşil"], ["#7048c8", "Mor"], ["#e07b16", "Turuncu"],
];
const MAX_ENTRIES = 400;

export function mountDrawing(host: HTMLElement): void {
  host.innerHTML = `
  <div class="draw">
    <div class="draw__bar" role="toolbar" aria-label="Çizim araçları">
      <div class="draw__group" role="radiogroup" aria-label="Araç">
        <button type="button" class="dbtn" role="radio" aria-checked="true" data-tool="pen" title="Kalem (P)">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20l4-1 11-11-3-3L5 16l-1 4Zm10-13 3 3"/></svg><span>Kalem</span></button>
        <button type="button" class="dbtn" role="radio" aria-checked="false" data-tool="eraser" title="Silgi (E)">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 20h12M5 15l8.5-8.5a2 2 0 0 1 2.8 0l2.2 2.2a2 2 0 0 1 0 2.8L11 19H8.5L5 15.5V15Zm3-3 5 5"/></svg><span>Silgi</span></button>
      </div>
      <div class="draw__group draw__colors" role="radiogroup" aria-label="Renk">
        ${COLORS.map(([c, n], i) => `<button type="button" class="swatch" role="radio" aria-checked="${i === 1}" aria-label="${n}" data-color="${c}" style="--c:${c}"></button>`).join("")}
      </div>
      <label class="draw__width"><span>Kalınlık</span><input type="range" min="1" max="24" value="4" aria-label="Kalınlık"><output>4</output></label>
      <div class="draw__group">
        <button type="button" class="dbtn" data-act="undo" title="Geri al (Ctrl+Z)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 14 4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/></svg><span>Geri al</span></button>
        <button type="button" class="dbtn" data-act="clear"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 7h14M10 7V5h4v2m-7 0 1 12h8l1-12"/></svg><span>Temizle</span></button>
        <button type="button" class="dbtn" data-act="png"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 19.5h14"/></svg><span>PNG</span></button>
        <button type="button" class="dbtn draw__full" data-act="full" aria-pressed="false"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg><span>Tam ekran</span></button>
      </div>
    </div>
    <div class="draw__paper">
      <canvas aria-label="Çizim alanı: fare, parmak ya da kalemle çiz" role="img"></canvas>
      <p class="draw__hint" aria-hidden="true">Buraya yaz ya da çiz</p>
    </div>
    <p class="demo__note">Tarayıcı denemesi · kaydedilmez. Uygulamada basınca duyarlı kalem türleri, fosforlu, lasso ve PDF üstüne yazma var.</p>
  </div>`;

  const root = host.querySelector<HTMLElement>(".draw")!;
  const paper = root.querySelector<HTMLElement>(".draw__paper")!;
  const canvas = root.querySelector("canvas")!;
  const hint = root.querySelector<HTMLElement>(".draw__hint")!;
  const ctx = canvas.getContext("2d")!;
  const range = root.querySelector<HTMLInputElement>('input[type="range"]')!;
  const out = root.querySelector("output")!;

  let tool: Tool = "pen";
  let color = COLORS[1][0];
  let width = 4;
  const history: Entry[] = [];
  let current: Stroke | null = null;
  let activeId = -1;
  let cssW = 1, cssH = 1, dpr = 1;

  function resize() {
    const r = paper.getBoundingClientRect();
    cssW = Math.max(1, r.width);
    cssH = Math.max(1, r.height);
    dpr = Math.min(devicePixelRatio || 1, 2.5);
    canvas.width = Math.round(cssW * dpr);
    canvas.height = Math.round(cssH * dpr);
    canvas.style.width = `${cssW}px`;
    canvas.style.height = `${cssH}px`;
    redraw();
  }

  function visible(): Stroke[] {
    let start = 0;
    for (let i = history.length - 1; i >= 0; i--) if ("clear" in history[i]) { start = i + 1; break; }
    return history.slice(start) as Stroke[];
  }

  function seg(s: Stroke, i: number, c: CanvasRenderingContext2D) {
    // i. noktaya kadar olan kısmı orta noktalar üzerinden ikinci derece eğriyle çizer
    const p = s.pts;
    const x = (k: number) => p[k * 3], y = (k: number) => p[k * 3 + 1];
    const n = p.length / 3;
    const lw = s.width * (s.pressure ? 0.35 + 1.1 * p[i * 3 + 2] : 1);
    c.lineWidth = s.tool === "eraser" ? Math.max(lw * 3, 10) : lw;
    c.beginPath();
    if (n === 1 || i === 0) {
      c.moveTo(x(0), y(0));
      c.lineTo(x(0) + 0.01, y(0));
    } else if (i === 1) {
      c.moveTo(x(0), y(0));
      c.lineTo((x(0) + x(1)) / 2, (y(0) + y(1)) / 2);
    } else {
      c.moveTo((x(i - 2) + x(i - 1)) / 2, (y(i - 2) + y(i - 1)) / 2);
      c.quadraticCurveTo(x(i - 1), y(i - 1), (x(i - 1) + x(i)) / 2, (y(i - 1) + y(i)) / 2);
    }
    c.stroke();
  }

  function drawStroke(s: Stroke, c: CanvasRenderingContext2D, from = 0) {
    c.save();
    c.lineCap = "round";
    c.lineJoin = "round";
    c.strokeStyle = s.color;
    c.globalCompositeOperation = s.tool === "eraser" ? "destination-out" : "source-over";
    const n = s.pts.length / 3;
    for (let i = from; i < n; i++) seg(s, i, c);
    c.restore();
  }

  function redraw() {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const list = visible();
    list.forEach((s) => drawStroke(s, ctx));
    hint.hidden = list.length > 0 || !!current;
  }

  const local = (e: PointerEvent) => {
    const r = canvas.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  };

  function addPoint(e: PointerEvent) {
    if (!current) return;
    const { x, y } = local(e);
    const p = current.pts;
    const n = p.length / 3;
    if (n > 0) {
      const dx = x - p[(n - 1) * 3], dy = y - p[(n - 1) * 3 + 1];
      if (dx * dx + dy * dy < 0.6) return;
    }
    p.push(x, y, e.pressure || 0.5);
    ctx.save();
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    drawStroke(current, ctx, p.length / 3 - 1);
    ctx.restore();
  }

  canvas.addEventListener("pointerdown", (e) => {
    if (activeId !== -1 || (e.pointerType === "mouse" && e.button !== 0)) return;
    activeId = e.pointerId;
    canvas.setPointerCapture(e.pointerId);
    current = { tool: e.button === 5 ? "eraser" : tool, color, width, pressure: e.pointerType === "pen", pts: [] };
    hint.hidden = true;
    addPoint(e);
    e.preventDefault();
  });
  canvas.addEventListener("pointermove", (e) => {
    if (e.pointerId !== activeId || !current) return;
    const list = e.getCoalescedEvents?.() ?? [e];
    (list.length ? list : [e]).forEach(addPoint);
  });
  const end = (e: PointerEvent) => {
    if (e.pointerId !== activeId) return;
    activeId = -1;
    if (current && current.pts.length) {
      history.push(current);
      if (history.length > MAX_ENTRIES) history.splice(0, history.length - MAX_ENTRIES);
    }
    current = null;
  };
  canvas.addEventListener("pointerup", end);
  canvas.addEventListener("pointercancel", end);

  // Araçlar
  root.querySelectorAll<HTMLButtonElement>("[data-tool]").forEach((b) =>
    b.addEventListener("click", () => setTool(b.dataset.tool as Tool)),
  );
  function setTool(t: Tool) {
    tool = t;
    root.querySelectorAll("[data-tool]").forEach((x) => x.setAttribute("aria-checked", String((x as HTMLElement).dataset.tool === t)));
    paper.classList.toggle("is-eraser", t === "eraser");
  }
  root.querySelectorAll<HTMLButtonElement>("[data-color]").forEach((b) =>
    b.addEventListener("click", () => {
      color = b.dataset.color!;
      root.querySelectorAll("[data-color]").forEach((x) => x.setAttribute("aria-checked", String(x === b)));
      setTool("pen");
    }),
  );
  range.addEventListener("input", () => {
    width = Number(range.value);
    out.textContent = range.value;
  });

  const undo = () => {
    history.pop();
    redraw();
  };
  const actions: Record<string, () => void> = {
    undo,
    clear: () => {
      if (visible().length) history.push({ clear: true });
      redraw();
    },
    png: exportPng,
    full: () => setFull(!root.classList.contains("is-full")),
  };
  root.querySelectorAll<HTMLButtonElement>("[data-act]").forEach((b) => b.addEventListener("click", () => actions[b.dataset.act!]()));
  root.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "z") { e.preventDefault(); undo(); }
    if (e.key === "Escape" && root.classList.contains("is-full")) setFull(false);
  });

  function setFull(on: boolean) {
    root.classList.toggle("is-full", on);
    document.documentElement.classList.toggle("draw-full", on);
    const b = root.querySelector<HTMLButtonElement>('[data-act="full"]')!;
    b.setAttribute("aria-pressed", String(on));
    b.querySelector("span")!.textContent = on ? "Küçült" : "Tam ekran";
    requestAnimationFrame(resize);
  }

  function exportPng() {
    const c = document.createElement("canvas");
    c.width = canvas.width;
    c.height = canvas.height;
    const g = c.getContext("2d")!;
    g.fillStyle = "#fbfbfd";
    g.fillRect(0, 0, c.width, c.height);
    g.strokeStyle = "rgba(47, 91, 211, 0.16)";
    g.lineWidth = dpr;
    for (let y = 40; y < cssH; y += 32) {
      g.beginPath();
      g.moveTo(0, y * dpr);
      g.lineTo(c.width, y * dpr);
      g.stroke();
    }
    g.drawImage(canvas, 0, 0);
    c.toBlob((blob) => {
      if (!blob) return;
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = "sounote-deneme.png";
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 2000);
    }, "image/png");
  }

  new ResizeObserver(() => resize()).observe(paper);
  resize();
  root.querySelector<HTMLElement>('[data-tool="pen"]')?.focus({ preventScroll: true });
}
