#!/usr/bin/env python3
"""Katalogdaki Word/PowerPoint kaynaklarını PDF'e çevirip "kaynaklar" sürümüne <driveId>.pdf olarak yükler.

Uygulama bu PDF'leri releases/download/kaynaklar/<driveId>.pdf adresinden indirir (Catalog.kt Resource.pdfUrl).
Daha önce çevrilmiş olanlar atlanır; herkese açık olmayan ya da bozuk dosyalar raporlanır, iş durmaz.
"""
import json, os, re, subprocess, sys, tempfile, urllib.request

REPO = os.environ["GITHUB_REPOSITORY"]
TAG = "kaynaklar"
CONVERTIBLE = {"pptx", "docx"}


def gh(*args, check=True):
    return subprocess.run(["gh", *args, "-R", REPO], capture_output=True, text=True, check=check)


def drive_id(url):
    m = re.search(r"drive\.google\.com/(?:file/d/|open\?id=|uc\?(?:export=download&)?id=)([A-Za-z0-9_-]{10,})", url)
    return m.group(1) if m else None


catalog = json.load(open("app/src/main/assets/catalog.json"))
wanted = {}
for course in catalog["courses"]:
    for topic in course["topics"]:
        for r in topic["resources"]:
            if r["kind"] in CONVERTIBLE and (i := drive_id(r["url"])):
                wanted[i] = (r["kind"], r["title"])

if gh("release", "view", TAG, check=False).returncode != 0:
    gh("release", "create", TAG, "--prerelease", "--latest=false", "--title", "Ders kaynakları (PDF)",
       "--notes", "Katalogdaki Word/PowerPoint dosyalarının PDF'e çevrilmiş halleri (otomatik güncellenir).")
have = {a["name"] for a in json.loads(gh("release", "view", TAG, "--json", "assets").stdout)["assets"]}

todo = {i: v for i, v in wanted.items() if f"{i}.pdf" not in have}
print(f"Toplam {len(wanted)} Word/PPT, çevrilecek {len(todo)}")
work = tempfile.mkdtemp()
out = os.path.join(work, "out")
os.makedirs(out)
failed = []
for i, (kind, title) in todo.items():
    src = os.path.join(work, f"{i}.{kind}")
    try:
        req = urllib.request.Request(
            f"https://drive.usercontent.google.com/download?id={i}&export=download&confirm=t",
            headers={"User-Agent": "Mozilla/5.0"},
        )
        with urllib.request.urlopen(req, timeout=120) as resp, open(src, "wb") as f:
            f.write(resp.read())
        with open(src, "rb") as f:
            if f.read(2) != b"PK":
                raise RuntimeError("Office dosyası değil (herkese açık paylaşılmamış olabilir)")
        subprocess.run(["soffice", "--headless", "--convert-to", "pdf", "--outdir", out, src],
                       check=True, capture_output=True, timeout=300)
        pdf = os.path.join(out, f"{i}.pdf")
        if not os.path.exists(pdf) or os.path.getsize(pdf) < 1000:
            raise RuntimeError("PDF oluşmadı")
        print(f"✓ {title}")
    except Exception as e:  # noqa: BLE001 — bir dosya yüzünden diğerleri durmasın
        failed.append(f"{title} ({i}): {e}")
        print(f"✗ {title}: {e}")

pdfs = [os.path.join(out, n) for n in os.listdir(out) if n.endswith(".pdf")]
for k in range(0, len(pdfs), 20):
    gh("release", "upload", TAG, *pdfs[k:k + 20], "--clobber")
print(f"Yüklendi: {len(pdfs)}, başarısız: {len(failed)}")
with open(os.environ.get("GITHUB_STEP_SUMMARY", os.devnull), "a") as s:
    s.write(f"Çevrilen: {len(pdfs)} · Başarısız: {len(failed)}\n\n" + "".join(f"- {x}\n" for x in failed))
if todo and not pdfs:
    sys.exit("Hiçbir dosya çevrilemedi")
