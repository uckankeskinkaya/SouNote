// Açılış (~2,9 sn): karanlık → kâğıt ve kalem birleşir → mavi-mor ışık geçer → kamera geri çekilir,
// logo 3B tabletin ekranına iner. Sayfa beklemez: gezinme ve indirme bu sırada da kullanılabilir.
// Oturum başına bir kez oynar; hareket azaltılmışsa hiç oynamaz. Tıklama, tuş ya da kaydırma atlatır.
import type { StageApi } from "../three/stage";

const root = document.documentElement;

export function introWanted(): boolean {
  return root.classList.contains("has-intro");
}

export function playIntro(getStage: () => StageApi | null, stageReady: Promise<unknown>): Promise<void> {
  const box = document.getElementById("intro");
  if (!box || !introWanted()) return Promise.resolve();
  const logo = box.querySelector<HTMLElement>(".intro__logo")!;
  const paper = box.querySelector<HTMLElement>(".intro__paper")!;
  const pen = box.querySelector<HTMLElement>(".intro__pen")!;
  const sheen = box.querySelector<HTMLElement>(".intro__sheen")!;
  const word = box.querySelector<HTMLElement>(".intro__word")!;
  const aura = box.querySelector<HTMLElement>(".intro__aura")!;
  sheen.style.setProperty("--mask", `url("${new URL("brand/logo-a-symbol-512.png", document.baseURI).href}")`);

  const anims: Animation[] = [];
  const A = (el: Element, k: Keyframe[], o: KeyframeAnimationOptions) => {
    const a = el.animate(k, { fill: "both", ...o });
    anims.push(a);
    return a;
  };
  const E = "cubic-bezier(0.22, 1, 0.36, 1)";

  A(aura, [{ opacity: 0 }, { opacity: 0.7, offset: 0.5 }, { opacity: 1 }], { duration: 1800, easing: "ease-out" });
  A(paper, [
    { opacity: 0, transform: "translateY(22px) rotate(-7deg) scale(0.92)", filter: "blur(10px)" },
    { opacity: 1, transform: "none", filter: "blur(0px)" },
  ], { duration: 850, delay: 180, easing: E });
  // Kalem kendi ekseni boyunca (sağ üstten) gelir ve kâğıda oturur
  A(pen, [
    { opacity: 0, transform: "translate(16%, -22%) rotate(9deg)" },
    { opacity: 1, offset: 0.45 },
    { opacity: 1, transform: "none" },
  ], { duration: 720, delay: 620, easing: E });
  A(sheen, [
    { opacity: 0, backgroundPosition: "110% 0" },
    { opacity: 1, offset: 0.25 },
    { opacity: 1, offset: 0.75 },
    { opacity: 0, backgroundPosition: "-10% 0" },
  ], { duration: 820, delay: 1220, easing: "ease-in-out" });
  A(word, [{ opacity: 0, transform: "translateY(10px)", letterSpacing: "0.04em" }, { opacity: 1, transform: "none", letterSpacing: "-0.03em" }], { duration: 650, delay: 1350, easing: E });

  let done = false;
  let resolveFn!: () => void;
  const finished = new Promise<void>((r) => (resolveFn = r));

  const finish = () => {
    if (done) return;
    done = true;
    anims.forEach((a) => a.cancel());
    box.remove();
    root.classList.remove("has-intro");
    getStage()?.setIntro(null);
    try { sessionStorage.setItem("sn-intro", "1"); } catch { /* yok say */ }
    removeEventListener("keydown", skip);
    removeEventListener("wheel", skip);
    removeEventListener("touchstart", skip);
    removeEventListener("pointerdown", skip);
    resolveFn();
  };
  const skip = () => {
    if (done) return;
    const st = getStage();
    // geri çekilme başladıysa doğal bitsin, yoksa hızlıca kapan
    box.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 260, fill: "forwards" }).finished.then(finish);
    st?.setIntro(1);
  };
  addEventListener("keydown", skip, { once: true });
  addEventListener("wheel", skip, { once: true, passive: true });
  addEventListener("touchstart", skip, { once: true, passive: true });
  addEventListener("pointerdown", skip, { once: true });

  // 1,9 sn: geri çekilme. Tablet hazırsa logo, tabletin ekranındaki logonun yerine iner.
  const PULL_AT = 1900, PULL_MS = 1000;
  setTimeout(async () => {
    if (done) return;
    const ok = await Promise.race([stageReady.then(() => true), new Promise<boolean>((r) => setTimeout(() => r(false), 600))]);
    const st = ok ? getStage() : null;
    if (done) return;
    A(word, [{ opacity: 1 }, { opacity: 0 }], { duration: 300, easing: "ease-in" });
    A(box, [{ backgroundColor: "rgba(5,8,20,1)" }, { backgroundColor: "rgba(5,8,20,0)" }], { duration: PULL_MS * 0.8, easing: "ease-in-out" });
    box.style.background = "transparent";
    A(aura, [{ opacity: 1 }, { opacity: 0 }], { duration: PULL_MS * 0.7 });
    if (!st) {
      A(logo, [{ opacity: 1, transform: "none" }, { opacity: 0, transform: "scale(0.6)" }], { duration: 700, easing: E }).finished.then(finish);
      return;
    }
    const start = performance.now();
    const base = logo.getBoundingClientRect();
    const tick = (now: number) => {
      if (done) return;
      const p = Math.min(1, (now - start) / PULL_MS);
      st.setIntro(p);
      const r = st.splashLogoRect();
      if (r) {
        const s = r.size / base.width;
        const dx = r.x - (base.left + base.width / 2);
        const dy = r.y - (base.top + base.height / 2);
        logo.style.transform = `translate(${dx}px, ${dy}px) scale(${s})`;
      }
      // son üçte birde 3B logoya devret
      logo.style.opacity = String(Math.min(1, Math.max(0, (1 - p) * 3)));
      if (p < 1) requestAnimationFrame(tick);
      else finish();
    };
    requestAnimationFrame(tick);
  }, PULL_AT);

  // Güvenlik: ne olursa olsun 4 sn'de kapan
  setTimeout(() => { if (!done) finish(); }, 4000);
  return finished;
}
