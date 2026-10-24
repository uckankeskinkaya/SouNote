import "./styles/fonts.css";
import "./styles/main.css";
import { initialTier, motion, probeWebGL } from "./core/env";
import { markLayoutDirty, registerProgress, setReducedProgress } from "./core/scroll";
import { initCodeRain, initReveal, initTilt, initVideo } from "./ui/effects";
import { introWanted, playIntro } from "./ui/intro";
import { initNav } from "./ui/nav";
import { initRelease } from "./ui/release";
import type { StageApi } from "./three/stage";

const root = document.documentElement;

registerProgress();
setReducedProgress(motion.reduced);
motion.onChange((r) => setReducedProgress(r));
initNav();
initCodeRain();
initReveal();
initTilt();
initVideo();
initRelease();
document.fonts?.ready.then(markLayoutDirty);
addEventListener("load", markLayoutDirty);

// ---------- 3B sahne (ayrı parça olarak, gerektiğinde yüklenir) ----------
let stage: StageApi | null = null;
const canvas = document.getElementById("stage") as HTMLCanvasElement;
const noWebGL = () => {
  root.classList.add("no-webgl");
  canvas.remove();
};
// İlk boyamayı bekle: WebGL bağlamı ve gölgelendirici derlemesi sayfanın görünmesini geciktirmesin.
const afterPaint = new Promise<void>((r) => requestAnimationFrame(() => setTimeout(r, 0)));
const stageReady: Promise<StageApi | null> = afterPaint.then(async () => {
  const gl = probeWebGL();
  if (!gl.ok) {
    noWebGL();
    return null;
  }
  try {
    const m = await import("./three/stage");
    const s = await m.createStage(canvas, initialTier(gl.software));
    stage = s;
    if (introWanted()) s.setIntro(0);
    return s;
  } catch (err) {
    console.warn("3B sahne açılamadı, statik görünüme geçiliyor:", err);
    noWebGL();
    return null;
  }
});

playIntro(() => stage, stageReady);

document.querySelectorAll<HTMLButtonElement>("[data-explore]").forEach((b) =>
  b.addEventListener("click", async () => {
    const s = await stageReady;
    s?.enterExplore();
  }),
);

// ---------- Denemeler: sadece istenince yüklenir ----------
document.querySelectorAll<HTMLButtonElement>("[data-demo]").forEach((btn) =>
  btn.addEventListener("click", async () => {
    const host = btn.closest<HTMLElement>(".demo")!;
    host.classList.add("is-loading");
    btn.textContent = "Yükleniyor…";
    try {
      if (btn.dataset.demo === "draw") (await import("./demos/drawing")).mountDrawing(host);
      else (await import("./demos/flashcards")).mountFlashcards(host);
    } catch (e) {
      console.error(e);
      btn.textContent = "Yüklenemedi, tekrar dene";
    } finally {
      host.classList.remove("is-loading");
      markLayoutDirty();
    }
  }),
);
