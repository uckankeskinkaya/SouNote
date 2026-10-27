"""Tanıtım videosu: sahne kayıtlarını telefon çerçevesine oturtur, başlık kartları ekler, birleştirir.

Girdi: scenes/NN-ad.mp4 (record.sh) + uygulama logosu (logo_tile.png). Çıktı: out/sounote.mp4 (1080x1920, dikey) ve out/poster.jpg.
Seslendirme varsa (align.py: out/voice.wav + out/voice-times.json) her parça kendi paragrafı kadar sürer
(kayıt kısaysa son kare bekletilir) ve ses videoya eklenir.
"""
import json
import os
import subprocess
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1080, 1920
# 2 çekirdekli sunucuda hızlı ama yeterli kalite
ENC = ["-c:v", "libx264", "-preset", "veryfast", "-crf", "27", "-pix_fmt", "yuv420p", "-threads", "0"]
FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
# Logodaki gibi: üstte lacivert, altta neredeyse siyah; elektrik mavisi ışıklar
TOP, BOTTOM = (6, 22, 78), (1, 5, 24)
GLOW_A, GLOW_B = (31, 79, 255), (61, 139, 255)
LOGO = "app/src/main/res/drawable-nodpi/logo_tile.png"
# Ekrandaki telefonun yeri (720x1600 kaydı 0.72 ölçekle)
PW, PH = 640, 1422
PX, PY = (W - PW) // 2, 420
R = 56

SCENES = [
    ("01-tanitim", "SouNote'a hoş geldin", "Ders notların, kalemin ve sınavların tek yerde"),
    ("02-defter", "Kalemle ya da parmakla yaz", "14 kalem, pürüzsüz çizgi, cetvel ve katmanlar"),
    ("03-ana", "Her şey ana sayfada", "Kaldığın yer, yaklaşan sınavlar, son kullanılanlar"),
    ("04-temalar", "Altı tema", "Logo, simge ve widget'lar temaya göre değişir"),
    ("05-dersler", "Haftalık ders kaynakları", "Drive'a eklenen yeni hafta kendiliğinden gelir"),
    ("06-harita", "Konu haritası", "Hangi konuda ne kadar hazırsın"),
    ("07-planlayici", "Sınavlarını kaçırma", "Takvim, geri sayım, hatırlatıcı ve widget"),
    ("08-yazi", "Yazı stüdyosu", "Essay yaz; asistan puanlasın, alıştırma hazırlasın"),
    ("09-asistan", "Asistan", "PDF'ini özetler, sorularını cevaplar, kart hazırlar"),
    ("10-arama", "Her şeyde ara", "PDF'lerin içinde, notlarında, derslerde"),
]


def font(path, size):
    return ImageFont.truetype(path, size)


def gradient():
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)))
    # Parlayan elektrik mavisi ışıklar (sağ üst, sol alt)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse([W - 700, -520, W + 420, 600], fill=GLOW_A + (150,))
    g.ellipse([-520, H - 820, 520, H + 300], fill=GLOW_B + (90,))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
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
    centered(d, 250, sub, font(FONT, 36), (190, 205, 255))
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # Telefonun etrafında logodaki gibi mavi ışıma
    ImageDraw.Draw(shadow).rounded_rectangle([PX - 10, PY - 10, PX + PW + 10, PY + PH + 10], R + 12, fill=GLOW_A + (120,))
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    bg.paste(shadow, (0, 0), shadow)
    return bg


