// Küçük sayfa efektleri: belirme, depodan gerçek kod satırları, geliştirici kartı eğimi, video penceresi.
import { coarsePointer, motion } from "../core/env";

/** Kaydırınca belirme (sadece ekran dışındaki öğeler; ilk ekran hiç gizlenmez). */
export function initReveal(): void {
  const sel = [
    ".decision__inner > *", ".solution__head", ".pillars li", ".dev-card", ".developer .kicker",
    ".features__head", ".feature__copy > *", ".more__title", ".more__list li",
    ".play__head", ".demo", ".compare__head", ".qa > div", ".download > *", ".install__title", ".steps li", ".faq details",
  ].join(",");
  const els = [...document.querySelectorAll<HTMLElement>(sel)].filter((el) => el.getBoundingClientRect().top > innerHeight);
  if (motion.reduced || !("IntersectionObserver" in window)) return;
  const io = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (!e.isIntersecting) continue;
        const el = e.target as HTMLElement;
        const siblings = el.parentElement ? [...el.parentElement.children].indexOf(el) : 0;
        el.style.transitionDelay = `${Math.min(siblings, 6) * 70}ms`;
        el.classList.add("in");
        io.unobserve(el);
      }
    },
    { rootMargin: "0px 0px -8% 0px", threshold: 0.05 },
  );
  els.forEach((el) => {
    el.classList.add("reveal");
    io.observe(el);
  });
  // Karar bölümündeki boru hattı yarıya gelince yanar
  const decision = document.querySelector(".decision");
  if (decision) {
    new IntersectionObserver(([e]) => decision.classList.toggle("lit", e.isIntersecting), { rootMargin: "-40% 0px -40% 0px" }).observe(decision);
  }
  document.querySelectorAll(".demo").forEach((d) => io.observe(d));
}

// SouNote deposundan birebir alınmış satırlar (app/ ve .github/workflows/).
const CODE = [
  "fun add(x: Float, y: Float, pressure: Float) {",
  "    points[count * 3 + 2] = pressure",
  "if (isStylusTool(event.getToolType(0))) lastStylusTime = event.eventTime",
  "Stroke(tool, color, width, pressure, points, audioFile, audioMs)",
  "val box = (card.box + 1).coerceAtMost(5)",
  "card.copy(box = box, due = now + INTERVAL_DAYS[box - 1] * DAY)",
  "./gradlew assembleRelease --build-cache --parallel",
  "tag_name: build-${{ github.run_number }}",
  "fun sharp(xy: FloatArray, pressure: Float = 1f): FloatArray {",
  "if (!tmp.renameTo(f)) throw IOException(\"Kartlar kaydedilemedi\")",
  "BiometricManager.Authenticators.BIOMETRIC_WEAK",
  "make_latest: true",
];

export function initCodeRain(): void {
  document.querySelectorAll<HTMLElement>("[data-code]").forEach((box, bi) => {
    const cols = Math.max(2, Math.round(box.clientWidth / 340));
    const frag = document.createDocumentFragment();
    for (let c = 0; c < cols; c++) {
      const col = document.createElement("div");
      col.className = "code-rain__col";
      col.style.left = `${(c / cols) * 100 + 2}%`;
      col.style.setProperty("--speed", `${(c % 2 ? -1 : 1) * (80 + ((c * 37 + bi * 11) % 90))}px`);
      const lines: string[] = [];
      for (let i = 0; i < 26; i++) lines.push(CODE[(i * 5 + c * 3 + bi) % CODE.length]);
      lines.forEach((l, i) => {
        const line = i % 7 === 3 ? document.createElement("b") : document.createElement("span");
        line.textContent = l + "\n";
        col.append(line);
      });
      frag.append(col);
    }
    box.replaceChildren(frag);
  });
}

export function initTilt(): void {
  if (coarsePointer) return;
  document.querySelectorAll<HTMLElement>("[data-tilt]").forEach((card) => {
    card.addEventListener("pointermove", (e) => {
      if (motion.reduced) return;
      const r = card.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width;
      const y = (e.clientY - r.top) / r.height;
      card.classList.add("is-tilting");
      card.style.setProperty("--ry", `${(x - 0.5) * 7}deg`);
      card.style.setProperty("--rx", `${(0.5 - y) * 6}deg`);
      card.style.setProperty("--gx", `${x * 100}%`);
      card.style.setProperty("--gy", `${y * 100}%`);
    });
    card.addEventListener("pointerleave", () => {
      card.classList.remove("is-tilting");
      card.style.setProperty("--rx", "0deg");
      card.style.setProperty("--ry", "0deg");
    });
  });
}

export function initVideo(): void {
  const dlg = document.getElementById("video-dialog") as HTMLDialogElement | null;
  if (!dlg) return;
  const video = dlg.querySelector("video")!;
  document.querySelectorAll("[data-video]").forEach((b) =>
    b.addEventListener("click", () => {
      if (!video.src) video.src = video.dataset.src!;
      dlg.showModal();
      video.play().catch(() => {});
    }),
  );
  dlg.addEventListener("close", () => video.pause());
  dlg.addEventListener("click", (e) => {
    if (e.target === dlg) dlg.close();
  });
}
