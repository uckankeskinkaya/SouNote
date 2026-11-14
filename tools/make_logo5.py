#!/usr/bin/env python3
"""SouNote logosu (4.0, 14 Kasım): mavi zeminde kıvrılıp S olan çizgili not kâğıdı.

Kaynak: tools/art/logo_source.png (Canva'da üretildi, köşedeki yıldız silindi).
Kaynaktan zemin degradesi (ikinci derece yüzey) ve kâğıt S (alfa ile) ayrılır; her tema için renk döndürülür.

Üretilenler:
  app/src/main/res/drawable-nodpi/logo_<tema>.webp   uygulama içi kare logo (AppLogo, widget)
  app/src/main/res/drawable-nodpi/ic_bg_<tema>.webp  uygulama simgesi zemini (108dp, 432 px)
  app/src/main/res/drawable-nodpi/ic_fg_<tema>.webp  uygulama simgesi ön planı (kâğıt S, güvenli alanda)
  app/src/main/res/drawable-nodpi/ic_launcher_monochrome.webp  temalı simge (Android 13+)
  site/public/brand/logo-a-*.png, site/public/img/icon.png, site/brand-src/logo-a-*.png, .github/video/logo.png
Kullanım: python3 tools/make_logo5.py
"""
import os
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "tools", "art", "logo_source.png")
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")

# tema → (ton kayması derece, doygunluk çarpanı, parlaklık çarpanı)
THEMES = {
    "electric": (0, 1.0, 1.0),
    "sapphire": (-10, 0.9, 0.72),
    "silver": (0, 0.2, 0.8),
    "mono": (0, 0.0, 0.62),
    "emerald": (-80, 0.85, 0.8),
    "rose": (110, 0.8, 1.0),
}


def load():
    a = np.asarray(Image.open(SRC).convert("RGB")).astype(np.float64)
    H, W, _ = a.shape
    # karo sınırları: beyaz olmayan alan
    nonwhite = a.sum(2) < 700
    ys, xs = np.nonzero(nonwhite)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    return a, (x0, y0, x1, y1)


def design(xx, yy):
    return np.stack([np.ones_like(xx), xx, yy, xx * xx, yy * yy, xx * yy], -1)


def split(a, box):
    H, W, _ = a.shape
    x0, y0, x1, y1 = box
    cx, cy, half = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
    nx, ny = (xx - cx) / half, (yy - cy) / half  # karo içi -1..1
    # kenar ve köşelerden uzak iç bölge
    inner = (np.maximum(np.abs(nx), np.abs(ny)) < 0.86) & (np.hypot(np.maximum(np.abs(nx) - 0.5, 0), np.maximum(np.abs(ny) - 0.5, 0)) < 0.34)
    sample = inner.copy()
    X = design(nx, ny)
    for _ in range(4):
        coef = [np.linalg.lstsq(X[sample], a[..., c][sample], rcond=None)[0] for c in range(3)]
        bg = np.stack([X @ k for k in coef], -1)
        resid = np.linalg.norm(a - bg, axis=2)
        sample = inner & (resid < 14)
    # kâğıt S'nin alfası (zeminden farka göre); gölgeler yarı saydam koyu kalır
    diff = np.linalg.norm(a - bg, axis=2)
    alpha = np.clip((diff - 9) / 40, 0, 1) * inner
    sym = bg + (a - bg) / np.maximum(alpha, 1e-3)[..., None]
    sym = np.clip(sym, 0, 255)
    ys, xs = np.nonzero(alpha > 0.5)
    sbox = (xs.min(), ys.min(), xs.max(), ys.max())
    return coef, (cx, cy, half), sym, alpha, sbox


def recolor(rgb, shift, keep_value=False):
    """Ton/doygunluk/parlaklık dönüşümü (numpy ile HSV). keep_value: kâğıt beyaz kalsın diye parlaklık değişmez."""
    dh, sm, vm = shift
    if shift == (0, 1.0, 1.0):
        return rgb
    x = np.clip(rgb, 0, 255) / 255.0
    r, g, b = x[..., 0], x[..., 1], x[..., 2]
    mx, mn = x.max(-1), x.min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    nz = d > 1e-9
    rc = np.where(nz, (mx - r) / np.where(nz, d, 1), 0)
    gc = np.where(nz, (mx - g) / np.where(nz, d, 1), 0)
    bc = np.where(nz, (mx - b) / np.where(nz, d, 1), 0)
    h = np.where(r == mx, bc - gc, np.where(g == mx, 2.0 + rc - bc, 4.0 + gc - rc))
    h = np.where(nz, (h / 6.0) % 1.0, 0)
    s = np.where(mx > 0, d / np.where(mx > 0, mx, 1), 0)
    v = mx
    h = (h + dh / 360.0) % 1.0
    s = np.clip(s * sm, 0, 1)
    if not keep_value:
        v = np.clip(v * vm, 0, 1)
    i = np.floor(h * 6).astype(int) % 6
    f = h * 6 - np.floor(h * 6)
    p_, q_, t_ = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    out = np.choose(i[..., None] * 0 + np.arange(3) * 0 + i[..., None], [
        np.stack([v, t_, p_], -1), np.stack([q_, v, p_], -1), np.stack([p_, v, t_], -1),
        np.stack([p_, q_, v], -1), np.stack([t_, p_, v], -1), np.stack([v, p_, q_], -1)])
    return out * 255


