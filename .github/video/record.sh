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
# Ana sayfanın en üstüne dön (her sahneden önce)
home_top() {
  adb shell am start -n $P/.SouNoteActivity >/dev/null; sleep 1.5
  tap "Ana sayfa"
  adb shell input swipe 540 700 540 2000 150; adb shell input swipe 540 700 540 2000 150; sleep 1.5
}
# Ana sayfadaki süzülen animasyon uiautomator'ı bekletiyor: konumları animasyonlar kapalıyken bir kez oku
anim() { for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k $1; done; }
declare -A POS
learn_home() {
  anim 0; home_top; sleep 1
  for t in "Ortalama" "Writing" "Belgelerde*" "Yeni defter"; do POS[$t]=$(find_xy "$t"); echo "konum $t: ${POS[$t]}"; done
  anim 1
}
tap_home() { if [ -n "${POS[$1]}" ]; then adb shell input tap ${POS[$1]}; sleep 1; else tap "$1"; fi; }
slow_scroll() { adb shell input swipe 540 1700 540 ${2:-900} ${1:-1200}; sleep 0.8; }
# Parmakla çizim: noktalar listesi "x,y x,y ..." (motionevent ile eğri)
draw() {
  set -- $1
  first=$1; shift
  adb shell input motionevent DOWN ${first%,*} ${first#*,}
  for p in "$@"; do adb shell input motionevent MOVE ${p%,*} ${p#*,}; done
  last=${!#}; adb shell input motionevent UP ${last%,*} ${last#*,}
}

# Not: tema değişince ana ekran simgesi başka takma ada geçer (.MainActivity kapanabilir); hep asıl ekranla aç.
A=$P/.SouNoteActivity
back() { adb shell input keyevent KEYCODE_BACK; sleep 0.8; }

# 1) Tanıtım (ilk açılış; tema seçimi dahil)
adb shell am start -W -n $A; sleep 3
rec 01-tanitim 11
sleep 1.5; tap "İleri"; sleep 2; tap "İleri"; sleep 2; tap "İleri"; sleep 2.5
stop 01-tanitim

# Adı olan bir kullanıcıyla devam (tanıtım atlanır), koyu tema
adb shell am force-stop $P
adb root; sleep 3
U=$(adb shell stat -c %u /data/data/$P | tr -d '\r')
adb shell "mkdir -p /data/data/$P/shared_prefs && cat > /data/data/$P/shared_prefs/viewer.xml <<'X'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><boolean name=\"onboarded\" value=\"true\" /><string name=\"userName\">Ayşe</string><string name=\"theme\">DARK</string><int name=\"seenMajor\" value=\"9\" /><int name=\"seenBuild\" value=\"99999\" /><long name=\"backupNudgeUntil\" value=\"9999999999999\" /><long name=\"updateChecked\" value=\"9999999999999\" /></map>
X
cat > /data/data/$P/shared_prefs/assistant.xml <<'X'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><string name=\"baseUrl\">https://example.invalid/v1</string><string name=\"model\">demo</string><string name=\"apiKey\">demo</string></map>
X
chown -R $U:$U /data/data/$P/shared_prefs; restorecon -R /data/data/$P/shared_prefs"
adb shell am start -W -n $A; sleep 5

# 2) Defter: parmakla yaz + çizim (yeni kalemler, pürüzsüz çizgi)
adb shell am start -a $P.QUICK_NOTE -n $A; sleep 4
tap "Parmakla yaz kapalı"; sleep 1
rec 02-defter 13
sleep 1
draw "200,700 200,760 200,820 200,880 200,940"
draw "200,820 240,820 280,820"
draw "280,700 280,760 280,820 280,880 280,940"
draw "340,800 340,860 340,940"
draw "340,730 342,735"
draw "160,1040 220,1010 280,1040 340,1070 400,1040 460,1010 520,1040 580,1070 640,1040"
draw "780,800 740,750 690,760 680,820 720,880 780,940 840,880 880,820 870,760 820,750 780,800"
draw "560,760 600,820 700,690"
sleep 1.5
stop 02-defter
back; sleep 1

# 3) Ana sayfa (kaldığın yer, yaklaşanlar, son kullanılanlar)
home_top
rec 03-ana 11
sleep 2; slow_scroll 1500 1000; sleep 1.5; slow_scroll 1500 1000; sleep 1.5; slow_scroll 1500 1000; sleep 1
stop 03-ana

# 4) Temalar: Ayarlar → Görünüm → 6 tema
home_top
tap "Ayarlar"; sleep 1.5; tap "Görünüm"; sleep 2
rec 04-temalar 13
sleep 1.5; tap "Safir"; sleep 1.8; tap "Zümrüt"; sleep 1.8; tap "Gül"; sleep 1.8; tap "Gümüş"; sleep 1.8; tap "Elektrik"; sleep 2
stop 04-temalar
adb shell am start -n $A >/dev/null; sleep 2; back; back

# 5) Dersler → Haftalar
home_top
tap "Dersler"; sleep 2.5
rec 05-dersler 11
sleep 1.5; slow_scroll 1500 1100; sleep 1.5; tap "Haftalar"; sleep 2.5; slow_scroll 1500 900; sleep 2
stop 05-dersler

# 6) Konu haritası
tap "Konular"; sleep 1.5; adb shell input swipe 540 700 540 1900 150; sleep 1
tap "Grammar"; sleep 2
rec 06-harita 9
sleep 1.5; tap "Konu haritası"; sleep 4
stop 06-harita
back; back

# 7) Planlayıcı (hafta görünümü)
home_top
tap "Planlayıcı"; sleep 2
rec 07-planlayici 9
sleep 1.5; tap "Hafta"; sleep 2.5; tap "Liste"; sleep 2
stop 07-planlayici

# 8) Yazı stüdyosu
home_top
tap "Yazı"; sleep 2
rec 08-yazi 12
sleep 1; tap "Yeni essay"; sleep 2.5
adb shell input text "Nowadays%smany%syoung%speople%sspend%shours%son%ssocial%smedia.%sIn%smy%sopinion,%sit%sdoes%smore%sharm%sthan%sgood."
sleep 3
stop 08-yazi
back; back; back

# 9) Asistan (sahte anahtarla: kurulum yerine sohbet ekranı ve öneriler; mesaj gönderilmez)
adb shell am start -a $P.OPEN_ASSISTANT -n $A; sleep 3
rec 09-asistan 10
sleep 2; tap "Mesaj yaz…"; sleep 1
adb shell input text "Present%sPerfect%sile%sPast%sSimple%sfarki%sne?"
sleep 4
stop 09-asistan
back; back; back

# 10) Arama
home_top
rec 10-arama 10
tap "Ara"; sleep 2.5; tap "Ders, belge, not, sınav ara*"; adb shell input text "grammar"; sleep 4
stop 10-arama

ls -la scenes
