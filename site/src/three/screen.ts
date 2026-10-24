// Tablet ekranı: gerçek ekran görüntüsü, oranı korunarak tuvale çizilir ve iki katman arasında yumuşak geçişle değişir.
// Görüntü tablete göre daha uzun olduğundan boş kalan kenarlar, görüntünün kendi kenar pikselleri uzatılarak doldurulur.
import * as THREE from "three";
import { screenById, type ScreenDef } from "../config";
import { DIM } from "./tablet";

const imgCache = new Map<string, Promise<HTMLImageElement>>();
export function loadImage(src: string): Promise<HTMLImageElement> {
  let p = imgCache.get(src);
  if (!p) {
    p = new Promise((res, rej) => {
      const i = new Image();
      i.decoding = "async";
      i.onload = () => res(i);
      i.onerror = rej;
      i.src = src;
    });
    imgCache.set(src, p);
  }
  return p;
}

/** İçeriğin tuvaldeki yeri (0..1). Yatay içerikte w görüntünün yüksekliği yönündedir (döndürülmüş çizim). */
export interface ContentRect { x: number; y: number; w: number; h: number }

interface Layer { canvas: HTMLCanvasElement; tex: THREE.CanvasTexture; mat: THREE.MeshBasicMaterial; mesh: THREE.Mesh; id: string; rect: ContentRect }

export class TabletScreen {
  readonly group = new THREE.Group();
  private layers: Layer[];
  private front = 0;
  private fade = 1;
  private wanted = "";
  private shownId = "";
  private token = 0;
  private logo: HTMLImageElement | null = null;
  private readonly cw: number;
  private readonly ch: number;

  constructor(textureWidth: number, private anisotropy: number) {
    this.cw = textureWidth;
    this.ch = Math.round(textureWidth * (DIM.screenH / DIM.screenW));
    const geo = new THREE.PlaneGeometry(DIM.screenW, DIM.screenH);
    this.layers = [0, 1].map((i) => {
      const canvas = document.createElement("canvas");
      canvas.width = this.cw;
      canvas.height = this.ch;
      const tex = new THREE.CanvasTexture(canvas);
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.anisotropy = this.anisotropy;
      const mat = new THREE.MeshBasicMaterial({ map: tex, toneMapped: false, transparent: true, color: 0xeef1fa });
      const mesh = new THREE.Mesh(geo, mat);
      mesh.position.z = i * 0.0004;
      mesh.renderOrder = 2 + i;
      this.group.add(mesh);
      return { canvas, tex, mat, mesh, id: "", rect: { x: 0, y: 0, w: 1, h: 1 } };
    });
    this.layers[1].mat.opacity = 0;
  }

  get currentId(): string { return this.shownId; }
  get currentRect(): ContentRect { return this.layers[this.front].rect; }

  setLogo(img: HTMLImageElement): void {
    this.logo = img;
    if (this.shownId === "splash") { this.shownId = ""; this.set("splash", true); }
  }

  /** Ekranı değiştirir; görüntü yüklenince geçiş başlar. instant: geçişsiz. */
  set(id: string, instant = false): void {
    if (id === this.wanted && (this.shownId === id || !instant)) return;
    this.wanted = id;
    const def = screenById(id);
    const t = ++this.token;
    const ready = def.src ? loadImage(def.src).then((img) => img) : Promise.resolve(null);
    ready.then((img) => {
      if (t !== this.token) return;
      const back = this.layers[1 - this.front];
      this.draw(back, def, img);
      back.id = id;
      // Arka katmanı öne al ve görünür yap
      back.mesh.position.z = 0.0004;
      back.mesh.renderOrder = 3;
      const fr = this.layers[this.front];
      fr.mesh.position.z = 0;
      fr.mesh.renderOrder = 2;
      fr.mat.opacity = 1;
      this.front = 1 - this.front;
      this.shownId = id;
      this.fade = instant ? 1 : 0;
      back.mat.opacity = this.fade;
    }).catch(() => {});
  }

  update(dt: number): void {
    if (this.fade >= 1) return;
    this.fade = Math.min(1, this.fade + dt / 0.45);
    const e = this.fade * this.fade * (3 - 2 * this.fade);
    this.layers[this.front].mat.opacity = e;
  }

