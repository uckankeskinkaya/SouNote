"""Seslendirmeyi hazırlar ve paragraflara böler → out/voice.wav + out/voice-times.json.

voice.txt'te satır başına bir paragraf (açılış kartı, sahneler, kapanış). voice.mp3 bunların INSERTS dışındakilerini
içerir; INSERTS'teki paragraflar ayrı dosyalardır (sonradan eklenen sahneler) ve bir sonraki paragrafın başladığı
sessizliğe yerleştirilir. Zamanlar Whisper (faster-whisper) kelime zamanlarından bulunur: metin difflib ile hizalanır,
paragrafın ilk kelimesinin başlangıcı alınır. Whisper çalışmazsa FALLBACK (sessizlik ölçümüyle bulunan anlar).
"""
import difflib
import json
import os
import re
import subprocess
import sys

VOICE = ".github/video/voice.mp3"
TEXT = ".github/video/voice.txt"
# voice.txt satır numarası (0'dan) → o paragrafın ayrı ses dosyası
INSERTS = {9: ".github/video/voice-asistan.mp3"}
# voice.mp3'teki paragraf 1..10'un başlangıcı (sn); voice.mp3 değişirse geçersizdir
FALLBACK = [5.41, 14.93, 29.65, 37.25, 50.40, 60.40, 68.91, 77.63, 85.87, 92.86]
GAP = 0.35  # eklenen parçanın önünde ve arkasında sessizlik


def norm(w):
    return re.sub(r"[^a-z0-9çğıöşü]+", "", w.lower().replace("İ", "i").replace("I", "ı"))


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         capture_output=True, text=True).stdout.strip()
    return float(out or 0)


def silences(path):
    err = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", path, "-af", "silencedetect=noise=-30dB:d=0.12",
                          "-f", "null", "-"], capture_output=True, text=True).stderr
    s = [float(x) for x in re.findall(r"silence_start: ([0-9.]+)", err)]
    e = [float(x) for x in re.findall(r"silence_end: ([0-9.]+)", err)]
    return list(zip(s, e))


def whisper_starts(paras):
    """voice.mp3'teki paragrafların (ilki hariç) başlangıçları; bulunamayan None."""
    from faster_whisper import WhisperModel
    model = WhisperModel("small", device="cpu", compute_type="int8")
    segs, _ = model.transcribe(VOICE, language="tr", word_timestamps=True)
    heard = [(norm(w.word), w.start) for s in segs for w in (s.words or [])]
    heard = [h for h in heard if h[0]]
    print("duyulan:", " ".join(h[0] for h in heard))
    script, first = [], []
    for p in paras:
        first.append(len(script))
        script += [n for n in (norm(w) for w in p.split()) if n]
    sm = difflib.SequenceMatcher(None, script, [h[0] for h in heard], autojunk=False)
    pairs = [(b.a + k, b.b + k) for b in sm.get_matching_blocks() for k in range(b.size)]
    print(f"hizalanan kelime: {len(pairs)}/{len(script)}")
    out = []
    for n, i in enumerate(first[1:]):
        end = first[n + 2] if n + 2 < len(first) else len(script)
        # Paragrafın ilk eşleşen kelimesi (paragrafın içinde); öncesindeki eşleşmeyen kelimeler kadar geri
        cand = [p for p in pairs if i <= p[0] < end]
        if not cand:
            out.append(None)
            continue
        a, b = min(cand)
        out.append(heard[max(b - (a - i), 0)][1])
    return out


def main():
    paras = [p.strip() for p in open(TEXT, encoding="utf-8") if p.strip()]
    base = [p for k, p in enumerate(paras) if k not in INSERTS]
    error = None
    starts = list(FALLBACK)
    try:
        found = whisper_starts(base)
        if len(found) != len(FALLBACK):
            raise RuntimeError(f"paragraf sayısı {len(found)}")
        print("whisper:", found)
        merged = [f if f is not None and abs(f - fb) < 4 else fb for f, fb in zip(found, FALLBACK)]
        if any(y <= x for x, y in zip(merged, merged[1:])):
            raise RuntimeError(f"sıra bozuk: {merged}")
        missing = sum(1 for f, m in zip(found, merged) if f != m)
        if missing:
            error = f"{missing} paragraf sabit süreden"
        starts = merged
    except Exception as e:  # noqa: BLE001
        error = f"{type(e).__name__}: {e}"
        print("Whisper kullanılamadı, sabit süreler:", error)
    os.makedirs("out", exist_ok=True)

    # Eklenecek parçalar: bir sonraki paragrafın başından hemen önceki sessizliğin ortasından kesilir
    sil = silences(VOICE)
    pieces, final, pos, shift = [], [], 0.0, 0.0
    bi = 0  # voice.mp3'teki paragraf sırası
    for k in range(1, len(paras)):
        if k in INSERTS:
            nxt = starts[bi]  # voice.mp3'te sıradaki paragrafın başı
            near = [s for s in sil if s[1] <= nxt + 0.3 and s[1] >= nxt - 0.8]
            cut = (near[-1][0] + near[-1][1]) / 2 if near else nxt - 0.1
            pieces.append(("base", pos, cut))
            pieces.append(("gap", GAP))
            final.append(round(cut + shift + GAP, 2))
            clip = INSERTS[k]
            pieces.append(("clip", clip))
            pieces.append(("gap", GAP))
            shift += GAP * 2 + duration(clip)
            pos = cut
            print(f"{clip} {cut:.2f} sn'ye eklendi")
        else:
            final.append(round(starts[bi] + shift, 2))
            bi += 1
    pieces.append(("base", pos, None))

    args, filt, labels = ["ffmpeg", "-y", "-i", VOICE], [], []
    clips = {}
    for p in pieces:
        if p[0] == "clip" and p[1] not in clips:
            clips[p[1]] = len(clips) + 1
            args += ["-i", p[1]]
    for n, p in enumerate(pieces):
        if p[0] == "base":
            end = f":end={p[2]:.3f}" if p[2] is not None else ""
            filt.append(f"[0:a]atrim=start={p[1]:.3f}{end},asetpts=PTS-STARTPTS,aresample=44100,aformat=channel_layouts=mono[s{n}]")
        elif p[0] == "gap":
            filt.append(f"anullsrc=r=44100:cl=mono,atrim=duration={p[1]}[s{n}]")
        else:
            filt.append(f"[{clips[p[1]]}:a]aresample=44100,aformat=channel_layouts=mono,asetpts=PTS-STARTPTS[s{n}]")
        labels.append(f"[s{n}]")
    filt.append("".join(labels) + f"concat=n={len(labels)}:v=0:a=1[out]")
    subprocess.run(args + ["-filter_complex", ";".join(filt), "-map", "[out]", "out/voice.wav"], check=True)

    json.dump({"starts": final, "whisper": error is None, "error": error}, open("out/voice-times.json", "w"))
    print("başlangıçlar:", final, "hata:", error)


if __name__ == "__main__":
    sys.exit(main())
