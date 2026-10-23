#!/bin/bash
set -x
P=com.sounote.app
mkdir -p shots
adb install -r a/SouNote.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
# Zor telefon: büyük yazı (1.3) ve 360dp genişlik
adb shell settings put system font_scale 1.3
adb shell wm density 480
sleep 2
shot() { sleep 3; adb exec-out screencap -p > "shots/$1.png"; }
find_xy() {
  adb shell uiautomator dump /sdcard/u.xml >/dev/null; adb pull /sdcard/u.xml /tmp/u.xml >/dev/null
  python3 - "$1" <<'PY'
import sys,re,xml.etree.ElementTree as ET
t=sys.argv[1]
for n in ET.parse('/tmp/u.xml').iter('node'):
    if n.get('text')==t or n.get('content-desc')==t:
        a=list(map(int,re.findall(r'\d+',n.get('bounds'))))
        if a[3]>a[1] and a[1]>150 and a[3]<2390: print((a[0]+a[2])//2,(a[1]+a[3])//2);break
PY
}
tap() { # metne göre dokun; görünmüyorsa aşağı kaydırıp ara
  for k in 1 2 3 4 5; do
    xy=$(find_xy "$1"); if [ -n "$xy" ]; then adb shell input tap $xy; echo "tap $1 -> $xy"; return 0; fi
    adb shell input swipe 540 1700 540 900 400; sleep 1
  done
  echo "BULUNAMADI: $1"
}
scroll() { adb shell input swipe 540 1800 540 600 400; }
adb shell am start -n $P/.MainActivity; shot 01-tanitim
adb shell input swipe 900 1200 150 1200 300; shot 02-tanitim2
adb shell am force-stop $P
adb root; sleep 3
U=$(adb shell stat -c %u /data/data/$P | tr -d '\r')
adb shell "mkdir -p /data/data/$P/shared_prefs && cat > /data/data/$P/shared_prefs/viewer.xml <<'X'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><boolean name=\"onboarded\" value=\"true\" /><string name=\"userName\">Ayşe Yılmaz</string></map>
X
chown -R $U:$U /data/data/$P/shared_prefs; restorecon -R /data/data/$P/shared_prefs"
adb shell am start -n $P/.MainActivity; shot 03-ana
scroll; shot 04-ana-asagi
scroll; shot 05-ana-asagi2
scroll; shot 06-ana-asagi3
tap "Kütüphane"; shot 07-kutuphane
tap "Dersler"; shot 08-dersler
tap "🗓️ Haftalar"; shot 09-haftalar
tap "Sınavlar"; shot 10-sinavlar
tap "Ayarlar"; shot 11-ayarlar
scroll; shot 12-ayarlar-asagi
scroll; shot 13-ayarlar-asagi2
tap "Ana sayfa"; sleep 1; tap "Asistan"; shot 14-asistan
adb shell input keyevent KEYCODE_BACK; sleep 1
tap "Ana sayfa"; sleep 1; tap "Yazılı not"; shot 15-yazili-not
adb shell input text "Merhaba%sdunya"; shot 16-yazili-not-yazi
adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -a $P.QUICK_NOTE -n $P/.MainActivity; sleep 4; shot 17-defter
adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Ana sayfa"; sleep 1; tap "Yeni defter"; shot 18-yeni-defter
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell settings put system user_rotation 1; adb shell settings put system accelerometer_rotation 0; sleep 3; shot 19-yatay
tap "Ana sayfa"; for t in Kütüphane Dersler Sınavlar Ayarlar Ana\ sayfa Sınavlar Kütüphane; do tap "$t"; sleep 0.3; done; shot 20-hizli-gecis
adb shell am start -a $P.QUICK_NOTE -n $P/.MainActivity; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 0.2; adb shell input keyevent KEYCODE_BACK; sleep 2; shot 21-ac-kapa
adb logcat -d | grep -E "FATAL|AndroidRuntime: (java|Caused)" | tail -40 > shots/crash.txt || true
ls -la shots
