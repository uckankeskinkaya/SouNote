"""Seslendirmeyi paragraflara böler: her paragrafın sesteki başlangıç anı → out/voice-times.json.

voice.txt'te satır başına bir paragraf (açılış kartı, 9 sahne, kapanış). Whisper (faster-whisper) sesi kelime
zamanlarıyla yazıya döker; metin difflib ile hizalanır, paragrafın ilk kelimesinin başlangıcı alınır.
Whisper çalışmazsa FALLBACK (yerelde sessizlik ölçümüyle bulunan anlar) kullanılır.
"""
import difflib
import json
import os
import re
import sys

VOICE = ".github/video/voice.mp3"
TEXT = ".github/video/voice.txt"
# Paragraf 1..10'un başlangıcı (sn); voice.mp3 değişirse geçersizdir
FALLBACK = [5.41, 14.93, 29.65, 37.25, 50.40, 60.40, 68.91, 77.63, 85.27, 92.86]


def norm(w):
    return re.sub(r"[^a-z0-9çğıöşü]+", "", w.lower().replace("İ", "i").replace("I", "ı"))


def main():
    paras = [p.strip() for p in open(TEXT, encoding="utf-8") if p.strip()]
    starts, error = None, None
    try:
        from faster_whisper import WhisperModel
        model = WhisperModel("small", device="cpu", compute_type="int8")
        segs, _ = model.transcribe(VOICE, language="tr", word_timestamps=True, initial_prompt=" ".join(paras)[:800])
        heard = [(norm(w.word), w.start) for s in segs for w in s.words]
        heard = [h for h in heard if h[0]]
        script, first = [], []
        for p in paras:
            first.append(len(script))
            script += [n for n in (norm(w) for w in p.split()) if n]
        sm = difflib.SequenceMatcher(None, script, [h[0] for h in heard], autojunk=False)
        pairs = [(b.a + k, b.b + k) for b in sm.get_matching_blocks() for k in range(b.size)]
        print(f"hizalanan kelime: {len(pairs)}/{len(script)}")
        if len(pairs) < len(script) * 0.6:
            raise RuntimeError("hizalama zayıf")
        starts = []
        for i in first[1:]:
            # Paragrafın ilk eşleşen kelimesi; araya giren eşleşmemiş kelimeler kadar geriye kayar
            a, b = min((p for p in pairs if p[0] >= i), key=lambda p: p[0])
            j = max(b - (a - i), 0)
            starts.append(round(heard[j][1], 2))
        if any(y <= x for x, y in zip(starts, starts[1:])):
            raise RuntimeError(f"sıra bozuk: {starts}")
    except Exception as e:  # noqa: BLE001
        error = f"{type(e).__name__}: {e}"
        print("Whisper kullanılamadı, sabit süreler:", error)
        starts = FALLBACK
    os.makedirs("out", exist_ok=True)
    json.dump({"starts": starts, "whisper": error is None, "error": error}, open("out/voice-times.json", "w"))
    print("başlangıçlar:", starts)


if __name__ == "__main__":
    sys.exit(main())
