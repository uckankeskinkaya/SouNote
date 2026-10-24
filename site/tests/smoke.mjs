// Uçtan uca duman testi: derlenmiş siteyi (../docs) /SouNote/ alt yolunda sunar ve gerçek Chromium'da gezer.
// Çalıştırma: npm run build && npm test   (Playwright gerekir: global ya da yerel kurulu olabilir)
// Ekran görüntüleri: tests/out/ (git'e girmez)
import { createServer } from "node:http";
import { readFile, mkdir } from "node:fs/promises";
import { execSync } from "node:child_process";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const DOCS = path.resolve(here, "../../docs");
const OUT = path.resolve(here, "out");
await mkdir(OUT, { recursive: true });

const require = createRequire(import.meta.url);
let pw;
try { pw = require("playwright"); } catch { pw = require(path.join(execSync("npm root -g").toString().trim(), "playwright")); }
const { chromium } = pw;

const TYPES = { ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".css": "text/css", ".png": "image/png", ".webp": "image/webp", ".jpg": "image/jpeg", ".mp4": "video/mp4", ".woff2": "font/woff2", ".json": "application/json", ".svg": "image/svg+xml" };
const server = createServer(async (req, res) => {
  const u = new URL(req.url, "http://x");
  if (!u.pathname.startsWith("/SouNote/")) { res.writeHead(404); return res.end("site /SouNote/ altında"); }
  let p = decodeURIComponent(u.pathname.slice("/SouNote/".length)) || "index.html";
  if (p.endsWith("/")) p += "index.html";
  try {
    const body = await readFile(path.join(DOCS, p));
    res.writeHead(200, { "Content-Type": TYPES[path.extname(p)] || "application/octet-stream" });
    res.end(body);
  } catch { res.writeHead(404); res.end(); }
});
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const BASE = `http://127.0.0.1:${server.address().port}/SouNote/`;

const GL = ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"];
const results = [];
const check = (name, ok, extra = "") => { results.push({ name, ok }); console.log(`${ok ? "✓" : "✗"} ${name}${extra ? ` — ${extra}` : ""}`); };
const errorsOf = (page) => { const e = []; page.on("pageerror", (x) => e.push(x.message)); page.on("console", (m) => m.type() === "error" && !/api\.github\.com|Failed to load resource/.test(m.text()) && e.push(m.text())); return e; };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const browser = await chromium.launch({ args: GL });

