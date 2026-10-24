// Bilgi kartı denemesi: çevir, kaydır (sağ = Öğrendim, sol = Tekrar et), puan ve seri, sonda mini test.
// Fizik motoru yok: sürükleme bırakılınca küçük bir yay benzetimi (rAF) kartı yerine oturtur ya da fırlatır.
import "./flashcards.css";

interface Card { q: string; a: string; ex?: string }

// Site için örnek deste (uygulamadaki kartlar kullanıcının kendi belgelerinden üretilir).
const DECK: Card[] = [
  { q: "reluctant", a: "isteksiz, gönülsüz", ex: "She was reluctant to leave early." },
  { q: "to put off", a: "ertelemek", ex: "Don't put off your revision." },
  { q: "If I had known, I ___ you.", a: "would have told", ex: "Third conditional: geçmişle ilgili hayalî durum." },
  { q: "unless", a: "-medikçe, -mezse (if … not)", ex: "You won't pass unless you study." },
  { q: "despite / although", a: "despite + isim ya da V-ing · although + cümle", ex: "Despite the rain… / Although it rained…" },
  { q: "Opinion essay'de tez cümlesi", a: "Giriş paragrafının sonunda", ex: "Konuya kendi görüşünü açıkça yazdığın cümle." },
];

const reduce = () => matchMedia("(prefers-reduced-motion: reduce)").matches;

