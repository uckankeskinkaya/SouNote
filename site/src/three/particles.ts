// Aurora tozları: tek çizim çağrısı, hareket tamamen GPU'da (zaman + kaydırma).
import * as THREE from "three";

const vert = /* glsl */ `
uniform float uTime;
uniform float uScroll;
uniform float uPixelRatio;
attribute vec3 aSeed;
varying float vAlpha;
varying float vHue;
void main() {
  vec3 p = position;
  float speed = 0.15 + aSeed.x * 0.35;
  p.y = mod(p.y + uTime * 0.04 * speed + uScroll * (0.4 + aSeed.y * 0.8) + 9.0, 18.0) - 9.0;
  p.x += sin(uTime * 0.2 * speed + aSeed.z * 6.283) * 0.25;
  vec4 mv = modelViewMatrix * vec4(p, 1.0);
  gl_Position = projectionMatrix * mv;
  gl_PointSize = (2.0 + aSeed.y * 5.0) * uPixelRatio * (9.0 / -mv.z);
  vAlpha = (0.25 + 0.75 * aSeed.z) * (0.55 + 0.45 * sin(uTime * (0.6 + aSeed.x) + aSeed.y * 20.0));
  vHue = aSeed.x;
}`;

const frag = /* glsl */ `
uniform float uOpacity;
varying float vAlpha;
varying float vHue;
void main() {
  float d = length(gl_PointCoord - 0.5);
  float a = smoothstep(0.5, 0.0, d);
  vec3 blue = vec3(0.29, 0.49, 1.0);
  vec3 violet = vec3(0.55, 0.42, 0.96);
  vec3 cyan = vec3(0.38, 0.84, 0.97);
  vec3 c = vHue < 0.5 ? mix(blue, violet, vHue * 2.0) : mix(violet, cyan, (vHue - 0.5) * 2.0);
  gl_FragColor = vec4(c, a * a * vAlpha * uOpacity);
}`;

export class Particles {
  readonly points: THREE.Points;
  private mat: THREE.ShaderMaterial;
  private geo: THREE.BufferGeometry;

  constructor(private max: number, pixelRatio: number) {
    const pos = new Float32Array(max * 3);
    const seed = new Float32Array(max * 3);
    for (let i = 0; i < max; i++) {
      pos[i * 3] = (Math.random() - 0.5) * 22;
      pos[i * 3 + 1] = (Math.random() - 0.5) * 18;
      pos[i * 3 + 2] = -6 + Math.random() * 7.5;
      seed[i * 3] = Math.random();
      seed[i * 3 + 1] = Math.random();
      seed[i * 3 + 2] = Math.random();
    }
    this.geo = new THREE.BufferGeometry();
    this.geo.setAttribute("position", new THREE.BufferAttribute(pos, 3));
    this.geo.setAttribute("aSeed", new THREE.BufferAttribute(seed, 3));
    this.mat = new THREE.ShaderMaterial({
      vertexShader: vert,
      fragmentShader: frag,
      uniforms: { uTime: { value: 0 }, uScroll: { value: 0 }, uPixelRatio: { value: pixelRatio }, uOpacity: { value: 1 } },
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    });
    this.points = new THREE.Points(this.geo, this.mat);
    this.points.frustumCulled = false;
    this.points.renderOrder = -1;
  }

  setCount(n: number): void { this.geo.setDrawRange(0, Math.min(n, this.max)); }
  setPixelRatio(r: number): void { this.mat.uniforms.uPixelRatio.value = r; }

  update(time: number, scrollY: number, opacity: number): void {
    this.mat.uniforms.uTime.value = time;
    this.mat.uniforms.uScroll.value = scrollY * 0.0016;
    this.mat.uniforms.uOpacity.value = opacity;
  }

  dispose(): void { this.geo.dispose(); this.mat.dispose(); }
}