// ---------- 1. Masaüstü: açılış, bağlantılar, 3D İncele ----------
{
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const errs = errorsOf(page);
  await page.goto(BASE + "?debug", { waitUntil: "domcontentloaded" });
  check("açılış oynuyor", await page.evaluate(() => document.documentElement.classList.contains("has-intro") && !!document.getElementById("intro")));
  await sleep(700); await page.screenshot({ path: `${OUT}/intro-1.png` });
  await sleep(900); await page.screenshot({ path: `${OUT}/intro-2.png` });
  await sleep(800); await page.screenshot({ path: `${OUT}/intro-3.png` });
  check("indirme düğmesi açılış sırasında tıklanabilir", await page.evaluate(() => {
    const a = document.querySelector(".nav__cta"); const r = a.getBoundingClientRect();
    return document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2)?.closest(".nav__cta") === a;
  }));
  await sleep(1800);
  check("açılış ~3 sn içinde kalkıyor", await page.evaluate(() => !document.getElementById("intro") && !document.documentElement.classList.contains("has-intro")));
  await page.screenshot({ path: `${OUT}/hero.png` });

  const apk = await page.$$eval("[data-apk]", (as) => as.map((a) => a.href));
  check("APK bağlantıları doğru", apk.length >= 4 && apk.every((h) => h === "https://github.com/uckankeskinkaya/SouNote/releases/latest/download/SouNote.apk"), `${apk.length} bağlantı`);
  const gh = await page.$$eval('a[href*="github.com/uckankeskinkaya"]', (as) => [...new Set(as.map((a) => a.href))]);
  check("GitHub bağlantıları depo/profil", gh.every((h) => /^https:\/\/github\.com\/uckankeskinkaya(\/SouNote(\/(releases|issues)(\/.*)?)?)?$/.test(h)), gh.join(" "));
  const assets = await page.evaluate(() => performance.getEntriesByType("resource").map((e) => e.name).filter((n) => n.includes("127.0.0.1")));
  check("varlıklar /SouNote/ altından yükleniyor", assets.length > 5 && assets.every((n) => n.includes("/SouNote/")), `${assets.length} dosya`);
  check("WebGL sahnesi hazır", await page.evaluate(() => document.getElementById("stage")?.classList.contains("is-ready")));

  // 3D İncele
  await page.click("[data-explore]");
  await sleep(900);
  check("3D İncele açılıyor", await page.evaluate(() => !document.getElementById("explore-ui").hidden && document.getElementById("stage").classList.contains("is-exploring")));
  const ry0 = await page.evaluate(() => window.__sn.state.ry);
  await page.mouse.move(720, 400); await page.mouse.down(); await page.mouse.move(900, 430, { steps: 8 }); await page.mouse.up();
  await sleep(500);
  const ry1 = await page.evaluate(() => window.__sn.state.ry);
  check("sürükleyince döner", Math.abs(ry1 - ry0) > 0.5, `${ry0.toFixed(2)} → ${ry1.toFixed(2)}`);
  await page.click('[data-x="next"]'); await sleep(700);
  const label = await page.textContent("#explore-screen");
  await page.click('[data-x="orient"]'); await sleep(900);
  check("ekran ve yönelim değişiyor", (await page.evaluate(() => window.__sn.state.orient)) > 0.9, `ekran: ${label} → ${await page.textContent("#explore-screen")}`);
  await page.screenshot({ path: `${OUT}/explore-landscape.png` });
  await page.click('[data-x="orient"]'); await page.click('[data-x="flip"]'); await sleep(1100);
  await page.screenshot({ path: `${OUT}/explore-back.png` });
  await page.click('[data-x="flip"]'); await sleep(1300);
  const spots = await page.$$eval(".hotspot:not(.is-hidden)", (b) => b.length);
  check("bilgi noktaları görünüyor", spots > 0, `${spots} nokta`);
  if (spots) { await page.click(".hotspot:not(.is-hidden)"); await sleep(200); check("bilgi noktası açılıyor", await page.evaluate(() => !!document.querySelector(".hotspot-pop:not([hidden])"))); }
  await page.screenshot({ path: `${OUT}/explore-spots.png` });
  await page.keyboard.press("Escape"); await sleep(900);
  check("Esc ile çıkılıyor", await page.evaluate(() => document.getElementById("explore-ui").hidden && !document.getElementById("stage").classList.contains("is-exploring")));
  const rest = await page.evaluate(() => window.__sn.state);
  check("çıkınca sinematik poza dönüyor", Math.abs(rest.ry + 0.36) < 0.2 && rest.orient < 0.05, `ry=${rest.ry.toFixed(2)}`);
  await page.mouse.wheel(0, 900); await sleep(600);
  check("sonra sayfa normal kayıyor", (await page.evaluate(() => scrollY)) > 300);

  // geri kaydırma tutarlılığı: aynı konumda aynı poz
  const at = async (sel, k) => { await page.evaluate(([s, k]) => { const e = document.querySelector(s); scrollTo({ top: e.getBoundingClientRect().top + scrollY + k * innerHeight, behavior: "instant" }); }, [sel, k]); await sleep(1500); return page.evaluate(() => ({ ...window.__sn.state })); };
  const a1 = await at("#f-sinavlar", 0.3);
  await at("#indir", 0); await at("#top", 0);
  const a2 = await at("#f-sinavlar", 0.3);
  check("ileri/geri kaydırmada poz aynı", Math.abs(a1.cx - a2.cx) < 3 && Math.abs(a1.ry - a2.ry) < 0.02 && a1.screen === a2.screen, `${a1.screen} cx ${a1.cx.toFixed(0)}/${a2.cx.toFixed(0)}`);
  check("hızlı kaydırma sonrası doğru ekran", a2.screen === "exams");
  const land = await at("#f-dersler", 0.3);
  check("Dersler sahnesinde yatay", land.orient > 0.95 && land.screen === "courses-land");

  // ---------- Çizim denemesi (fare) ----------
  await at("#dene", 0.2);
  await page.click('[data-demo="draw"]');
  await page.waitForSelector(".draw canvas");
  await sleep(300);
  const box = await page.locator(".draw__paper").boundingBox();
  // kâğıdın sol üstüne göre CSS pikseli (ilk boyuta göre oran)
  const ink = (fx, fy) => page.evaluate(([px, py]) => { const c = document.querySelector(".draw canvas"); const k = c.width / c.clientWidth; return c.getContext("2d").getImageData(Math.round(px * k), Math.round(py * k), 1, 1).data[3]; }, [box.width * fx, box.height * fy]);
  await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.5); await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.8, box.y + box.height * 0.5, { steps: 20 }); await page.mouse.up();
  check("fareyle çizim", (await ink(0.5, 0.5)) > 0);
  await page.click('[data-act="undo"]');
  check("geri al", (await ink(0.5, 0.5)) === 0);
  await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.5); await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.8, box.y + box.height * 0.5, { steps: 20 }); await page.mouse.up();
  await page.click('[data-tool="eraser"]');
  await page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.3); await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.7, { steps: 12 }); await page.mouse.up();
  check("silgi", (await ink(0.5, 0.5)) === 0 && (await ink(0.3, 0.5)) > 0);
  await page.setViewportSize({ width: 1100, height: 800 }); await sleep(600);
  check("boyut değişince çizgi hizalı", (await ink(0.3, 0.5)) > 0 && (await ink(0.3, 0.6)) === 0);
  await page.screenshot({ path: `${OUT}/draw.png` });
  await page.click('[data-act="clear"]');
  check("temizle", (await ink(0.3, 0.5)) === 0);
  await page.click('[data-act="undo"]');
  check("temizle geri alınabiliyor", (await ink(0.3, 0.5)) > 0);
  const dl = page.waitForEvent("download", { timeout: 4000 }).catch(() => null);
  await page.click('[data-act="png"]');
  check("PNG dışa aktarma", !!(await dl)?.suggestedFilename()?.endsWith(".png"));
  await page.setViewportSize({ width: 1440, height: 900 });

  // ---------- Kart denemesi (fare) ----------
  await page.click('[data-demo="cards"]');
  await page.waitForSelector(".fc__card");
  await page.click(".fc__card[tabindex]"); await sleep(700);
  check("kart çevriliyor", await page.evaluate(() => document.querySelector(".fc__card[tabindex]").classList.contains("is-flipped")));
  await page.click('[data-a="repeat"]'); await sleep(900);
  check("tekrar et kartı sona atıyor", (await page.textContent('[data-s="learned"]')).startsWith("0 /"));
  const cb = await page.locator(".fc__card[tabindex]").boundingBox();
  await page.mouse.move(cb.x + cb.width / 2, cb.y + cb.height / 2); await page.mouse.down();
  await page.mouse.move(cb.x + cb.width / 2 + 260, cb.y + cb.height / 2, { steps: 10 }); await page.mouse.up();
  await sleep(1000);
  check("sağa kaydırma = öğrendim", (await page.textContent('[data-s="learned"]')).startsWith("1 /"));
  for (let i = 0; i < 5; i++) { await page.click('[data-a="learned"]'); await sleep(800); }
  check("ilerleme 6/6", (await page.getAttribute(".fc__progress", "aria-valuenow")) === "6");
  for (let i = 0; i < 3; i++) { await page.waitForSelector(".fc__opt:not([disabled])"); await page.click(".fc__opt >> nth=0"); await sleep(1100); }
  check("mini test ve bitiş", await page.isVisible(".fc__done"));
  await page.screenshot({ path: `${OUT}/cards-done.png` });
  await page.click('[data-a="restart"]'); await sleep(300);
  check("baştan başla", (await page.textContent('[data-s="learned"]')).startsWith("0 /") && (await page.isVisible(".fc__card")));
  check("masaüstünde hata yok", errs.length === 0, errs.join(" | "));
  await page.close();
}

