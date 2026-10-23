#!/bin/bash
adb install a/SouNote.apk
adb logcat -c
for seed in 1 7 42 99; do
  adb shell monkey -p com.sounote.app --pct-syskeys 0 --pct-appswitch 2 --throttle 100 -s $seed -v 3000 > m$seed.txt 2>&1
  echo "== seed $seed: $(grep -c 'CRASH' m$seed.txt) crash, $(tail -n 1 m$seed.txt)"
done
echo "==== CRASH BUFFER ===="
adb logcat -d -b crash | head -200
echo "==== APP ERRORLOG ===="
adb shell run-as com.sounote.app sh -c 'cat files/logs/* 2>/dev/null' | tail -n 80
exit 0
