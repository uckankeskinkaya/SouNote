// Prosedürel tablet: titanyum-lacivert gövde, ince çerçeve, cam ekran, arkada SouNote logosu.
// Model dikey (portrait) kurulur; yatay duruş, ekran normali etrafında -90° döndürülerek elde edilir.
import * as THREE from "three";
import type { TIER_SETTINGS } from "../core/env";

export const DIM = {
  w: 1.0,
  h: 1.5496,
  t: 0.052,
  r: 0.075,
  bezel: 0.042,
  get screenW() { return this.w - 2 * this.bezel; },
  get screenH() { return this.h - 2 * this.bezel; },
};

function roundedRect(w: number, h: number, r: number): THREE.Shape {
  const s = new THREE.Shape();
  const x = -w / 2, y = -h / 2;
  s.moveTo(x + r, y);
  s.lineTo(x + w - r, y);
  s.quadraticCurveTo(x + w, y, x + w, y + r);
  s.lineTo(x + w, y + h - r);
  s.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  s.lineTo(x + r, y + h);
  s.quadraticCurveTo(x, y + h, x, y + h - r);
  s.lineTo(x, y + r);
  s.quadraticCurveTo(x, y, x + r, y);
  return s;
}

/** Arka kapaktaki kabartma logo: simgenin alfa kanalı gümüş tonla doldurulur. */
function backLogoTexture(symbol: HTMLImageElement | null): THREE.CanvasTexture {
  const c = document.createElement("canvas");
  c.width = 512; c.height = 640;
  const g = c.getContext("2d")!;
  if (symbol) {
    g.drawImage(symbol, 96, 40, 320, 320);
    g.globalCompositeOperation = "source-in";
    const grad = g.createLinearGradient(96, 40, 416, 360);
    grad.addColorStop(0, "#e6ecff");
    grad.addColorStop(0.5, "#9fb2e6");
    grad.addColorStop(1, "#c9b9ff");
    g.fillStyle = grad;
    g.fillRect(0, 0, 512, 400);
    g.globalCompositeOperation = "source-over";
  }
  g.fillStyle = "rgba(220,228,255,0.9)";
  g.font = '600 76px "Space Grotesk Variable", "Space Grotesk", sans-serif';
  g.textAlign = "center";
  g.fillText("SouNote", 256, 470);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 4;
  return t;
}

export interface Tablet {
  root: THREE.Group;
  /** Ekran katmanlarının ekleneceği grup (ön yüzün hemen üstü). */
  screenMount: THREE.Group;
  setBackLogo(img: HTMLImageElement): void;
  dispose(): void;
}

