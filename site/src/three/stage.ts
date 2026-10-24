// Kalıcı WebGL sahnesi: tek tuval, tek tablet, bütün sayfa boyunca yaşar.
// Poz kaynağı sırası: açılış (intro) → kaydırma koreografisi → keşif modu ("3D İncele"). Hepsi aynı
// sönümlenmiş poz durumuna yazar; dönüşüm özelliklerine başka hiçbir şey dokunmaz.
import * as THREE from "three";
import { RoomEnvironment } from "three/examples/jsm/environments/RoomEnvironment.js";
import { lowerTier, motion, TIER_SETTINGS, type Tier } from "../core/env";
import { onFrame, viewport } from "../core/scroll";
import { SCREENS, screenById, type ScreenDef } from "../config";
import { Choreography, fitPx, type Pose } from "./choreography";
import { Particles } from "./particles";
import { loadImage, TabletScreen } from "./screen";
import { createTablet, DIM } from "./tablet";

const FOV = 28;
const CAM_Z = 10;
const TAU = Math.PI * 2;

type Num = Exclude<keyof Pose, "screen">;
const NUM_KEYS: Num[] = ["cx", "cy", "px", "rx", "ry", "rz", "orient", "glow", "vis"];

function glowTexture(): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = c.height = 256;
  const g = c.getContext("2d")!;
  const r = g.createRadialGradient(128, 128, 0, 128, 128, 128);
  r.addColorStop(0, "rgba(90,130,255,0.9)");
  r.addColorStop(0.35, "rgba(110,92,240,0.45)");
  r.addColorStop(0.7, "rgba(60,90,220,0.12)");
  r.addColorStop(1, "rgba(0,0,0,0)");
  g.fillStyle = r;
  g.fillRect(0, 0, 256, 256);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

export interface StageApi {
  enterExplore(): void;
  exitExplore(): void;
  readonly exploring: boolean;
  /** Açılış: tablet logonun hizasından geri çekilir. p: 0..1 */
  setIntro(p: number | null): void;
  /** Açılış ekranındaki logonun o anki ekran konumu (CSS px). */
  splashLogoRect(): { x: number; y: number; size: number } | null;
  readonly tier: Tier;
}