// ---------- 2. Telefon: menü, dokunmayla çizim ve kaydırma, taşma ----------
{
  const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  const errs = errorsOf(page);
  await page.goto(BASE + "?intro=0", { waitUntil: "networkidle" });
  await sleep(1200);
  await page.click("#nav-toggle");
  check("mobil menü açılıyor", await page.isVisible("#nav-menu"));
  await page.screenshot({ path: `${OUT}/m-menu.png` });
  await page.click('#nav-menu a[href="#dene"]'); await sleep(2500);
  check("menü bağlantısı bölüme götürüyor", await page.evaluate(() => Math.abs(document.getElementById("dene").getBoundingClientRect().top) < 140) && !(await page.isVisible("#nav-menu")));
  await page.click('[data-demo="draw"]'); await page.waitForSelector(".draw canvas"); await sleep(300);
  const cdp = await ctx.newCDPSession(page);
  const touch = async (pts) => {
    await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: pts[0][0], y: pts[0][1] }] });
    for (const p of pts.slice(1)) await cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: p[0], y: p[1] }] });
    await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  };
  const b = await page.locator(".draw__paper").boundingBox();
  const y0 = await page.evaluate(() => scrollY);
  await touch(Array.from({ length: 12 }, (_, i) => [b.x + b.width * (0.2 + i * 0.05), b.y + b.height * 0.5]));
  await sleep(200);
  const alpha = await page.evaluate(() => { const c = document.querySelector(".draw canvas"); return c.getContext("2d").getImageData(c.width * 0.45, c.height * 0.5, 1, 1).data[3]; });
  check("dokunmayla çizim", alpha > 0);
  check("çizerken sayfa kaymıyor", Math.abs((await page.evaluate(() => scrollY)) - y0) < 2);
  // tuval dışında dikey kaydırma çalışıyor mu
  const hb = await page.locator(".play__head").boundingBox();
  await touch(Array.from({ length: 8 }, (_, i) => [200, hb.y + 120 - i * 30]));
  await sleep(600);
  check("tuval dışında sayfa kayıyor", (await page.evaluate(() => scrollY)) !== y0);
  await page.click('[data-act="full"]'); await sleep(300);
  check("tam ekran çizim", await page.evaluate(() => document.querySelector(".draw").classList.contains("is-full")));
  await page.screenshot({ path: `${OUT}/m-draw-full.png` });
  await page.click('[data-act="full"]');
  await page.click('[data-demo="cards"]'); await page.waitForSelector(".fc__card[tabindex]"); await sleep(300);
  await page.locator(".fc__card[tabindex]").scrollIntoViewIfNeeded();
  const c = await page.locator(".fc__card[tabindex]").boundingBox();
  await touch(Array.from({ length: 10 }, (_, i) => [c.x + c.width / 2 - i * 28, c.y + c.height / 2]));
  await sleep(1000);
  check("kartı sola kaydırma (dokunma)", (await page.textContent('[data-s="learned"]')).startsWith("0 /") && (await page.evaluate(() => !document.querySelector(".fc__card.is-leaving"))));
  await page.screenshot({ path: `${OUT}/m-cards.png` });
  for (const w of [360, 412, 768, 1024]) {
    await page.setViewportSize({ width: w, height: 860 }); await sleep(300);
    const ov = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
    check(`yatay taşma yok (${w}px)`, ov <= 0, `${ov}px`);
  }
  check("telefonda hata yok", errs.length === 0, errs.join(" | "));
  await ctx.close();
}