export function mountFlashcards(host: HTMLElement): void {
  host.innerHTML = `
  <div class="fc">
    <div class="fc__top">
      <div class="fc__progress" role="progressbar" aria-label="Öğrenilen kartlar" aria-valuemin="0" aria-valuemax="${DECK.length}" aria-valuenow="0"><i></i></div>
      <div class="fc__stats"><span data-s="learned">0 / ${DECK.length} öğrenildi</span><span data-s="points">0 puan</span><span data-s="streak" hidden></span></div>
    </div>
    <div class="fc__stage" aria-live="polite"></div>
    <div class="fc__actions">
      <button type="button" class="dbtn fc__btn fc__btn--repeat" data-a="repeat">← Tekrar et</button>
      <button type="button" class="dbtn fc__btn" data-a="flip">Çevir</button>
      <button type="button" class="dbtn fc__btn fc__btn--ok" data-a="learned">Öğrendim →</button>
    </div>
    <p class="demo__note">Site için örnek deste · kaydedilmez. Uygulamada kartları kendi PDF'inden seçerek ya da asistana ürettirerek oluşturursun; zamanı gelen kartlar 5 kutulu aralıklı tekrarla geri gelir.</p>
  </div>`;

  const root = host.querySelector<HTMLElement>(".fc")!;
  const stage = root.querySelector<HTMLElement>(".fc__stage")!;
  const actions = root.querySelector<HTMLElement>(".fc__actions")!;
  const bar = root.querySelector<HTMLElement>(".fc__progress")!;
  const stat = (k: string) => root.querySelector<HTMLElement>(`[data-s="${k}"]`)!;

  let queue: number[] = [];
  let learned = 0, points = 0, streak = 0, best = 0;
  let flipped = false;
  let busy = false;
  let cardEls: HTMLElement[] = [];

  function start() {
    queue = DECK.map((_, i) => i);
    learned = points = streak = best = 0;
    actions.hidden = false;
    renderStats();
    renderStack();
  }

  function renderStats() {
    stat("learned").textContent = `${learned} / ${DECK.length} öğrenildi`;
    stat("points").textContent = `${points} puan`;
    const s = stat("streak");
    s.hidden = streak < 2;
    s.textContent = `🔥 ${streak} seri`;
    bar.setAttribute("aria-valuenow", String(learned));
    bar.style.setProperty("--w", `${(learned / DECK.length) * 100}%`);
  }

  function cardHtml(c: Card, depth: number) {
    return `<div class="fc__card" style="--d:${depth}" ${depth ? 'aria-hidden="true"' : 'tabindex="0" role="button" aria-label="Kartı çevir"'}>
      <div class="fc__inner">
        <div class="fc__face fc__front"><small>Soru</small><b>${c.q}</b><span class="fc__tap">Çevirmek için dokun</span></div>
        <div class="fc__face fc__back"><small>Cevap</small><b>${c.a}</b>${c.ex ? `<span>${c.ex}</span>` : ""}</div>
      </div>
    </div>`;
  }

  function renderStack() {
    flipped = false;
    stage.innerHTML = queue.slice(0, 3).map((i, d) => cardHtml(DECK[i], d)).reverse().join("");
    cardEls = [...stage.querySelectorAll<HTMLElement>(".fc__card")].reverse();
    const top = cardEls[0];
    if (!top) return;
    bindDrag(top);
    top.addEventListener("keydown", (e) => {
      if (e.key === " " || e.key === "Enter") { e.preventDefault(); flip(); }
    });
  }

  function flip() {
    const top = cardEls[0];
    if (!top || busy) return;
    flipped = !flipped;
    top.classList.toggle("is-flipped", flipped);
    top.setAttribute("aria-label", flipped ? `Cevap: ${DECK[queue[0]].a}. Tekrar çevirmek için dokun` : "Kartı çevir");
  }

  // Basit yay: x'' = -k(x - hedef) - c x'
  function spring(el: HTMLElement, from: number, to: number, v0: number, done?: () => void) {
    if (reduce()) { setX(el, to); done?.(); return; }
    let x = from, v = v0, last = performance.now();
    const k = 170, c = 20;
    const step = (now: number) => {
      const dt = Math.min(0.032, (now - last) / 1000);
      last = now;
      const a = -k * (x - to) - c * v;
      v += a * dt;
      x += v * dt;
      setX(el, x);
      if (Math.abs(x - to) < 0.5 && Math.abs(v) < 5) { setX(el, to); done?.(); return; }
      requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
  function setX(el: HTMLElement, x: number) {
    el.style.setProperty("--x", `${x}px`);
    el.style.setProperty("--r", `${x * 0.05}deg`);
    const w = stage.clientWidth || 300;
    el.dataset.hint = x > w * 0.12 ? "ok" : x < -w * 0.12 ? "repeat" : "";
  }

  function fling(dir: 1 | -1, v0 = 0) {
    const top = cardEls[0];
    if (!top || busy) return;
    busy = true;
    const w = stage.clientWidth;
    const to = dir * (w + 160);
    const fromX = parseFloat(top.style.getPropertyValue("--x")) || 0;
    top.classList.add("is-leaving");
    const after = () => {
      busy = false;
      if (dir === 1) {
        learned++;
        streak++;
        best = Math.max(best, streak);
        points += 10 + (streak >= 3 ? 5 : 0);
        queue.shift();
      } else {
        streak = 0;
        queue.push(queue.shift()!);
      }
      renderStats();
      if (!queue.length) quiz();
      else renderStack();
    };
    if (reduce()) { after(); return; }
    // fırlatma: başlangıç hızıyla ekran dışına (yay yerine sönümsüz hareket)
    let x = fromX, v = v0 || dir * 1800, last = performance.now();
    const step = (now: number) => {
      const dt = Math.min(0.032, (now - last) / 1000);
      last = now;
      v += dir * 2600 * dt;
      x += v * dt;
      setX(top, x);
      if (Math.abs(x) >= Math.abs(to)) { after(); return; }
      requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  function bindDrag(el: HTMLElement) {
    let id = -1, sx = 0, sy = 0, x = 0, moved = false, lastX = 0, lastT = 0, vx = 0;
    el.addEventListener("pointerdown", (e) => {
      if (busy || id !== -1) return;
      id = e.pointerId; sx = e.clientX; sy = e.clientY; x = 0; moved = false; lastX = e.clientX; lastT = performance.now(); vx = 0;
      el.setPointerCapture(e.pointerId);
      el.classList.add("is-dragging");
    });
    el.addEventListener("pointermove", (e) => {
      if (e.pointerId !== id) return;
      const dx = e.clientX - sx, dy = e.clientY - sy;
      if (!moved && Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy)) moved = true;
      if (!moved) return;
      x = dx;
      const now = performance.now();
      vx = ((e.clientX - lastX) / Math.max(1, now - lastT)) * 1000;
      lastX = e.clientX; lastT = now;
      setX(el, x);
    });
    const up = (e: PointerEvent) => {
      if (e.pointerId !== id) return;
      id = -1;
      el.classList.remove("is-dragging");
      if (!moved) { flip(); return; }
      const w = stage.clientWidth;
      if (x > w * 0.28 || vx > 900) fling(1, Math.max(vx, 900));
      else if (x < -w * 0.28 || vx < -900) fling(-1, Math.min(vx, -900));
      else spring(el, x, 0, vx);
    };
    el.addEventListener("pointerup", up);
    el.addEventListener("pointercancel", up);
  }

  actions.addEventListener("click", (e) => {
    const a = (e.target as HTMLElement).closest<HTMLButtonElement>("[data-a]")?.dataset.a;
    if (a === "flip") flip();
    if (a === "learned") fling(1);
    if (a === "repeat") fling(-1);
  });
  root.addEventListener("keydown", (e) => {
    if ((e.target as HTMLElement).closest(".fc__quiz")) return;
    if (e.key === "ArrowRight") fling(1);
    if (e.key === "ArrowLeft") fling(-1);
  });

  // ---------- Mini test ----------
  function quiz() {
    actions.hidden = true;
    const order = [2, 0, 4];
    let qi = 0, correct = 0;
    const ask = () => {
      const c = DECK[order[qi]];
      const others = DECK.filter((d) => d !== c).map((d) => d.a);
      const opts = [c.a, others[(qi * 2 + 1) % others.length], others[(qi * 2 + 3) % others.length]];
      const shuffled = [opts[(qi + 1) % 3], opts[(qi + 2) % 3], opts[qi % 3]];
      stage.innerHTML = `<div class="fc__quiz">
        <small>Mini test · ${qi + 1} / ${order.length}</small>
        <b>${c.q}</b>
        <div class="fc__opts">${shuffled.map((o) => `<button type="button" class="fc__opt">${o}</button>`).join("")}</div>
      </div>`;
      const btns = [...stage.querySelectorAll<HTMLButtonElement>(".fc__opt")];
      btns[0].focus({ preventScroll: true });
      btns.forEach((b) => b.addEventListener("click", () => {
        const ok = b.textContent === c.a;
        btns.forEach((x) => { x.disabled = true; if (x.textContent === c.a) x.classList.add("is-ok"); });
        if (!ok) b.classList.add("is-bad");
        if (ok) { correct++; points += 20; renderStats(); }
        setTimeout(() => { qi++; if (qi < order.length) ask(); else complete(correct, order.length); }, reduce() ? 300 : 900);
      }));
    };
    ask();
  }

  function complete(correct: number, total: number) {
    const colors = ["#4a7dff", "#8b6cf6", "#60d7f7", "#f4f7ff"];
    const bits = reduce() ? "" : Array.from({ length: 28 }, (_, i) => {
      const ang = (i / 28) * Math.PI * 2;
      const dist = 90 + ((i * 37) % 70);
      return `<i style="--tx:${Math.cos(ang) * dist}px;--ty:${Math.sin(ang) * dist - 30}px;--c:${colors[i % 4]};--rot:${(i * 47) % 360}deg;--dl:${(i % 5) * 40}ms"></i>`;
    }).join("");
    stage.innerHTML = `<div class="fc__done">
      <div class="fc__burst" aria-hidden="true">${bits}</div>
      <div class="fc__medal" aria-hidden="true">${correct === total ? "★" : "✓"}</div>
      <b>Deste bitti!</b>
      <p>${DECK.length} kartın hepsini öğrendin. Mini testte ${total} sorudan ${correct} doğru.</p>
      <p class="fc__score">${points} puan · en uzun seri ${best}</p>
      <button type="button" class="btn btn--primary" data-a="restart">Baştan başla</button>
    </div>`;
    const b = stage.querySelector<HTMLButtonElement>('[data-a="restart"]')!;
    b.addEventListener("click", start);
    b.focus({ preventScroll: true });
  }

  start();
  cardEls[0]?.focus({ preventScroll: true });
}
