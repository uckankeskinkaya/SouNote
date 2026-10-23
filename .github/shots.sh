#!/bin/bash
set -x
P=com.sounote.app
mkdir -p shots
adb install -r a/SouNote.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
shot() { sleep 3; adb exec-out screencap -p > "shots/$1.png"; }
tap() { # metne (ya da açıklamaya) göre dokun
  adb shell uiautomator dump /sdcard/u.xml >/dev/null; adb pull /sdcard/u.xml /tmp/u.xml >/dev/null
  xy=$(python3 - "$1" <<'PY'
import sys,re,xml.etree.ElementTree as ET
t=sys.argv[1]
for n in ET.parse('/tmp/u.xml').iter('node'):
    if n.get('text')==t or n.get('content-desc')==t:
        a=list(map(int,re.findall(r'\d+',n.get('bounds'))));print((a[0]+a[2])//2,(a[1]+a[3])//2);break
PY
)
  echo "tap $1 -> $xy"; [ -n "$xy" ] && adb shell input tap $xy
}
scroll() { adb shell input swipe 540 1800 540 600 400; }
adb shell am start -n $P/.MainActivity; shot 01-tanitim
adb shell input swipe 900 1200 150 1200 300; shot 02-tanitim2
adb shell am force-stop $P
adb root; sleep 3
U=$(adb shell stat -c %u /data/data/$P | tr -d '\r')
adb shell "mkdir -p /data/data/$P/shared_prefs && cat > /data/data/$P/shared_prefs/viewer.xml <<'X'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><boolean name=\"onboarded\" value=\"true\" /><string name=\"userName\">Ayşe</string></map>
X
chown -R $U:$U /data/data/$P/shared_prefs; restorecon -R /data/data/$P/shared_prefs"
adb shell am start -n $P/.MainActivity; shot 03-ana
scroll; shot 04-ana-asagi
scroll; shot 05-ana-asagi2
tap "Kütüphane"; shot 06-kutuphane
tap "Dersler"; shot 07-dersler
scroll; shot 08-dersler-asagi
tap "Sınavlar"; shot 09-sinavlar
tap "Ayarlar"; shot 10-ayarlar
scroll; shot 11-ayarlar-asagi
tap "Ana sayfa"; sleep 1; tap "Asistan"; shot 12-asistan
adb shell input text "merhaba"; shot 13-asistan-yazi
adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -a $P.QUICK_NOTE -n $P/.MainActivity; sleep 3; shot 14-defter
adb shell input keyevent KEYCODE_BACK; sleep 2
tap "Yazılı not"; shot 15-yazili-not
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK
tap "Yeni defter"; shot 16-yeni-defter
adb logcat -d | grep -iE "AndroidRuntime|FATAL" | tail -40 > shots/crash.txt || true
ls -la shots