export async function createStage(canvas: HTMLCanvasElement, startTier: Tier): Promise<StageApi> {
  let tier = startTier;
  let q = TIER_SETTINGS[tier];
  const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: tier !== "low", powerPreference: "high-performance", premultipliedAlpha: true });
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  let dpr = Math.min(devicePixelRatio || 1, q.dpr);
  renderer.setPixelRatio(dpr);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(FOV, 1, 0.1, 60);
  camera.position.set(0, 0, CAM_Z);

  let pmrem: THREE.PMREMGenerator | null = null;
  if (q.env) {
    pmrem = new THREE.PMREMGenerator(renderer);
    const room = new RoomEnvironment();
    scene.environment = pmrem.fromScene(room, 0.04).texture;
    scene.environmentIntensity = 0.55;
    room.dispose();
  }
  scene.add(new THREE.HemisphereLight(0x9db4ff, 0x050814, q.env ? 0.35 : 1.0));
  const key = new THREE.DirectionalLight(0xffffff, q.env ? 1.4 : 2.2);
  key.position.set(3, 5, 7);
  const rimBlue = new THREE.DirectionalLight(0x4a7dff, 3.2);
  rimBlue.position.set(-6, 2.5, -3);
  const rimViolet = new THREE.DirectionalLight(0x8b6cf6, 2.8);
  rimViolet.position.set(6, -2, -4);
  scene.add(key, rimBlue, rimViolet);

  // Sahne ağacı: rig (konum/ölçek) → tablet (dönüş) ; parıltı rig'de, tabletle dönmez.
  const rig = new THREE.Group();
  const tiltGroup = new THREE.Group();
  tiltGroup.rotation.order = "YXZ";
  rig.add(tiltGroup);
  scene.add(rig);
  const tablet = createTablet(q);
  tiltGroup.add(tablet.root);
  const screen = new TabletScreen(Math.min(q.texture, renderer.capabilities.maxTextureSize), Math.min(8, renderer.capabilities.getMaxAnisotropy()));
  tablet.screenMount.add(screen.group);

  const glowMat = new THREE.MeshBasicMaterial({ map: glowTexture(), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, opacity: 0.5 });
  const glow = new THREE.Mesh(new THREE.PlaneGeometry(4.2, 4.2), glowMat);
  glow.position.z = -0.9;
  glow.renderOrder = -2;
  rig.add(glow);

  const particles = new Particles(TIER_SETTINGS.high.particles, dpr);
  particles.setCount(q.particles);
  scene.add(particles.points);

  // Logolar (ekrandaki açılış görseli ve arka kapak)
  await document.fonts?.load('600 64px "Space Grotesk Variable"').catch(() => undefined);
  const [tile, symbol] = await Promise.all([loadImage("brand/logo-a-512.png"), loadImage("brand/logo-a-symbol-512.png")]).catch(() => [null, null]);
  if (tile) screen.setLogo(tile);
  if (symbol) tablet.setBackLogo(symbol);
  screen.set("splash", true);

  // Gölgelendiricileri arka planda derle (destekleniyorsa KHR_parallel_shader_compile ile), ilk karede takılma olmasın.
  await renderer.compileAsync(scene, camera).catch(() => undefined);

  const choreo = new Choreography();
  const state = {} as Pose;
  let hasState = false;
  let introP: number | null = null;
  let exploring = false;
  let exploreT = 0;
  let lastSegment = -1;
  let dip = 0; // hareket azaltıldığında sahne geçişlerinde kısa karartma

  // ---------- Keşif modu ----------
  const ui = document.getElementById("explore-ui")!;
  const backdrop = document.getElementById("explore-backdrop")!;
  const spotsBox = document.getElementById("hotspots")!;
  const screenLabel = document.getElementById("explore-screen")!;
  const orientBtn = ui.querySelector<HTMLButtonElement>('[data-x="orient"]')!;
  const spotsBtn = ui.querySelector<HTMLButtonElement>('[data-x="spots"]')!;
  const user = { yaw: 0, pitch: 0, vy: 0, vp: 0, flipTarget: 0 };
  let spotsOn = true;
  let exScreen = "home";
  let exOrient = 0;
  let returnFocus: HTMLElement | null = null;
  let wheelAcc = 0;
  const explorable = SCREENS.filter((s) => s.id !== "onboarding");

  const setExScreen = (id: string) => {
    const def = screenById(id);
    exScreen = def.id;
    exOrient = def.orientation === "landscape" ? 1 : 0;
    screenLabel.textContent = def.label;
    orientBtn.setAttribute("aria-pressed", String(exOrient === 1));
    orientBtn.textContent = exOrient ? "Dikey" : "Yatay";
    buildSpots(def);
  };

  let spotEls: { btn: HTMLButtonElement; u: number; v: number; pop: HTMLElement }[] = [];
  function buildSpots(def: ScreenDef) {
    spotsBox.replaceChildren();
    spotEls = (def.hotspots ?? []).map((h, i) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "hotspot is-hidden";
      btn.textContent = String(i + 1);
      btn.setAttribute("aria-expanded", "false");
      btn.setAttribute("aria-label", `Bilgi noktası ${i + 1}: ${h.title}`);
      const pop = document.createElement("div");
      pop.className = "hotspot-pop";
      pop.hidden = true;
      pop.id = `spot-pop-${i}`;
      const b = document.createElement("b");
      b.textContent = h.title;
      pop.append(b, document.createTextNode(h.text));
      btn.setAttribute("aria-controls", pop.id);
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const open = btn.getAttribute("aria-expanded") !== "true";
        spotEls.forEach((s) => { s.btn.setAttribute("aria-expanded", "false"); s.pop.hidden = true; });
        btn.setAttribute("aria-expanded", String(open));
        pop.hidden = !open;
      });
      spotsBox.append(btn, pop);
      return { btn, u: h.u, v: h.v, pop };
    });
  }

  const enterExplore = () => {
    if (exploring) return;
    exploring = true;
    returnFocus = document.activeElement as HTMLElement;
    const cur = screen.currentId;
    setExScreen(cur === "splash" || cur === "onboarding" ? "home" : cur);
    user.yaw = state.ry ?? 0;
    user.pitch = state.rx ?? 0;
    user.flipTarget = 0;
    user.vy = user.vp = 0;
    wheelAcc = 0;
    ui.hidden = false;
    backdrop.hidden = false;
    canvas.classList.add("is-exploring");
    document.documentElement.classList.add("exploring");
    ui.querySelector<HTMLElement>("button")?.focus({ preventScroll: true });
  };
  const exitExplore = () => {
    if (!exploring) return;
    exploring = false;
    ui.hidden = true;
    backdrop.hidden = true;
    canvas.classList.remove("is-exploring", "is-dragging");
    document.documentElement.classList.remove("exploring");
    spotsBox.replaceChildren();
    spotEls = [];
    returnFocus?.focus({ preventScroll: true });
  };

  const step = 0.45;
  ui.addEventListener("click", (e) => {
    const b = (e.target as HTMLElement).closest<HTMLButtonElement>("[data-x]");
    if (!b) return;
    const idx = explorable.findIndex((s) => s.id === exScreen);
    switch (b.dataset.x) {
      case "left": user.yaw -= step; break;
      case "right": user.yaw += step; break;
      case "up": user.pitch = Math.max(-0.9, user.pitch - 0.25); break;
      case "down": user.pitch = Math.min(0.9, user.pitch + 0.25); break;
      case "flip": {
        // tam öne ya da tam arkaya otur
        const front = Math.round(user.yaw / TAU) * TAU;
        user.yaw = Math.abs(user.yaw - front) < Math.PI / 2 ? front + Math.PI : front;
        user.pitch = 0;
        user.vy = user.vp = 0;
        break;
      }
      case "orient": setExScreen(exOrient ? "home" : "courses-land"); break;
      case "prev": setExScreen(explorable[(idx - 1 + explorable.length) % explorable.length].id); break;
      case "next": setExScreen(explorable[(idx + 1) % explorable.length].id); break;
      case "spots":
        spotsOn = !spotsOn;
        spotsBtn.setAttribute("aria-pressed", String(spotsOn));
        break;
      case "close": exitExplore(); break;
    }
  });
  document.addEventListener("keydown", (e) => {
    if (!exploring) return;
    if (e.key === "Escape") { exitExplore(); return; }
    const t = e.target as HTMLElement;
    if (t.closest(".hotspot-pop")) return;
    const m: Record<string, () => void> = {
      ArrowLeft: () => (user.yaw -= 0.3), ArrowRight: () => (user.yaw += 0.3),
      ArrowUp: () => (user.pitch = Math.max(-0.9, user.pitch - 0.2)), ArrowDown: () => (user.pitch = Math.min(0.9, user.pitch + 0.2)),
    };
    if (m[e.key]) { e.preventDefault(); m[e.key](); }
  });
  // Sürükleme: sadece keşif modunda, sadece tuvalin üstünde. Sayfa kaydırması başka hiçbir yerde engellenmez.
  let drag: { id: number; x: number; y: number; t: number } | null = null;
  canvas.addEventListener("pointerdown", (e) => {
    if (!exploring) return;
    drag = { id: e.pointerId, x: e.clientX, y: e.clientY, t: performance.now() };
    canvas.setPointerCapture(e.pointerId);
    canvas.classList.add("is-dragging");
    user.vy = user.vp = 0;
  });
  canvas.addEventListener("pointermove", (e) => {
    if (!drag || e.pointerId !== drag.id) return;
    const now = performance.now();
    const dt = Math.max(1, now - drag.t) / 1000;
    const dx = (e.clientX - drag.x) * 0.008;
    const dy = (e.clientY - drag.y) * 0.006;
    user.yaw += dx;
    user.pitch = Math.max(-0.9, Math.min(0.9, user.pitch + dy));
    user.vy = dx / dt;
    user.vp = dy / dt;
    drag.x = e.clientX; drag.y = e.clientY; drag.t = now;
  });
  const endDrag = (e: PointerEvent) => {
    if (!drag || e.pointerId !== drag.id) return;
    drag = null;
    canvas.classList.remove("is-dragging");
  };
  canvas.addEventListener("pointerup", endDrag);
  canvas.addEventListener("pointercancel", endDrag);
  backdrop.addEventListener("click", exitExplore);
  addEventListener("wheel", (e) => {
    if (!exploring) return;
    wheelAcc += Math.abs(e.deltaY);
    if (wheelAcc > 240) exitExplore();
  }, { passive: true });

  // ---------- Yardımcılar ----------
  const v3 = new THREE.Vector3();
  const camDir = new THREE.Vector3();
  const nrm = new THREE.Vector3();
  const pxToWorld = () => (2 * CAM_Z * Math.tan(THREE.MathUtils.degToRad(FOV / 2))) / Math.max(1, viewport.h);

  const project = (local: THREE.Vector3) => {
    v3.copy(local);
    tablet.root.localToWorld(v3);
    v3.project(camera);
    const r = canvas.getBoundingClientRect();
    return { x: (v3.x * 0.5 + 0.5) * r.width + r.left, y: (-v3.y * 0.5 + 0.5) * r.height + r.top, z: v3.z };
  };
  const frontFacing = () => {
    nrm.set(0, 0, 1).applyQuaternion(tablet.root.getWorldQuaternion(new THREE.Quaternion()));
    tablet.root.getWorldPosition(camDir);
    camDir.sub(camera.position).negate().normalize();
    return nrm.dot(camDir);
  };

  const splashLogoRect = () => {
    const l = screen.splashLogoLocal();
    const zf = DIM.t / 2;
    const c = project(new THREE.Vector3(l.x, l.y, zf));
    const e = project(new THREE.Vector3(l.x + l.size / 2, l.y, zf));
    return { x: c.x, y: c.y, size: Math.hypot(e.x - c.x, e.y - c.y) * 2 };
  };

  function introStartPose(target: Pose): Pose {
    const domSize = Math.min(Math.min(viewport.w, viewport.h) * 0.38, 280);
    const l = screen.splashLogoLocal();
    const px = domSize / l.size;
    return { ...target, cx: viewport.w / 2, cy: viewport.h / 2 + l.y * px, px, rx: 0, ry: 0, rz: 0, orient: 0, glow: 0.9, vis: 1, screen: "splash" };
  }

  function explorePose(): Pose {
    const uiH = ui.offsetHeight || 180;
    const top = 76, bottom = viewport.h - uiH - 28;
    const h = Math.max(160, bottom - top);
    const w = viewport.w * 0.9;
    // her açıda sığsın: uzun kenar hem genişliğe hem yüksekliğe sığmalı (yakın uçtaki perspektif büyümesi için pay)
    const px = Math.min(fitPx(w, h, exOrient), Math.min(w, h) / DIM.h) * 0.86;
    return { cx: viewport.w / 2, cy: top + h / 2, px, rx: user.pitch, ry: user.yaw, rz: 0, orient: exOrient, glow: 0.9, vis: 1, screen: exScreen };
  }

  const ease = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);

  // ---------- Uyarlanır kalite ----------
  let fpsAcc = 0, fpsFrames = 0, slowWindows = 0, lastDowngrade = 0;
  function monitor(dt: number, now: number) {
    fpsAcc += dt; fpsFrames++;
    if (fpsAcc < 3) return;
    const fps = fpsFrames / fpsAcc;
    fpsAcc = 0; fpsFrames = 0;
    slowWindows = fps < 42 ? slowWindows + 1 : 0;
    if (slowWindows >= 2 && tier !== "low" && now - lastDowngrade > 8) {
      tier = lowerTier(tier);
      q = TIER_SETTINGS[tier];
      lastDowngrade = now;
      slowWindows = 0;
      dpr = Math.min(devicePixelRatio || 1, q.dpr);
      renderer.setPixelRatio(dpr);
      particles.setPixelRatio(dpr);
      particles.setCount(q.particles);
      document.documentElement.dataset.tier = tier;
      lastW = 0; // yeniden boyutlandır
    }
  }

  // ---------- Döngü ----------
  let lastW = 0, lastH = 0;
  let wasVisible = false;
  const reducedNow = () => motion.reduced;
  document.documentElement.dataset.tier = tier;

  onFrame((dt, time) => {
    // boyut
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (w !== lastW || h !== lastH) {
      lastW = w; lastH = h;
      renderer.setSize(w, h, false);
      camera.aspect = w / Math.max(1, h);
      camera.updateProjectionMatrix();
    }
    const reduced = reducedNow();

    // hedef poz
    let target = choreo.evaluate(reduced);
    if (reduced && choreo.segment !== lastSegment) {
      if (lastSegment !== -1) dip = 1;
      lastSegment = choreo.segment;
    }
    if (introP !== null) {
      const s = introStartPose(target);
      const e = ease(Math.min(1, Math.max(0, introP)));
      const mixed = { ...target };
      for (const k of NUM_KEYS) mixed[k] = s[k] + (target[k] - s[k]) * e;
      mixed.screen = "splash";
      target = mixed;
    }
    exploreT = Math.min(1, Math.max(0, exploreT + (exploring ? dt : -dt) / 0.6));
    if (exploring) {
      if (!drag) {
        user.yaw += user.vy * dt;
        user.pitch = Math.max(-0.9, Math.min(0.9, user.pitch + user.vp * dt));
        const f = Math.exp(-dt * 4);
        user.vy *= f; user.vp *= f;
      }
    }
    if (exploreT > 0) {
      const ex = explorePose();
      const e = ease(exploreT);
      const mixed = { ...target };
      for (const k of NUM_KEYS) mixed[k] = target[k] + (ex[k] - target[k]) * e;
      mixed.screen = exploreT > 0.4 ? ex.screen : target.screen;
      target = mixed;
    }

    // sönümleme
    if (!hasState || reduced) {
      Object.assign(state, target);
      if (!hasState && exploring) user.yaw = target.ry;
      hasState = true;
    } else {
      const k = exploring ? 10 : 7;
      const a = 1 - Math.exp(-dt * k);
      // keşiften dönerken fazladan tam turları atla (en kısa yoldan dön)
      if (!exploring) {
        const d = state.ry - target.ry;
        state.ry = target.ry + (((d + Math.PI) % TAU) + TAU) % TAU - Math.PI;
      }
      for (const key of NUM_KEYS) state[key] += (target[key] - state[key]) * a;
      // konum ve ölçek sayfayla birlikte akmalı: büyük farklarda daha hızlı yakala
      const ak = 1 - Math.exp(-dt * (exploring ? 10 : 14));
      state.cx += (target.cx - state.cx) * ak;
      state.cy += (target.cy - state.cy) * ak;
      state.screen = target.screen;
    }

    // görünürlük ve kalite
    dip = Math.max(0, dip - dt / 0.35);
    const vis = state.vis * (1 - Math.sin(Math.PI * Math.min(1, dip)) * 0.85);
    const visible = vis > 0.004 || exploring || introP !== null;
    canvas.style.setProperty("--vis", vis.toFixed(3));
    document.querySelector<HTMLElement>(".finale")?.style.setProperty("--glow", (state.glow * state.vis).toFixed(3));
    if (!visible) {
      if (wasVisible) { renderer.clear(); wasVisible = false; }
      return;
    }
    wasVisible = true;
    if (introP === null && !exploring) monitor(dt, time);

    // dönüşümler
    const ptw = pxToWorld();
    const s = state.px * ptw;
    rig.position.set((state.cx - viewport.w / 2) * ptw, (viewport.h / 2 - state.cy) * ptw, 0);
    rig.scale.setScalar(s);
    const idle = reduced || exploring ? 0 : 1;
    rig.position.y += Math.sin(time * 0.9) * 0.02 * s * idle;
    tiltGroup.rotation.set(state.rx + Math.sin(time * 0.6) * 0.012 * idle, state.ry + Math.sin(time * 0.45) * 0.02 * idle, state.rz - (Math.PI / 2) * state.orient);
    glowMat.opacity = Math.min(1, state.glow) * 0.55;
    glow.scale.setScalar(1 + (state.glow - 0.6) * 0.3);
    rimBlue.intensity = 2.2 + state.glow * 1.6;
    rimViolet.intensity = 1.8 + state.glow * 1.6;

    if (screen.currentId !== state.screen) screen.set(state.screen, reduced);
    screen.update(dt);
    particles.update(time, viewport.y, Math.min(1, 0.35 + state.glow * 0.65) * (introP !== null ? 0.6 : 1));

    renderer.render(scene, camera);

    // bilgi noktaları
    if (spotEls.length) {
      const show = exploring && spotsOn && exploreT > 0.95 && screen.currentId === exScreen && frontFacing() > 0.3;
      const rect = screen.currentRect;
      const def = screenById(exScreen);
      const zf = DIM.t / 2 + 0.004;
      for (const sp of spotEls) {
        let cu: number, cv: number;
        if (def.orientation === "landscape") { cu = rect.x + sp.v * rect.w; cv = rect.y + (1 - sp.u) * rect.h; }
        else { cu = rect.x + sp.u * rect.w; cv = rect.y + sp.v * rect.h; }
        const p = project(new THREE.Vector3((cu - 0.5) * DIM.screenW, (0.5 - cv) * DIM.screenH, zf));
        sp.btn.style.transform = `translate(${p.x.toFixed(1)}px, ${p.y.toFixed(1)}px)`;
        sp.btn.classList.toggle("is-hidden", !show);
        sp.btn.tabIndex = show ? 0 : -1;
        if (!show) { sp.pop.hidden = true; sp.btn.setAttribute("aria-expanded", "false"); }
        else if (!sp.pop.hidden) {
          const left = Math.min(viewport.w - 280, Math.max(12, p.x + 24));
          sp.pop.style.transform = `translate(${left.toFixed(0)}px, ${Math.max(70, p.y - 30).toFixed(0)}px)`;
        }
      }
    }
  });

  canvas.addEventListener("webglcontextlost", (e) => {
    e.preventDefault();
    document.documentElement.classList.add("no-webgl");
    canvas.remove();
  });

  canvas.classList.add("is-ready");
  if (/[?&]debug/.test(location.search)) Object.assign(window, { __sn: { state, rig, camera, renderer, choreo } });

  return {
    enterExplore,
    exitExplore,
    get exploring() { return exploring; },
    setIntro(p) { introP = p; },
    splashLogoRect,
    get tier() { return tier; },
  };
}
