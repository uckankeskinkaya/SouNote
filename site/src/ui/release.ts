// Yayın bilgisi: GitHub'daki en son sürümden okunur. Okunamazsa sayfadaki sabit bağlantılar
// (releases/latest/download/SouNote.apk her zaman en yeni APK'ya gider) olduğu gibi kalır.
import { LINKS } from "../config";

interface Asset { name: string; size: number; digest?: string }
interface Release { name?: string; tag_name?: string; body?: string; published_at?: string; assets?: Asset[] }

const CACHE_KEY = "sn-release";
const CACHE_MS = 30 * 60 * 1000;

async function fetchRelease(): Promise<Release | null> {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY);
    if (raw) {
      const c = JSON.parse(raw) as { t: number; d: Release };
      if (Date.now() - c.t < CACHE_MS) return c.d;
    }
  } catch { /* depolama kapalı olabilir */ }
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 8000);
  try {
    const r = await fetch(LINKS.latestApi, { signal: ctrl.signal, headers: { Accept: "application/vnd.github+json" } });
    if (!r.ok) return null;
    const d = (await r.json()) as Release;
    try { sessionStorage.setItem(CACHE_KEY, JSON.stringify({ t: Date.now(), d })); } catch { /* yok say */ }
    return d;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

const set = (key: string, text: string) =>
  document.querySelectorAll<HTMLElement>(`[data-release="${key}"]`).forEach((el) => (el.textContent = text));

export async function initRelease(): Promise<void> {
  const d = await fetchRelease();
  if (!d) return;
  const apk = d.assets?.find((a) => a.name === "SouNote.apk");
  const version = (d.name || "").replace(/^SouNote\s*/i, "").trim();
  if (version) {
    set("version", `Sürüm ${version}`);
    set("version-full", version);
  }
  if (apk) {
    const mb = (apk.size / 1048576).toLocaleString("tr-TR", { maximumFractionDigits: 1 });
    set("size", `${mb} MB`);
    set("size-full", `${mb} MB`);
    const sha = /^sha256:[0-9a-f]{64}$/.test(apk.digest || "") ? apk.digest!.slice(7) : null;
    if (sha) {
      set("hash", `SHA-256: ${sha}`);
      const vt = document.getElementById("vt-link") as HTMLAnchorElement | null;
      if (vt) vt.href = `https://www.virustotal.com/gui/file/${sha}`;
    }
  }
  if (d.published_at) {
    set("date", new Date(d.published_at).toLocaleDateString("tr-TR", { day: "numeric", month: "long", year: "numeric" }));
  }
  const lines = (d.body || "")
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l && !/^(Co-Authored-By|Claude-Session)/i.test(l));
  const notes = document.querySelector<HTMLElement>('[data-release="notes"]');
  if (notes && lines.length) {
    const b = document.createElement("b");
    b.textContent = `Bu sürümde: ${lines[0]}`;
    notes.replaceChildren(b, document.createTextNode(lines.slice(1, 4).map((l) => l.replace(/^[-•*]\s*/, "")).join(" · ")));
    notes.hidden = false;
  }
}
