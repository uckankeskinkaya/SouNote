# Proje: Kalemle PDF not uygulaması (StarNote benzeri)

Kullanıcıyla Türkçe konuş. Kısa ve net ol.

## Amaç
Ders notlarımı (PDF, Word) içe aktarıp üstünde kalemle çalışacağım kişisel bir Android uygulaması. Ücretli uygulama almak yerine kendim yapıyorum.
Referans: https://play.google.com/store/apps/details?id=com.onyx.galaxy.global.note (StarNote) — incele, temel deneyimi hedefliyoruz. (AI başta kapsam dışıydı; kullanıcı sonradan yardımcı asistan istedi.)

## Ortam
- Cihaz: Xiaomi Pad 7 Pro (Android 16 / HyperOS 3) + kalem. Bilgisayar yok, her şey tablette.
- Kod tablette yazılır, derleme GitHub Actions'ta yapılır, APK indirilip kurulur.
- Termux'ta yerel Gradle derlemesi şimdilik yok (aapt2 sorunları). AndroidIDE arşivlendi; gerekirse ileride Code on the Go düşünülebilir.

## Teknik kararlar
- Kotlin + native Android (web/Flutter/Expo/FlutterFlow değerlendirildi, kalem akıcılığı yüzünden elendi).
- PDF görüntüleme: Android PdfRenderer.
- Düşük gecikmeli mürekkep: androidx low-latency/ink kütüphanelerini değerlendir.
- Varsayılan: sadece kalem (stylus toolType) çizer; parmak kaydırır ve yakınlaştırır (avuç içi sorunu). "Parmakla çiz" ayarı açılınca tek parmak çizer.
- Notlar cihazda saklanır; işaretli PDF olarak dışa aktarılabilir.
- Word dosyaları uygulamada açılmaz, önce PDF'e çevrilir.