def render(coef, geo, sym, alpha, sbox, size, sym_h, shift, rounded=None, with_symbol=True, with_bg=True):
    """size×size görüntü: zemin degradesi + ortada sym_h piksel yüksekliğinde kâğıt S."""
    cx, cy, half = geo
    sx0, sy0, sx1, sy1 = sbox
    scx, scy = (sx0 + sx1) / 2, (sy0 + sy1) / 2
    k = sym_h / (sy1 - sy0)  # kaynak piksel → çıktı pikseli
    out = np.zeros((size, size, 4))
    if with_bg:
        v, u = np.mgrid[0:size, 0:size].astype(np.float64)
        srcx, srcy = scx + (u - size / 2) / k, scy + (v - size / 2) / k
        nx = np.clip((srcx - cx) / half, -0.9, 0.9)
        ny = np.clip((srcy - cy) / half, -0.9, 0.9)
        X = design(nx, ny)
        bg = np.stack([X @ c for c in coef], -1)
        out[..., :3] = recolor(bg, shift)
        out[..., 3] = 255
    if with_symbol:
        pad = 6
        crop = (sx0 - pad, sy0 - pad, sx1 + pad, sy1 + pad)
        rgba = np.dstack([sym, alpha * 255])[crop[1]:crop[3], crop[0]:crop[2]]
        rgba[..., :3] = recolor(rgba[..., :3], shift, keep_value=True)
        im = Image.fromarray(rgba.astype(np.uint8), "RGBA")
        w, h = round(im.width * k), round(im.height * k)
        im = im.resize((w, h), Image.LANCZOS)
        layer = Image.new("RGBA", (size, size))
        layer.paste(im, (round(size / 2 - w / 2), round(size / 2 - h / 2)))
        base = Image.fromarray(out.astype(np.uint8), "RGBA")
        out_im = Image.alpha_composite(base, layer) if with_bg else layer
    else:
        out_im = Image.fromarray(out.astype(np.uint8), "RGBA")
    if rounded:
        m = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(m).rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=round(size * 4 * rounded), fill=255)
        m = m.resize((size, size), Image.LANCZOS)
        out_im.putalpha(Image.fromarray(np.minimum(np.asarray(out_im.getchannel("A")), np.asarray(m))))
    return out_im


def main():
    a, box = load()
    coef, geo, sym, alpha, sbox = split(a, box)
    os.makedirs(RES, exist_ok=True)
    R = 0.225  # karo köşe yarıçapı
    for name, shift in THEMES.items():
        render(coef, geo, sym, alpha, sbox, 512, 512 * 0.62, shift, rounded=R).save(os.path.join(RES, f"logo_{name}.webp"), lossless=False, quality=92)
        render(coef, geo, sym, alpha, sbox, 432, 432 * 0.42, shift, with_symbol=False).convert("RGB").save(os.path.join(RES, f"ic_bg_{name}.webp"), quality=92)
        render(coef, geo, sym, alpha, sbox, 432, 432 * 0.42, shift, with_bg=False).save(os.path.join(RES, f"ic_fg_{name}.webp"), quality=92)
    # Temalı simge: sadece kâğıt (zeminden açık kısımlar), beyaz + alfa
    fg = np.asarray(render(coef, geo, sym, alpha, sbox, 432, 432 * 0.42, (0, 1.0, 1.0), with_bg=False)).astype(np.float64)
    lum = fg[..., :3].mean(2)
    mono = np.zeros_like(fg)
    mono[..., :3] = 255
    mono[..., 3] = fg[..., 3] * np.clip((lum - 90) / 60, 0, 1)
    Image.fromarray(mono.astype(np.uint8), "RGBA").save(os.path.join(RES, "ic_launcher_monochrome.webp"), quality=92)

    # Site ve video
    el = THEMES["electric"]
    big = render(coef, geo, sym, alpha, sbox, 1024, 1024 * 0.62, el, rounded=R)
    brand = os.path.join(ROOT, "site", "public", "brand")
    for s in (32, 64, 180, 256, 512):
        big.resize((s, s), Image.LANCZOS).save(os.path.join(brand, f"logo-a-{s}.png"))
    render(coef, geo, sym, alpha, sbox, 512, 512 * 0.62, el, rounded=R, with_symbol=False).save(os.path.join(brand, "logo-a-paper.png"))
    render(coef, geo, sym, alpha, sbox, 512, 512 * 0.62, el, with_bg=False).save(os.path.join(brand, "logo-a-pen.png"))
    symbol = render(coef, geo, sym, alpha, sbox, 1024, 1024 * 0.86, el, with_bg=False)
    symbol.resize((512, 512), Image.LANCZOS).save(os.path.join(brand, "logo-a-symbol-512.png"))
    symbol.save(os.path.join(ROOT, "site", "brand-src", "logo-a-symbol-1024.png"))
    big.save(os.path.join(ROOT, "site", "brand-src", "logo-a-1024.png"))
    big.resize((512, 512), Image.LANCZOS).save(os.path.join(ROOT, "site", "public", "img", "icon.png"))
    big.resize((512, 512), Image.LANCZOS).save(os.path.join(ROOT, ".github", "video", "logo.png"))
    # açılış ekranı zemini: simge zemininin ortası
    mid = np.asarray(render(coef, geo, sym, alpha, sbox, 64, 32, el, with_symbol=False))[32, 32, :3]
    print("splash", "#%02X%02X%02X" % tuple(int(x) for x in mid))


if __name__ == "__main__":
    main()