def frame_overlay():
    """Telefon çerçevesi: ekran kısmı saydam, köşeler yuvarlak."""
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    bez = 16
    d.rounded_rectangle([PX - bez, PY - bez, PX + PW + bez, PY + PH + bez], R + bez, fill=(4, 8, 26, 255),
                        outline=(120, 150, 255, 200), width=3)
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
    if logo and os.path.exists(LOGO):
        ic = Image.open(LOGO).convert("RGBA").resize((300, 300), Image.LANCZOS)
        # Logonun arkasında ışıma
        halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(halo).rounded_rectangle([(W - 300) // 2 - 20, 420, (W + 300) // 2 + 20, 760], 80, fill=GLOW_A + (170,))
        halo = halo.filter(ImageFilter.GaussianBlur(40))
        bg.paste(halo, (0, 0), halo)
        bg.paste(ic, ((W - 300) // 2, 440), ic)
        y = 820
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


VOICE = "out/voice.wav"  # align.py: seslendirme + eklenen parçalar


def voice_slots(n):
    """Seslendirme varsa parça süreleri: açılış, n sahne, kapanış (sn); yoksa None."""
    if not os.path.exists(VOICE) or not os.path.exists("out/voice-times.json"):
        return None
    starts = json.load(open("out/voice-times.json"))["starts"]
    if len(starts) != n + 1:
        print("seslendirme paragraf sayısı tutmuyor:", len(starts))
        return None
    # Sahne, konuşmadan biraz önce gelsin
    cuts = [0.0] + [max(t - 0.25, 0.5) for t in starts] + [duration(VOICE) + 1.5]
    return [b - a for a, b in zip(cuts, cuts[1:])]


def main():
    os.makedirs("out/tmp", exist_ok=True)
    slots = voice_slots(len(SCENES))
    frame_overlay().save("out/tmp/frame.png")
    parts = []
    # Açılış kartı
    card([("SouNote", 110, True, (255, 255, 255)), ("Kalemle ders notu uygulaması", 44, False, (190, 205, 255))], "out/tmp/intro.png")
    t0 = slots[0] if slots else 2.5
    run(["ffmpeg", "-y", "-loop", "1", "-t", f"{t0:.2f}", "-i", "out/tmp/intro.png", "-vf",
         f"fade=in:0:12,fade=out:st={t0 - 0.4:.2f}:d=0.4,format=yuv420p", "-r", "30", *ENC, "out/tmp/p00.mp4"])
    parts.append("out/tmp/p00.mp4")
    for i, (name, title, sub) in enumerate(SCENES, 1):
        src = f"scenes/{name}.mp4"
        ok = os.path.exists(src) and duration(src) >= 1
        if not ok and not slots:
            print("atlandı:", src)
            continue
        scene_background(title, sub).save(f"out/tmp/bg{i}.png")
        out = f"out/tmp/p{i:02d}.mp4"
        if not ok:
            # Seslendirmeyle sıra kaymasın: kayıt yoksa sadece başlık
            print("kayıt yok, başlık kartı:", src)
            run(["ffmpeg", "-y", "-loop", "1", "-t", f"{slots[i]:.2f}", "-i", f"out/tmp/bg{i}.png", "-vf",
                 "fade=in:0:9,format=yuv420p", "-r", "30", *ENC, out])
            parts.append(out)
            continue
        if slots:
            dur = slots[i]
            # Kayıt paragraftan kısaysa son kare bekler
            pad = f",tpad=stop_mode=clone:stop_duration={max(dur - duration(src), 0) + 0.5:.2f}"
        else:
            dur, pad = min(duration(src), 13), ""
        run([
            "ffmpeg", "-y", "-loop", "1", "-i", f"out/tmp/bg{i}.png", "-i", src, "-i", "out/tmp/frame.png",
            "-filter_complex",
            f"[1:v]scale={PW}:{PH},fps=30{pad}[v];[0:v][v]overlay={PX}:{PY}:shortest=1[a];[a][2:v]overlay=0:0,"
            f"fade=in:0:9,fade=out:st={max(dur - 0.35, 0.5):.2f}:d=0.35,format=yuv420p",
            "-t", f"{dur:.2f}", "-r", "30", "-an", *ENC, out,
        ])
        parts.append(out)
    # Kapanış kartı
    card([
        ("Ücretsiz · Reklamsız", 54, True, (255, 255, 255)),
        ("uckankeskinkaya.github.io/SouNote", 40, False, (141, 166, 255)),
    ], "out/tmp/outro.png")
    run(["ffmpeg", "-y", "-loop", "1", "-t", f"{slots[-1] if slots else 3:.2f}", "-i", "out/tmp/outro.png", "-vf",
         "fade=in:0:12,format=yuv420p", "-r", "30", *ENC, "out/tmp/p99.mp4"])
    parts.append("out/tmp/p99.mp4")
    with open("out/tmp/list.txt", "w") as f:
        for p in parts:
            f.write(f"file '{os.path.abspath(p)}'\n")
    # Parçalar aynı ayarlarla kodlandı: yeniden kodlamadan birleştir
    if slots:
        run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", "out/tmp/list.txt", "-i", VOICE, "-map", "0:v", "-map", "1:a",
             "-c:v", "copy", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "out/sounote.mp4"])
    else:
        run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", "out/tmp/list.txt", "-c", "copy", "-movflags", "+faststart", "out/sounote.mp4"])
    run(["ffmpeg", "-y", "-ss", "4", "-i", "out/sounote.mp4", "-frames:v", "1", "-q:v", "3", "out/poster.jpg"])
    print("Süre:", duration("out/sounote.mp4"), "sn, boyut:", os.path.getsize("out/sounote.mp4") // 1024, "KB")


if __name__ == "__main__":
    sys.exit(main())
