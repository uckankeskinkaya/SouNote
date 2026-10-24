// Uyarlanır gezinme: masaüstünde yüzen cam çubuk, dar ekranda açılır "ada" menüsü.
import { onFrame, viewport } from "../core/scroll";

export function initNav(): void {
  const nav = document.getElementById("nav")!;
  const toggle = document.getElementById("nav-toggle") as HTMLButtonElement;
  const menu = document.getElementById("nav-menu")!;

  const setOpen = (open: boolean) => {
    toggle.setAttribute("aria-expanded", String(open));
    toggle.setAttribute("aria-label", open ? "Menüyü kapat" : "Menüyü aç");
    menu.hidden = !open;
    nav.classList.toggle("is-open", open);
    if (open) menu.querySelector<HTMLElement>("a")?.focus();
  };
  toggle.addEventListener("click", () => setOpen(menu.hidden));
  menu.addEventListener("click", (e) => {
    if ((e.target as HTMLElement).closest("a")) setOpen(false);
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !menu.hidden) {
      setOpen(false);
      toggle.focus();
    }
  });
  document.addEventListener("click", (e) => {
    if (!menu.hidden && !nav.contains(e.target as Node)) setOpen(false);
  });
  matchMedia("(min-width: 920px)").addEventListener("change", () => setOpen(false));

  let compact = false;
  onFrame(() => {
    const c = viewport.y > 40;
    if (c !== compact) {
      compact = c;
      nav.classList.toggle("is-compact", c);
    }
  });

  // Etkin bölümü işaretle
  const links = [...document.querySelectorAll<HTMLAnchorElement>(".nav__links a")];
  const targets = links.map((a) => document.querySelector(a.hash)).filter(Boolean) as Element[];
  const io = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (!e.isIntersecting) continue;
        links.forEach((a) => a.setAttribute("aria-current", String(a.hash === `#${e.target.id}`)));
      }
    },
    { rootMargin: "-45% 0px -50% 0px" },
  );
  targets.forEach((t) => io.observe(t));
}
