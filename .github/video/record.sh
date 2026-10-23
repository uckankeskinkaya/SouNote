#!/bin/bash
# Tanıtım videosu için sahne kayıtları (emülatör). Çıktı: scenes/NN-ad.mp4
set -x
P=com.sounote.app
mkdir -p scenes
adb install -r a/SouNote.apk || exit 1
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
# Animasyonlar açık kalsın (video için)
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1

find_xy() {
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml /tmp/u.xml >/dev/null 2>&1
  python3 - "$1" <<'PY'
import sys,re,xml.etree.ElementTree as ET
t=sys.argv[1]
try: nodes=list(ET.parse('/tmp/u.xml').iter('node'))
except Exception: nodes=[]
for n in nodes:
    if n.get('text')==t or n.get('content-desc')==t or (t.endswith('*') and (n.get('text') or '').startswith(t[:-1])):
        a=list(map(int,re.findall(r'\d+',n.get('bounds'))))
        if a[3]>a[1]: print((a[0]+a[2])//2,(a[1]+a[3])//2);break
PY
}
tap() {
  for k in 1 2 3; do
    xy=$(find_xy "$1"); if [ -n "$xy" ]; then adb shell input tap $xy; sleep 1; return 0; fi
    adb shell input swipe 540 1600 540 900 300; sleep 1.5
  done
  echo "bulunamadı: $1"
}
# Kayıt: rec ad süre  →  arka planda screenrecord; stop ile biter
rec() { adb shell screenrecord --size 720x1600 --bit-rate 8000000 --time-limit "$2" "/sdcard/$1.mp4" & RECPID=$!; sleep 1; }
stop() { wait $RECPID; sleep 1; adb pull "/sdcard/$1.mp4" "scenes/$1.mp4"; }
slow_scroll() { adb shell input swipe 540 1700 540 ${2:-900} ${1:-1200}; sleep 0.8; }
# Parmakla çizim: noktalar listesi "x,y x,y ..." (motionevent ile eğri)
draw() {
  set -- $1
  first=$1; shift
  adb shell input motionevent DOWN ${first%,*} ${first#*,}
  for p in "$@"; do adb shell input motionevent MOVE ${p%,*} ${p#*,}; done
  last=${!#}; adb shell input motionevent UP ${last%,*} ${last#*,}
}

# 1) Tanıtım (ilk açılış)
adb shell am start -W -n $P/.MainActivity; sleep 3
rec 01-tanitim 9
sleep 2; tap "İleri"; sleep 2; tap "İleri"; sleep 2
stop 01-tanitim

# Adı olan bir kullanıcıyla devam (tanıtım atlanır)
adb shell am force-stop $P
adb root; sleep 3
U=$(adb shell stat -c %u /data/data/$P | tr -d '\r')
adb shell "mkdir -p /data/data/$P/shared_prefs && cat > /data/data/$P/shared_prefs/viewer.xml <<'X'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><boolean name=\"onboarded\" value=\"true\" /><string name=\"userName\">Ayşe</string><int name=\"seenMajor\" value=\"9\" /><int name=\"seenBuild\" value=\"99999\" /><long name=\"backupNudgeUntil\" value=\"9999999999999\" /><long name=\"updateChecked\" value=\"9999999999999\" /></map>
X
chown -R $U:$U /data/data/$P/shared_prefs; restorecon -R /data/data/$P/shared_prefs"
adb shell am start -W -n $P/.MainActivity; sleep 4

# 2) Ana sayfa
rec 02-ana 12
sleep 2; slow_scroll 1500 1000; sleep 1.5; slow_scroll 1500 1000; sleep 1.5; slow_scroll 1500 1000; sleep 1
stop 02-ana
adb shell input swipe 540 600 540 2000 200; adb shell input swipe 540 600 540 2000 200; sleep 1

# 3) Dersler → Haftalar
tap "Dersler"; sleep 2
rec 03-dersler 11
sleep 2; slow_scroll 1500 1100; sleep 1.5; tap "🗓️ Haftalar"; sleep 3
stop 03-dersler

# 4) Sınavlar
tap "Sınavlar"; sleep 2
rec 04-sinavlar 8
sleep 2; slow_scroll 1500 1000; sleep 2
stop 04-sinavlar

# 5) Defter: parmakla yaz + çizim
tap "Ana sayfa"; sleep 1
adb shell am start -a $P.QUICK_NOTE -n $P/.MainActivity; sleep 4
tap "Parmakla yaz kapalı"; sleep 1
rec 05-defter 14
sleep 1
# "Hi" + dalga + kalp + onay işareti
draw "200,700 200,760 200,820 200,880 200,940"
draw "200,820 240,820 280,820"
draw "280,700 280,760 280,820 280,880 280,940"
draw "340,800 340,860 340,940"
draw "340,730 342,735"
draw "160,1040 220,1010 280,1040 340,1070 400,1040 460,1010 520,1040 580,1070 640,1040"
draw "780,800 740,750 690,760 680,820 720,880 780,940 840,880 880,820 870,760 820,750 780,800"
draw "560,760 600,820 700,690"
sleep 1.5
stop 05-defter
adb shell input keyevent KEYCODE_BACK; sleep 2

# 6) Writing
tap "Ana sayfa"; sleep 1
rec 06-writing 16
tap "Writing"; sleep 2.5; tap "Opinion Essay"; sleep 2.5
tap "Social media does more harm than good to young people. Do you agree?"; sleep 2
adb shell input text "Nowadays%smany%syoung%speople%sspend%shours%son%ssocial%smedia.%sIn%smy%sopinion,%sit%sdoes%smore%sharm%sthan%sgood."
sleep 3
stop 06-writing
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1

# 7) Ortalama
adb shell am start -n $P/.MainActivity; sleep 2; tap "Ana sayfa"; sleep 1
rec 07-ortalama 10
tap "Ortalama"; sleep 2; tap "Bu hesabı kur"; sleep 2; slow_scroll 1500 1000; sleep 2
stop 07-ortalama
adb shell input keyevent KEYCODE_BACK; sleep 1

# 8) Arama
adb shell am start -n $P/.MainActivity; sleep 2; tap "Ana sayfa"; sleep 1
rec 08-arama 8
tap "Ara"; sleep 2; adb shell input text "not"; sleep 3
stop 08-arama

ls -la scenes
