#!/bin/bash
# Çökme testi: APK'yı emülatöre kurar, tanıtımı ve ana ekranları gezer, belge/not/asistan açar.
# Uygulama çökerse (crash günlüğünde com.sounote.app) betik hata verir ve release yapılmaz.
set -x
P=com.sounote.app
mkdir -p shots
adb install -r a/SouNote.apk || exit 1
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
adb logcat -b crash -c || true
shot() { sleep 2; adb exec-out screencap -p > "shots/$1.png"; }
find_xy() {
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml /tmp/u.xml >/dev/null 2>&1
  python3 - "$1" <<'PY'
import sys,re,xml.etree.ElementTree as ET
t=sys.argv[1]
try:
    nodes=list(ET.parse('/tmp/u.xml').iter('node'))
except Exception:
    nodes=[]
for n in nodes:
    if n.get('text')==t or n.get('content-desc')==t:
        a=list(map(int,re.findall(r'\d+',n.get('bounds'))))
        if a[3]>a[1]: print((a[0]+a[2])//2,(a[1]+a[3])//2);break
PY
}
tap() {
  for k in 1 2 3 4; do
    xy=$(find_xy "$1"); if [ -n "$xy" ]; then adb shell input tap $xy; sleep 1; return 0; fi
    adb shell input swipe 540 1600 540 800 300; sleep 1
  done
  echo "bulunamadı: $1"
}
back() { adb shell input keyevent KEYCODE_BACK; sleep 1; }

# 1) Tanıtım
adb shell am start -W -n $P/.MainActivity; shot 01-tanitim
tap "İleri"; shot 01b-tanitim
tap "Atla"; sleep 2; tap "Başlayalım"; sleep 3; shot 02-ana

# 2) Sekmeler
for t in Kütüphane Dersler Sınavlar Ayarlar; do tap "$t"; shot "03-$t"; done
tap "Ana sayfa"
adb shell input swipe 540 1700 540 500 300; shot 04-ana-asagi

# 3) Hızlı not (belge ekranı), geri
adb shell am start -a $P.QUICK_NOTE -n $P/.MainActivity; sleep 4; shot 05-defter
adb shell input swipe 300 900 700 1200 400; sleep 1
back; back

# 4) Yazılı not, asistan, arama
tap "Ana sayfa"; tap "Yazılı not"; sleep 2; adb shell input text "deneme"; shot 06-yazili-not; back; back
tap "Ana sayfa"; tap "Asistan"; sleep 2; shot 07-asistan; back
tap "Ana sayfa"; tap "Ara"; sleep 2; adb shell input text "test"; sleep 2; shot 08-arama; back; back
tap "Ana sayfa"; tap "Ortalama"; sleep 2; shot 09-ortalama; back

# 5) Hızlı geçişler + döndürme
for t in Kütüphane Dersler Sınavlar Ayarlar "Ana sayfa" Dersler; do tap "$t"; done
adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1; sleep 3; shot 10-yatay
adb shell settings put system user_rotation 0; sleep 2

# Sonuç
adb logcat -b crash -d > shots/crash.txt || true
adb logcat -d | grep -E "AndroidRuntime|FATAL" > shots/runtime.txt || true
if grep -q "$P" shots/crash.txt; then
  echo "::error::Uygulama çöktü"; cat shots/crash.txt; exit 1
fi
if [ -z "$(adb shell pidof $P)" ]; then
  echo "::error::Uygulama kapanmış"; exit 1
fi
echo "Çökme yok"