// ---------- 3. Hareket azaltılmış ----------
{
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, reducedMotion: "reduce" });
  const page = await ctx.newPage();
  const errs = errorsOf(page);
  await page.goto(BASE, { waitUntil: "networkidle" }); await sleep(800);
  check("hareket azaltılınca açılış yok", await page.evaluate(() => !document.documentElement.classList.contains("has-intro") && document.documentElement.classList.contains("reduce")));
  await page.evaluate(() => scrollTo({ top: document.getElementById("hikaye").getBoundingClientRect().top + scrollY + 300, behavior: "instant" })); await sleep(800);
  check("hikâye satırlarının hepsi görünür", await page.evaluate(() => [...document.querySelectorAll(".frictions li")].every((li) => parseFloat(getComputedStyle(li).opacity) > 0.95)));
  await page.screenshot({ path: `${OUT}/reduced-story.png` });
  check("hareket azaltılmışta hata yok", errs.length === 0, errs.join(" | "));
  await ctx.close();
}
await browser.close();

// ---------- 4. WebGL yok ----------
{
  const b2 = await chromium.launch({ args: ["--disable-webgl", "--disable-3d-apis"] });
  const page = await b2.newPage({ viewport: { width: 1280, height: 800 } });
  const errs = errorsOf(page);
  await page.goto(BASE + "?intro=0", { waitUntil: "networkidle" }); await sleep(800);
  check("WebGL yoksa statik görünüm", await page.evaluate(() => document.documentElement.classList.contains("no-webgl") && !document.getElementById("stage")));
  check("statik tablet görünür", await page.isVisible(".hero .tablet-fallback"));
  check("özellik ekran görüntüleri görünür", await page.isVisible("#f-kalem .feature__shot"));
  check("3D İncele gizli", !(await page.isVisible("[data-explore]")));
  await page.screenshot({ path: `${OUT}/nowebgl-hero.png` });
  await page.evaluate(() => scrollTo({ top: document.getElementById("f-dersler").getBoundingClientRect().top + scrollY + 200, behavior: "instant" })); await sleep(800);
  await page.screenshot({ path: `${OUT}/nowebgl-feature.png` });
  check("WebGL'siz hata yok", errs.length === 0, errs.join(" | "));
  await b2.close();
}

server.close();
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} geçti`);
process.exit(failed.length ? 1 : 0);