## Yol haritası
(✅ tablette doğrulandı · 🧪 derlendi, tablette test bekliyor)
3–8 ve ek özellikler: kullanıcı tablette kullandı, sorun bildirmedi (build-16).
1. ✅ Boş proje + GitHub Actions workflow'u → APK derlenip tablete kurulsun (Releases'tan indirilir).
2. ✅ PDF içe aktar (tek dosya + "klasör bağla": bağlı klasördeki PDF'ler otomatik görünür, alt klasörler klasör olur), kütüphane ızgarası, sayfaları göster, kaydır/yakınlaştır (yakınlaşınca net).
3. ✅ Kalem çekirdeği: basınca duyarlı kalem, hassas kalınlık (0.1 mm), silgi (çizgi + hassas), fosforlu, hazır renkler. "Parmakla çiz" anahtarı (açıkken tek parmak çizer, iki parmak kaydırır/yakınlaştırır; kalemsiz arkadaşlar için).
4. ✅ Otomatik kayıt (bağlı klasördeki PDF'e ilk not alınınca kopyası uygulamaya alınır), geri al/ileri al, metin kutusu (klavyeyle yazı; taşı, iki parmakla ya da köşe tutamacıyla büyüt/küçült, renk), PDF form alanlarını doldurma (Android 15+ PdfRenderer form API'si), işaretli PDF olarak dışa aktar.
5. ✅ Gelişmiş kalem: kalem türleri (dolma/tükenmez/kurşun/fırça/kaligrafi/kesik), favori kalemler, basınç hassasiyeti, şekil düzeltme (bekletince çizgi/daire/dikdörtgen/üçgen), düz fosforlu, tam renk seçici.
6. ✅ Düzenleme: lasso (taşı/boyutlandır/renk/kopyala-yapıştır/sil; parmakla sürükle, iki parmakla büyüt), resim ekleme (galeriden; ink/<belge>/img/).
7. ✅ Sayfalar: sayfa paneli (küçük resimler, ekle/sil/taşı), boş defter + şablonlar (düz/çizgili/kareli/noktalı), araya boş sayfa, yer imleri, PDF içinde arama (Android 15+), yatayda çift sayfa.
8. ✅ Kütüphane: kendi klasörlerim (iç içe, renkli; belge/klasör taşı), arama, sıralama (son açılan/eklenen/ad), yedekle/geri yükle (zip: library.json + docs + ink), tema seçimi (sistem/açık/koyu).

Teknik notlar:
- Notlar sayfa noktası (PDF pt, sol üst köşe) cinsinden: filesDir/ink/<belgeId>/<sayfa>.bin (InkStore, ikili biçim).
- Bağlı klasördeki belgenin kopyası filesDir/docs/<belgeId>.pdf; form doldurulunca PdfRenderer.write ile bu dosyaya yazılır.
- Sayfa ekle/sil/taşı PDF'in kendisinde yapılır (PdfPages, PDFBox); notlar InkStore.remapPages ile yeni numaralara taşınır, geri al geçmişi sıfırlanır.
- Dışa aktarma: PDFBox-Android (com.tom-roush:pdfbox-android) ile orijinal PDF'e ek içerik akışı; metin kutuları yol olarak.
- Hata kaydı: ErrorLog (filesDir/logs). Çökme olursa sonraki açılışta paylaşma penceresi; Ayarlar → "Hata raporu gönder". Kullanıcıdan gelen raporla hata ayıkla.
- Düşük gecikme (deneysel, kalem ayarlarında): androidx.graphics CanvasFrontBufferedRenderer; çizilen çizgi ön tampona, bitince görünüme geçer (FrontInk, PdfContainer).
- Bu ortamda Android SDK ve Google Maven yok; API'leri doğrulamak gerekirse CI'da javap ile keşif iş akışı çalıştırılabilir.

Ek özellikler (✅): ekran açık kalır, tam ekran, sayfayı PNG paylaş, PDF bağlantıları (Android 15+), kapakta notlar,
kaynağı kaybolan notlu belgeler bölümü (docs/<id>.json), 3 parmak = ileri al, sayfaya git, otomatik yedek (SAF klasörü, 2 dönüşümlü zip),
ezber bandı (InkTool.TAPE; parmakla dokun aç/kapat, dışa aktarılmaz), lazer kalem (kaydedilmez), ders ses kaydı (ink/<belge>/audio/*.m4a),
yazı kalın + arka plan (InkStore v3), silgi yazı/resim de silebilir, Cornell şablonu, defter boyutu/yönü, kütüphanede Son açılanlar.

Tema sabit (dinamik renk yok): HyperOS dinamik renkleri kontrastı bozuyordu.

Arayüz dili Türkçe. Paket: com.sounote.app. compile/targetSdk 36, minSdk 29.
İmza anahtarı keystore/sounote.jks — değiştirme, yoksa güncelleme kurulmaz ve notlar gider.

Yeni iş için: yeni özellikler 🧪 olarak eklenir; kullanıcı hata raporu gönderirse önce onu düzelt.
Eklendi (🧪, build-17+): seçimi döndürme (lasso üst tutamaç; TextBox/ImageItem.rotation, InkStore v4), akıllı fosforlu (Android 15+ metin alanları,
bulunamazsa normal), belge etiketleri + filtre (library.json tags), çalışma süresi (belge başına + günlük, prefs) ve istatistik, pomodoro (25/5, 50/10),
PDF içindekiler (PDFBox outline, sayfa panelinde), sayfayı notlarıyla çoğalt, dışa aktarmada sayfa aralığı.
Eklendi (🧪, build-18+): bilgi kartları (lasso → Karta çevir, Leitner 1–5 kutu; ink/<belge>/cards.json + cards/), damga aracı (✓✗★…; yazı kutusu olarak),
sağ kenarda sayfa kaydırıcı, gece okuma (renk ters çevirme), hızlı not + uygulama kısayolları (son açılanlar dinamik), başka PDF'ten sayfa ekle,
şablonlar: milimetrik, müzik, izometrik.
Eklendi (🧪, build-19+): sesle eşleşen notlar (kayıt sürerken çizilen çizgi Stroke.audioFile/audioMs, InkStore v5; dokununca kayıt o andan çalar),
çöp kutusu (silinen belge library.json deletedAt, 30 gün sonra kalıcı silinir), şekil aracı (çizgi/ok/dikdörtgen/elips/üçgen; Tool.SHAPE, Shapes),
yazdır (PrintManager, notlu PDF), uygulama kilidi (platform BiometricPrompt, açılışta + 60 sn arka plan sonrası; prefs appLock),
defter kâğıt rengi (PaperColor; araya eklenen sayfa da aynı renk, prefs paper_<id>), klavye kısayolları (Ctrl+Z/Y/Shift+Z, PageUp/Down, Ctrl+F/P/S, Ctrl+Home/End).
Eklendi (🧪, build-21+): ana menü (MainShell: Ana sayfa / Kütüphane / Ayarlar; tablette NavigationRail, dar ekranda NavigationBar; sekme VM.tab),
ilk açılışta tanıtım (OnboardingScreen, 5 sayfa: ad, kalem/parmak, tema; prefs onboarded, userName), ana sayfa (karşılama, kaldığın yer, hızlı başla,
son açılanlar, haftalık çalışma, ipucu), ayarlar sayfası (kütüphanedeki ⋮ menüsü buraya taşındı), arkadaşla paylaş (indirme bağlantısı + APK dosyası),
yeni simge (gradyan zemin, temalı tek renk), açılış ekranı (values-v31 windowSplashScreen*). Release'ler artık Latest: releases/latest/download/SouNote.apk.
APK'da dex sıkıştırılmış (packaging.dex.useLegacyPackaging): sıkıştırılmamış classes.dex tablete inerken bozuluyordu (CRC hatası, build-20..22).
Eklendi (🧪, build-24+): yazılı notlar (klavyeyle; TextNoteStore filesDir/texts/<id>.json, yedeğe girer; yapılacak/madde listesi, tarih,
yazı boyutu, sabitle, metin/PDF paylaş, "Deftere çevir" = PDF olarak kütüphaneye ekler; TextNoteScreen, VM.openText).

Eklendi (🧪, build-27+): renk temaları (ColorTheme, prefs colorTheme), ders klasörü kapakları (UserFolder.icon emoji), Drive'a yedek (paylaş → Drive),
dışa aktarmada "sadece notlu / yer imli sayfalar".
Eklendi (🧪, build-29+): Dersler ve Sınavlar sekmeleri. Ortak katalog app/src/main/assets/catalog.json (uygulamayla gelir; repo gizli olduğu için
GitHub'dan okunamıyor, Drive bağlayıcısının yazma izni yok). Katalog: Drive "Ingilizce calismalar" klasöründeki 82 dosya alan/konuya ayrıldı
(Grammar, Vocabulary, Reading, Listening, Writing, Speaking, Ünite, Sınav hazırlık) + 2026-27 güz sınav takvimi (L1–L4, ders saatleri:
4. ders Cuma 11:45 diğer günler 12:45, 5. ders 13:40). Kur seçimi (prefs userLevel), kendi sınavların (filesDir/exams.json, yedeğe girer),
hatırlatıcılar (AlarmManager; 3 gün/1 gün önce 19:00, 2 saat önce), PDF kaynağı "İndir" → kütüphanede alan adlı klasöre.
Drive dosyaları herkese açık değilse indirme/açma sadece sahibinde çalışır. Katalog üretici betik: scratchpad catalog.py (yeniden üretmek için Drive listesi).


Eklendi (🧪, build-34+): yazılı notta çizim alanı (gövdede \uFFFCsketch:<id> satırı; filesDir/texts/sketch/<id>.json; PDF'e de çizilir)
ve kalemle yazma paneli (ML Kit digital-ink-recognition 18.1.0, "tr" modeli ilk kullanımda indirilir).

Eklendi (🧪, build-36+): yardımcı asistan (AssistantDialog; OpenAI uyumlu /chat/completions, ayarlar cihazda prefs "assistant":
adres/model/anahtar, anahtar APK'ya gömülmez). Sistem talimatı uygulama özelliklerini, L4 konularını ve yaklaşan sınavları içerir;
belgeden açılınca PDF metni (PDFBox PDFTextStripper; bu sayfa / tüm belge), yazılı nottan açılınca not metni bağlam olarak gider.
Varsayılan servis Evren (SSYZ, e-devlet): https://evren-llmapi.ssyz.org.tr/v1, model "auto", anahtar evren_llm_…;
ilk kullanımda 403 terms_not_accepted → /v1/terms/text gösterilir, kabul edilirse POST /v1/terms/accept {"version"} (build-37+).
Eklendi (🧪, build-42+): asistan ekranı yenilendi (balonlar, Markdown gösterimi ui/Markdown.kt, yazıyor animasyonu, öneri kartları).
Telefon düzeni: CI'da pixel_6 emülatörde 1.3 yazı + 360dp ile ekran görüntüsü alındı (geçici probe.yml + .github/shots.sh, git geçmişinde;
görüntüler shots-tmp dalına itilir). Dar ekranda: sınav kartında geri sayım alta, karşılama logosu gizli, derslerde 2 sütun, sekme yazısı tek satır.
Eklendi (🧪, build-48+): animasyonlar (ui/Motion.kt: pressScale, appear/Appear sırayla belirme, sekme ve ekran geçişleri MainActivity Screen + AnimatedContent),
süzülen karşılama daireleri, büyüyen haftalık çubuklar, kapak Crossfade, listelerde animateItem, yaklaşan (≤2 gün) sınav nabzı.
Belge ekranındaki paneller bilerek animasyonsuz (PDF görünümü yeniden boyutlanıp takılmasın).
Sürüm 1.0 (🧪, build-51+; repo artık herkese açık): versionName 1.0.<run>; güncelleme denetimi (Updates.kt, GitHub releases/latest API, 6 saatte bir;
release notu = commit mesajı, notes.md) + "Yenilikler" penceresi (UpdateDialogs.kt, prefs seenMajor); katalog her release'e catalog.json olarak
eklenir, uygulama releases/latest/download/catalog.json'dan çeker (main geride olduğu için raw main kullanılmadı); build.yml concurrency: release.
Ana sayfada otomatik yedek önerisi (7 gün ertelenir), ana ekran widget'ı (ExamWidget, RemoteViews; sınav/çalışma değişince updateAll),
asistanda "🃏 Kart üret" (generateCards → JSON [{q,a}], önizle → addGeneratedCards), haftalık liste (prefs weekDone StringSet; Dersler/Haftalar'da
onay kutuları + ilerleme, ana sayfada "Hafta N listesi"), not paylaşma: .sounote paketi (SouNotePackage: manifest.json + document.pdf + ink/, ses hariç;
VIEW/SEND octet-stream/zip → vm.importShared; yeni kimlikle createDocument).
Eklendi (🧪, build-60+): genel arama (SearchIndex: filesDir/search/<id>-<stamp>.txt, sayfalar \f ile; SearchDialog; ana sayfada arama çubuğu),
not ortalaması (Grades/GradeStore filesDir/grades.json, yedeğe girer; GradesDialog; Ankara Ü. şablonu: kur içi %40 [Quiz 10, PT 20, Quiz 10, PT 20,
Sözlü 10+10, Yazma 10+10] + Kur Atlama Sınavı %60 [LRU 60, Yazma 20, Sözlü 20], geçme 70; Güz Yeterlik ayrıca 70; kaynak: Öğrenci El Kitabı 2026-2027).
CI (build.yml): build → release (test beklemeden, ~4 dk) ve paralel smoke (emülatör, AVD snapshot önbelleği, .github/smoke.sh); smoke başarısızsa
rollback işi sürümü ön sürüme çeker. Gradle cache-read-only: false. Derleme sırasında dal ilerlediyse release atlanır (GITHUB_TOKEN eski commit'e
etiket koyamıyor: "workflows" izni ister). Smoke ekran görüntüleri "screenshots" dalına itilir.
İndirme sayfası docs/index.html (+ docs/img), GitHub Pages: Settings → Pages → main /docs (kullanıcı açmalı). README yenilendi.

## Devam eden iş (sonraki oturum)
- Drive düzeni (yapıldı, 23 Ekim): "Ingilizce calismalar" (1f2chdgREDpdIpHoKh4I43oeBRC0tlo8q) altında hafta klasörleri:
  Hafta 01 1_LtxFdJkk_niIhFy6Yb51kEj4imxIf-y · 02 1w5xH2YWfGvsXWFDFMNsw6xyji1EU-Wyh · 03 13B7g3Ggx9iQgfHkNUxvAm2GQznBdv1In ·
  04 1Pey8wrErbhwuiyrnO2XR8WGRjvTMoF_2 · 05 1H1EqjgFYcBzws5FTsLoHCsjBGUIgPzx7 · Diğer 1GGk0lDJ3_zDYWGSBE9nl247k78YATQUM.
  Yeni hafta: klasör aç ("Hafta 06 · 26 Ekim-1 Kasım"), dosyaları taşı (update_file parentId), WEEKS'e ekle.
- Kullanıcı her hafta Harmanlanmış Eğitim ekran görüntüsü atar (gün gün + self-study): WEEKS'e ekle, `python3 tools/make_catalog.py`, derle.
  Drive'da olmayanlar "!başlık|tür" olarak girer (uygulamada "Drive'da yok").
- Katalog Drive'a da konabilir (SouNote-katalog.json, herkese açık) ve Catalog.kt REMOTE_URL ile uzaktan güncellenir; klasör herkese açık olmalı.
- Sadece L4: katalogda başka kur yok.

Her adımı küçük tut, çalıştığı doğrulanmadan sonrakine geçme.