export function createTablet(q: (typeof TIER_SETTINGS)["high"]): Tablet {
  const root = new THREE.Group();
  const disposables: { dispose(): void }[] = [];
  const keep = <T extends { dispose(): void }>(x: T) => (disposables.push(x), x);

  const bevel = 0.013;
  const depth = DIM.t - 2 * bevel;
  const outline = roundedRect(DIM.w - 2 * bevel, DIM.h - 2 * bevel, DIM.r - bevel);
  const bodyGeo = keep(new THREE.ExtrudeGeometry(outline, {
    depth, bevelEnabled: true, bevelThickness: bevel, bevelSize: bevel, bevelSegments: q.physical ? 6 : 3, curveSegments: q.physical ? 14 : 8,
  }));
  bodyGeo.translate(0, 0, -depth / 2);

  const Std = q.physical ? THREE.MeshPhysicalMaterial : THREE.MeshStandardMaterial;
  const chassis = keep(new Std({ color: 0x29324d, metalness: 0.92, roughness: 0.3 }));
  if (chassis instanceof THREE.MeshPhysicalMaterial) { chassis.clearcoat = 0.5; chassis.clearcoatRoughness = 0.25; }
  const backMat = keep(new Std({ color: 0x18213b, metalness: 0.7, roughness: 0.48 }));
  const body = new THREE.Mesh(bodyGeo, [backMat, chassis]);
  root.add(body);

  // Ön cam (çerçeve dahil tek parça, siyah)
  const front = new THREE.Mesh(keep(new THREE.ShapeGeometry(roundedRect(DIM.w - 0.006, DIM.h - 0.006, DIM.r - 0.003), 12)),
    keep(new THREE.MeshStandardMaterial({ color: 0x04060c, roughness: 0.18, metalness: 0.2 })));
  front.position.z = DIM.t / 2 + 0.0006;
  root.add(front);

  // Ön kamera
  const cam = new THREE.Mesh(keep(new THREE.CircleGeometry(0.0085, 20)), keep(new THREE.MeshStandardMaterial({ color: 0x0b1226, roughness: 0.1, metalness: 0.6 })));
  cam.position.set(0, DIM.h / 2 - DIM.bezel / 2, DIM.t / 2 + 0.0012);
  root.add(cam);

  const screenMount = new THREE.Group();
  screenMount.position.z = DIM.t / 2 + 0.0014;
  root.add(screenMount);

  // Cam yansıması: siyah, toplamalı karışım; sadece ortam yansımasını ekler (Fresnel ile kenarda güçlenir).
  if (q.glass) {
    const glass = new THREE.Mesh(keep(new THREE.ShapeGeometry(roundedRect(DIM.w - 0.006, DIM.h - 0.006, DIM.r - 0.003), 12)),
      keep(new THREE.MeshStandardMaterial({ color: 0x000000, roughness: 0.05, metalness: 0, envMapIntensity: 1.6, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false })));
    glass.position.z = DIM.t / 2 + 0.0032;
    glass.renderOrder = 5;
    root.add(glass);
  }

  // Arka kapak paneli (hafif mat) + kamera adası + logo
  const backPanel = new THREE.Mesh(keep(new THREE.ShapeGeometry(roundedRect(DIM.w - 0.01, DIM.h - 0.01, DIM.r - 0.005), 12)), backMat);
  backPanel.rotation.y = Math.PI;
  backPanel.position.z = -DIM.t / 2 - 0.0006;
  root.add(backPanel);

  const bumpGeo = keep(new THREE.ExtrudeGeometry(roundedRect(0.2, 0.2, 0.06), { depth: 0.012, bevelEnabled: true, bevelThickness: 0.004, bevelSize: 0.004, bevelSegments: 3, curveSegments: 10 }));
  const bump = new THREE.Mesh(bumpGeo, chassis);
  bump.rotation.y = Math.PI;
  bump.position.set(DIM.w / 2 - 0.17, DIM.h / 2 - 0.17, -DIM.t / 2);
  root.add(bump);
  const lensMat = keep(new THREE.MeshPhysicalMaterial({ color: 0x050810, roughness: 0.05, metalness: 0.4, clearcoat: 1 }));
  const ringMat = keep(new THREE.MeshStandardMaterial({ color: 0x9aa7c7, roughness: 0.25, metalness: 1 }));
  const lensGeo = keep(new THREE.CylinderGeometry(0.034, 0.034, 0.006, 28));
  const ringGeo = keep(new THREE.TorusGeometry(0.036, 0.004, 8, 28));
  for (const [x, y] of [[0.04, 0.04], [-0.04, -0.04]]) {
    const lens = new THREE.Mesh(lensGeo, lensMat);
    lens.rotation.x = Math.PI / 2;
    lens.position.set(bump.position.x - x, bump.position.y + y, -DIM.t / 2 - 0.02);
    root.add(lens);
    const ring = new THREE.Mesh(ringGeo, ringMat);
    ring.position.set(lens.position.x, lens.position.y, lens.position.z - 0.003);
    root.add(ring);
  }
  const flash = new THREE.Mesh(keep(new THREE.CircleGeometry(0.012, 16)), keep(new THREE.MeshBasicMaterial({ color: 0xf2e6c9 })));
  flash.rotation.y = Math.PI;
  flash.position.set(bump.position.x - 0.045, bump.position.y - 0.04, -DIM.t / 2 - 0.0175);
  root.add(flash);

  const logoMat = keep(new THREE.MeshStandardMaterial({ transparent: true, metalness: 0.85, roughness: 0.22, color: 0xffffff, depthWrite: false }));
  const logo = new THREE.Mesh(keep(new THREE.PlaneGeometry(0.42, 0.525)), logoMat);
  logo.rotation.y = Math.PI;
  logo.position.set(0, 0.02, -DIM.t / 2 - 0.0016);
  root.add(logo);
  const setBackLogo = (img: HTMLImageElement) => {
    logoMat.map?.dispose();
    logoMat.map = backLogoTexture(img);
    logoMat.needsUpdate = true;
  };
  logoMat.map = backLogoTexture(null);

  // Tuşlar ve hoparlör delikleri
  const keyGeo = keep(new THREE.BoxGeometry(0.008, 0.11, 0.018));
  const vol = new THREE.Mesh(keyGeo, chassis);
  vol.position.set(DIM.w / 2 + 0.002, DIM.h / 2 - 0.3, 0);
  root.add(vol);
  const power = new THREE.Mesh(keep(new THREE.BoxGeometry(0.09, 0.008, 0.018)), chassis);
  power.position.set(DIM.w / 2 - 0.2, DIM.h / 2 + 0.002, 0);
  root.add(power);
  const slotGeo = keep(new THREE.CapsuleGeometry(0.0035, 0.05, 2, 6));
  const slotMat = keep(new THREE.MeshBasicMaterial({ color: 0x020308 }));
  for (const sy of [1, -1]) for (const sx of [-0.28, 0.28]) {
    const s = new THREE.Mesh(slotGeo, slotMat);
    s.rotation.z = Math.PI / 2;
    s.position.set(sx, sy * (DIM.h / 2 + 0.0015), 0);
    root.add(s);
  }

  return {
    root,
    screenMount,
    setBackLogo,
    dispose() {
      disposables.forEach((d) => d.dispose());
      logoMat.map?.dispose();
    },
  };
}