  private draw(layer: Layer, def: ScreenDef, img: HTMLImageElement | null): void {
    const g = layer.canvas.getContext("2d")!;
    const W = this.cw, H = this.ch;
    g.setTransform(1, 0, 0, 1, 0, 0);
    if (!img) {
      this.drawSplash(g, W, H);
      layer.rect = { x: 0, y: 0, w: 1, h: 1 };
    } else if (def.orientation === "portrait") {
      const s = Math.min(W / img.width, H / img.height);
      const dw = img.width * s, dh = img.height * s;
      const dx = (W - dw) / 2, dy = (H - dh) / 2;
      g.fillStyle = "#f4f6fb";
      g.fillRect(0, 0, W, H);
      g.imageSmoothingQuality = "high";
      // kenar uzatma: sol/sağ ve üst/alt şeritler görüntünün kenar pikselleriyle
      if (dx > 0.5) {
        g.drawImage(img, 0, 0, 2, img.height, 0, dy, dx + 1, dh);
        g.drawImage(img, img.width - 2, 0, 2, img.height, dx + dw - 1, dy, dx + 1, dh);
      }
      if (dy > 0.5) {
        g.drawImage(img, 0, 0, img.width, 2, 0, 0, W, dy + 1);
        g.drawImage(img, 0, img.height - 2, img.width, 2, 0, dy + dh - 1, W, dy + 1);
      }
      g.drawImage(img, dx, dy, dw, dh);
      layer.rect = { x: dx / W, y: dy / H, w: dw / W, h: dh / H };
    } else {
      // Yatay içerik: tablet -90° dönünce düz görünsün diye görüntünün üstü tuvalin soluna gelecek şekilde çizilir.
      g.fillStyle = "#f4f6fb";
      g.fillRect(0, 0, W, H);
      g.translate(0, H);
      g.rotate(-Math.PI / 2);
      // döndürülmüş koordinatlarda alan H x W
      const s = Math.min(H / img.width, W / img.height);
      const dw = img.width * s, dh = img.height * s;
      const dx = (H - dw) / 2, dy = (W - dh) / 2;
      g.imageSmoothingQuality = "high";
      if (dy > 0.5) {
        g.drawImage(img, 0, 0, img.width, 2, dx, 0, dw, dy + 1);
        g.drawImage(img, 0, img.height - 2, img.width, 2, dx, dy + dh - 1, dw, dy + 1);
      }
      if (dx > 0.5) {
        g.drawImage(img, 0, 0, 2, img.height, 0, 0, dx + 1, W);
        g.drawImage(img, img.width - 2, 0, 2, img.height, dx + dw - 1, 0, dx + 1, W);
      }
      g.drawImage(img, dx, dy, dw, dh);
      g.setTransform(1, 0, 0, 1, 0, 0);
      // tuval koordinatlarında: x = dy..dy+dh, y = H-dx-dw..H-dx
      layer.rect = { x: dy / W, y: (H - dx - dw) / H, w: dh / W, h: dw / H };
    }
    layer.tex.needsUpdate = true;
  }

  private drawSplash(g: CanvasRenderingContext2D, W: number, H: number): void {
    const bg = g.createRadialGradient(W / 2, H * 0.42, 0, W / 2, H * 0.42, H * 0.7);
    bg.addColorStop(0, "#132650");
    bg.addColorStop(0.55, "#0a1430");
    bg.addColorStop(1, "#050914");
    g.fillStyle = bg;
    g.fillRect(0, 0, W, H);
    const size = W * 0.46;
    if (this.logo) g.drawImage(this.logo, (W - size) / 2, H * 0.42 - size / 2, size, size);
    g.textAlign = "center";
    g.fillStyle = "#f4f7ff";
    g.font = `600 ${Math.round(W * 0.105)}px "Space Grotesk Variable", "Space Grotesk", sans-serif`;
    g.fillText("SouNote", W / 2, H * 0.42 + size / 2 + W * 0.15);
    g.fillStyle = "#a7b5d1";
    g.font = `400 ${Math.round(W * 0.038)}px "Geist Variable", "Geist", sans-serif`;
    g.fillText("Ders çalışmanın en sade hâli.", W / 2, H * 0.42 + size / 2 + W * 0.23);
  }

  /** Logo merkezinin ekran düzlemindeki yeri ve boyu (açılış animasyonunun ineceği nokta). */
  splashLogoLocal(): { x: number; y: number; size: number } {
    return { x: 0, y: (0.5 - 0.42) * DIM.screenH, size: 0.46 * DIM.screenW };
  }

  dispose(): void {
    this.layers.forEach((l) => { l.tex.dispose(); l.mat.dispose(); });
    (this.layers[0].mesh.geometry as THREE.BufferGeometry).dispose();
  }
}
