// Cihaz ve tercih algılama: hareket azaltma, kalite katmanı (yüksek / orta / düşük).

export type Tier = "high" | "medium" | "low";

const root = document.documentElement;
const reduceQuery = matchMedia("(prefers-reduced-motion: reduce)");

export const motion = {
  get reduced(): boolean {
    return reduceQuery.matches;
  },
  onChange(cb: (reduced: boolean) => void): void {
    reduceQuery.addEventListener("change", () => {
      root.classList.toggle("reduce", reduceQuery.matches);
      cb(reduceQuery.matches);
    });
  },
};

export const coarsePointer = matchMedia("(pointer: coarse)").matches;

interface NavigatorExtras {
  deviceMemory?: number;
  connection?: { saveData?: boolean };
}

/** WebGL desteği ve GPU bilgisi. Yazılımsal işleyiciler (SwiftShader, llvmpipe) düşük sayılır. */
export function probeWebGL(): { ok: boolean; software: boolean; renderer: string } {
  try {
    const c = document.createElement("canvas");
    const gl = (c.getContext("webgl2") || c.getContext("webgl")) as WebGLRenderingContext | null;
    if (!gl) return { ok: false, software: false, renderer: "" };
    const ext = gl.getExtension("WEBGL_debug_renderer_info");
    const renderer = String(ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER));
    gl.getExtension("WEBGL_lose_context")?.loseContext();
    return { ok: true, software: /swiftshader|llvmpipe|software|basic render/i.test(renderer), renderer };
  } catch {
    return { ok: false, software: false, renderer: "" };
  }
}

/** Başlangıç katmanı. ?quality=high|medium|low ile zorlanabilir (test için). */
export function initialTier(software: boolean): Tier {
  const forced = new URLSearchParams(location.search).get("quality");
  if (forced === "high" || forced === "medium" || forced === "low") return forced;
  const nav = navigator as Navigator & NavigatorExtras;
  const mem = nav.deviceMemory ?? 8;
  const cores = navigator.hardwareConcurrency || 4;
  if (software || nav.connection?.saveData || mem <= 2 || cores <= 2) return "low";
  if (coarsePointer || mem <= 4 || cores <= 4) return "medium";
  return "high";
}

export const TIER_SETTINGS: Record<Tier, { dpr: number; particles: number; texture: number; env: boolean; glass: boolean; physical: boolean }> = {
  high: { dpr: 2, particles: 900, texture: 1280, env: true, glass: true, physical: true },
  medium: { dpr: 1.5, particles: 380, texture: 1024, env: true, glass: true, physical: false },
  low: { dpr: 1, particles: 90, texture: 768, env: false, glass: false, physical: false },
};

export const lowerTier = (t: Tier): Tier => (t === "high" ? "medium" : "low");
