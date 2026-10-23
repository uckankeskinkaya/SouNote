"""Tanıtım videosu: sahne kayıtlarını telefon çerçevesine oturtur, başlık kartları ekler, birleştirir.

Girdi: scenes/NN-ad.mp4 (record.sh) + docs/img/icon.png. Çıktı: out/sounote.mp4 (1080x1920, dikey) ve out/poster.jpg.
"""
import os
import subprocess
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1080, 1920
FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
TOP, BOTTOM = (74, 123, 234), (30, 58, 138)
# Ekrandaki telefonun yeri (720x1600 kaydı 0.72 ölçekle)
PW, PH = 640, 1422
PX, PY = (W - PW) // 2, 420
R = 56

SCENES = [
    ("01-tanitim", "SouNote'a hoş geldin", "Ders notların, kalemin ve sınavların tek yerde"),
    ("02-ana", "Her şey ana sayfada", "Hızlı başla, haftalık liste, bugünkü tekrar"),
    ("03-dersler", "Haftalık ders kaynakları", "Konu konu ve hafta hafta; bitirdiğini işaretle"),
    ("04-sinavlar", "Sınavlarını kaçırma", "Takvim, geri sayım ve hatırlatıcılar"),
    ("05-defter", "Kalemle ya da parmakla yaz", "Kalemin yoksa: Parmakla yaz'ı aç"),
    ("06-writing", "Writing pratiği", "Essay yaz, asistan 100 üzerinden puanlasın"),
    ("07-ortalama", "Not ortalaman hazır", "Geçmek için kaç alman gerektiğini gör"),
    ("08-arama", "Her şeyde ara", "PDF'lerin içinde, notlarında, kartlarında"),
]


def font(path, size):
    return ImageFont.truetype(path, size)


def gradient():
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)))
    # Yumuşak daireler
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse([W - 520, -260, W + 260, 520], fill=(255, 255, 255, 26))
    g.ellipse([-300, H - 700, 400, H], fill=(255, 255, 255, 18))
    img.paste(glow, (0, 0), glow)
    return img


def centered(d, y, text, f, fill):
    w = d.textlength(text, font=f)
    d.text(((W - w) / 2, y), text, font=f, fill=fill)


def scene_background(title, sub):
    """Arka plan + başlık + telefonun gölgesi (video bunun üstüne, çerçeve en üste)."""
    bg = gradient()
    d = ImageDraw.Draw(bg)
    centered(d, 150, title, font(FONT_B, 66), (255, 255, 255))
    centered(d, 250, sub, font(FONT, 38), (225, 233, 255))
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([PX - 6, PY + 20, PX + PW + 6, PY + PH + 40], R + 10, fill=(0, 0, 0, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    bg.paste(shadow, (0, 0), shadow)
    return bg


def frame_overlay():
    """Telefon çerçevesi: ekran kısmı saydam, köşeler yuvarlak."""
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    bez = 16
    d.rounded_rectangle([PX - bez, PY - bez, PX + PW + bez, PY + PH + bez], R + bez, fill=(13, 18, 34, 255))
    # ekranı oy (saydam yap)
    hole = Image.new("L", (W, H), 0)
    ImageDraw.Draw(hole).rounded_rectangle([PX, PY, PX + PW, PY + PH], R, fill=255)
    alpha = ov.getchannel("A")
    alpha = Image.composite(Image.new("L", (W, H), 0), alpha, hole)
    ov.putalpha(alpha)
    return ov


def card(lines, path, logo=True):
    bg = gradient()
    d = ImageDraw.Draw(bg)
    y = 640
    if logo and os.path.exists("docs/img/icon.png"):
        ic = Image.open("docs/img/icon.png").convert("RGBA").resize((260, 260))
        bg.paste(ic, ((W - 260) // 2, 470), ic)
        y = 790
    for text, size, bold, color in lines:
        f = font(FONT_B if bold else FONT, size)
        centered(d, y, text, f, color)
        y += int(size * 1.45)
    bg.save(path)


def run(cmd):
    print(" ".join(cmd), flush=True)
    subprocess.run(cmd, check=True)


def duration(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
        capture_output=True, text=True,
    ).stdout.strip()
    return float(out or 0)


def main():
    os.makedirs("out/tmp", exist_ok=True)
    frame_overlay().save("out/tmp/frame.png")
    parts = []
    # Açılış kartı
    card([("SouNote", 110, True, (255, 255, 255)), ("Kalemle ders notu uygulaması", 44, False, (225, 233, 255))], "out/tmp/intro.png")
    run(["ffmpeg", "-y", "-loop", "1", "-t", "2.5", "-i", "out/tmp/intro.png", "-vf",
         "fade=in:0:12,fade=out:st=2.1:d=0.4,format=yuv420p", "-r", "30", "out/tmp/p00.mp4"])
    parts.append("out/tmp/p00.mp4")
    for i, (name, title, sub) in enumerate(SCENES, 1):
        src = f"scenes/{name}.mp4"
        if not os.path.exists(src) or duration(src) < 1:
            print("atlandı:", src)
            continue
        scene_background(title, sub).save(f"out/tmp/bg{i}.png")
        dur = min(duration(src), 14)
        out = f"out/tmp/p{i:02d}.mp4"
        run([
            "ffmpeg", "-y", "-loop", "1", "-i", f"out/tmp/bg{i}.png", "-i", src, "-i", "out/tmp/frame.png",
            "-filter_complex",
            f"[1:v]scale={PW}:{PH},fps=30[v];[0:v][v]overlay={PX}:{PY}:shortest=1[a];[a][2:v]overlay=0:0,"
            f"fade=in:0:9,fade=out:st={max(dur - 0.35, 0.5):.2f}:d=0.35,format=yuv420p",
            "-t", f"{dur:.2f}", "-r", "30", "-an", out,
        ])
        parts.append(out)
    # Kapanış kartı
    card([
        ("Ücretsiz · Reklamsız", 54, True, (255, 255, 255)),
        ("uckankeskinkaya.github.io/SouNote", 40, False, (247, 185, 40)),
    ], "out/tmp/outro.png")
    run(["ffmpeg", "-y", "-loop", "1", "-t", "3", "-i", "out/tmp/outro.png", "-vf",
         "fade=in:0:12,format=yuv420p", "-r", "30", "out/tmp/p99.mp4"])
    parts.append("out/tmp/p99.mp4")
    with open("out/tmp/list.txt", "w") as f:
        for p in parts:
            f.write(f"file '{os.path.abspath(p)}'\n")
    run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", "out/tmp/list.txt", "-c:v", "libx264", "-preset", "slow",
         "-crf", "28", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "out/sounote.mp4"])
    run(["ffmpeg", "-y", "-ss", "4", "-i", "out/sounote.mp4", "-frames:v", "1", "-q:v", "3", "out/poster.jpg"])
    print("Süre:", duration("out/sounote.mp4"), "sn, boyut:", os.path.getsize("out/sounote.mp4") // 1024, "KB")


if __name__ == "__main__":
    sys.exit(main())
