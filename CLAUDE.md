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
İmza anahtarı keystore/sounote.jks — değiştirme/silme, yoksa güncelleme kurulmaz ve notlar gider.
İmza anahtar değişimi (kullanıcı onayladı, 23 Ekim): eski anahtar herkese açık repoda olduğundan yeni anahtar keystore/sounote2.p12.enc
(şifreli; şifre sadece GitHub gizli değeri SIGNING_PASSWORD) + geçiş kaydı keystore/lineage.bin (keygen.yml, tek seferlik).
build.yml APK'yı apksigner ile eski+yeni anahtar ve lineage (--rotation-min-sdk-version 28) ile yeniden imzalar; smoke eski anahtarlı
APK'nın yeni sürümün üstüne kurulamadığını dener. Bu iki dosya ve gizli değer de asla değiştirilmez/yeniden üretilmez.
(24 Ekim) Başka oturumda üretilen ikinci bir anahtar (SOUNOTE_KEYSTORE_B64 vb. secret'lar) iptal edildi; kullanılmaz. Geçerli tek düzen yukarıdaki.

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
KURAL (kullanıcı istedi): her yeni özellikte docs/index.html "Neler yapabilirsin?" kartlarına ve README özellik tablosuna ekle.
YAYIN KANALLARI (build-68+): her push = TEST sürümü (ön sürüm "Test 1.0.N", sadece yönetici alır). Herkese gitmesi için "Sürümü yayınla"
(publish.yml, workflow_dispatch: version, title, notes "a | b | c", build boş = en yeni test) çalıştırılır; kullanıcı "yayınla" deyince
mcp actions_run_trigger ile ben çalıştırırım. Çökme testi geçmeyen (notunda ⚠️) sürüm yayınlanmaz. Sitenin "Yenilikler"i sadece yayınlanan
ana sürümleri gösterir (en yenisi "Son güncelleme"). Yönetici modu: Ayarlar → sürüm satırına 7 dokunuş (prefs testChannel; Updates.latestIncludingTests).
Test kanalı: "test" etiketli release'e her derlemede SouNote.apk + test.json {build,name,notes,sha} yüklenir (GitHub release LİSTESİ
ön sürümleri göstermiyor; doğrudan etiket/indirme bağlantıları çalışıyor). Updates.latestIncludingTests, publish.yml ve video.yml buradan okur.
Katalog ayrı kanal: "catalog" etiketli release'in catalog.json'u her derlemede güncellenir (Catalog.REMOTE_URL); yayınlanan sürüme de kopyalanır.
Eklendi (🧪, build-62+): Bugünkü tekrar (vm.dueCards/reviewAny, ana sayfa kartı), Writing pratiği (Essays.kt, EssayStore filesDir/essays yedeğe girer,
AssistantClient.gradeEssay JSON 5 ölçüt × 20, WritingScreen: liste → konu → süreli editör → değerlendirme).

Eklendi (🧪, build-84+): asistan kurulum rehberi (ui/AssistantSetup.kt: Evren'i aç → evren.ssyz.org.tr/api-keys, e-Devlet, anahtar yapıştır,
"Kaydet ve dene"; Evren 1 Kasım 2026'ya kadar ücretsiz, günlük kota; 429'da 5 sn bekleyip bir kez yeniden dener), çalışma serisi
(prefs activeDays StringSet + bestStreak; belgede 5 dk, kart tekrarı, Writing, haftalık liste işareti, yazılı not günü sayar; ana sayfa Hero + WeekCard),
sitede Play Protect uyarısı + VirusTotal (APK digest'iyle rapor bağlantısı), arkadaşla paylaş metninde aynı uyarı.

Word/PPT kaynakları (🧪, build-88+): satırda "Drive" + "Aç ↗" (vm.downloadForOtherApp → cache/exports/<ad>.pptx|docx, FileProvider,
ACTION_VIEW → WPS/Office). Kullanıcı oradan PDF olarak SouNote'a paylaşır (SEND pdf). PDF'e çevirme yok: GitHub'da kopya (kullanıcı: okul
materyali, olmaz) ve Drive Apps Script yöntemi kullanıcı isteğiyle bırakıldı.

## SouNote 2.0 (✅ yayınlandı build-96, 24 Ekim; dal ccr-c4eded18-21kvrf → main)
ccr-aefb7f13-i7jc0n dalı (1.2–1.4: anahtar değişimi, test kanalı + publish.yml, Word/PPT kaynakları, Writing puanlama, çalışma serisi,
bugünkü tekrar) bu dala birleştirildi. Yayın akışı onlarınki: her push "Test 2.0.<run>" ön sürümü; herkese açmak için
.github/publish/request.json (version/title/notes/build) itilir. Writing puanlama ekranı ui/EssayGraderScreen.kt (data/Essays.kt Essay),
yazı stüdyosunun metin yardımcıları data/Writing.kt EssayText. Çalışma serisi VM.streak/activeDays (onlarınki).
- Tasarım: ui/Theme.kt (SouNote Light; koyu paletler DarkPalette OBSIDIAN/MIDNIGHT/GRAPHITE prefs darkPalette; vurgu Accent: prefs colorTheme
  null/AUTO = tema rengi, enum adı = hazır, CUSTOM + accentCustom), sistem yazı tipi (Manrope 2.0.1'de kaldırıldı); SouNoteTheme LocalContentColor = onBackground (Surface dışı yazılar koyu temada siyah kalıyordu), LocalSpacing (UiDensity prefs uiDensity),
  LocalMotionLevel (prefs motionLevel; sistem animasyon ölçeği 0 ise OFF), LocalSouColors. Ortak bileşenler ui/Components.kt.
- Gezinme (AppShell): <600dp alt çubuk (Ayarlar ana sayfadaki ⚙), 600–1000dp ray, ≥1000dp geniş menü; prefs navStyle. Sekmeler: HOME, LIBRARY, COURSES, EXAMS (Planlayıcı), WRITING, SETTINGS.
- Ana sayfa modülleri: data/QuickAccess.kt HomeModule/HomeLayout (prefs homeOrder, homeHidden). Hızlı erişim QuickItem (prefs quickAccess JSON; doc/folder/course/note, pinned).
- Planlayıcı: Exam.priority/done (exams.json'a eklendi, eski dosya okunur); katalogdaki ödevlerin tamamı prefs examDone; tamamlananlara hatırlatma kurulmaz. prefs plannerView.
- Yazı stüdyosu: WritingScreen + TextNoteScreen (Markdown işaretleri düz metinde: # başlık, **kalın**, _italik_; MarkdownVisual; PDF'te EssayText.plain ile temizlenir),
  data/Writing.kt EssayText + WritingCoach (mevcut AssistantClient; öneri JSON; metin sadece "Uygula" ile değişir), WritingCoachPanel.
- PDF: okuma modu (prefs readingMode, PdfDocumentView.readOnly → panOnly), lasso bölgesi onLassoRegion → PdfText.region (PDFTextStripperByArea) → AssistantSource.Document.selection.
- Arama: SearchHit CourseHit/TopicHit/FolderHit/ExamHit + filtre çipleri, son aramalar (prefs recentSearches). Ayarlar: kategoriler + arama (SettingsScreen Entry).
- 2.0.1 (✅ yayınlandı build-99): yeni logo (drawable-nodpi/logo_tile.png, ic_logo_fg.png; AppLogo, simge, açılış), sade ana sayfa (HomeLayout DEFAULT_HIDDEN TIP+NOTES, 6 hızlı işlem).
- Tanıtım 4 sayfa; "Yenilikler" MAJOR = 2 (1.0'dan gelen bir kez görür, onboarding'e zorlanmaz).

## SouNote 3.0 (✅ yayınlandı build-124, 24 Ekim; PR #5 ile main)
3.0.1 yayınlandı build-127; 3.1 yayınlandı build-138 (26 Ekim, PR #6 ile main). 3.2 yayınlandı build-145 (27 Ekim): RenderNode kaydı sistemce silinirse
(hasDisplayList false) yeniden kaydedilir + pencere görünür olunca inkNodes atılır (notlar kalem değene kadar görünmüyordu).
Tanıtım videosu sesli: .github/video/voice.mp3 + voice-asistan.mp3 (INSERTS), voice.txt paragrafları, align.py Whisper (ses ffmpeg ile çözülür). Otomatik yedek zip'i herkese açık ders klasöründeydi:
Drive'da gizli "SouNote yedekleri" (1YKfOGh3d6faxq5gPuW2wRTm897sBuhSG) klasörüne taşındı; uygulamadaki otomatik yedek klasörü de oraya çevrilmeli.
- Tasarım: sade, emoji yok. Simgeler ui/Icons.kt SouIcons (Material Symbols Rounded; tools/make_icons.py ile üretilir, yeni simge = NAMES'e ekle).
  Renkler logoya göre: açık tema mavi-mor (#3A4BE0), koyu varsayılan MIDNIGHT "Lacivert" (logodaki lacivert). Zemin Modifier.souBackground()
  (lacivert + mavi/mor ışık parıltısı); ekran Scaffold'ları saydam. Kart: Modifier.card() kenarlıksız (koyuda ince kenar).
- Görseller Canva'da üretilir (res/drawable-nodpi pen_*, course_*, cover_*). Bu ortamdan canva.com kapalı: görseli Canva tasarımına
  (DAHY7owgeFk) koy → export-design → URL'yi .github/canva/request.txt'e yaz, push → canva-fetch.yml canva-assets dalına koyar; sonra dosyayı sil.
  course_grammar AI çizimi bozuktu: tools/art/course_grammar.svg'den yeniden çizildi.
- Kalemler: PenType 14 tür (defaultWidthMm), InkTool'a NEON ve DOTTED eklendi (ordinal saklanır, yenileri sadece sona). Kalem hafızası
  ToolState.penMemory (prefs penMemory, switchPen). Yazma desteği ToolState.smoothing (EMA, PdfDocumentView.addPoint; kalkınca rawLast'a tamamlanır).
- Cetvel/açıölçer: pdf/GuideOverlay.kt, ToolState.guide (kaydedilmez); parmakla taşı/iki parmakla döndür, kenara oturma guideSnap.
- Bilgi kartı çalışma: StudyDialog tam ekran, dönen kart, kaydırma. Widget: widget_exam.xml lacivert parıltı.
- Asistan: AssistantSource.Course (ders PDF'lerinden özet), Dersler'de "Asistanla ders özeti".
- Defter kapakları: Covers (prefs cover_<id>), CoverArt/CoverPicker; şablonlar WEEKLY, VOCAB, CORNELL_COLOR (PDFBox Helvetica: ı, ş, ğ, İ yazılmaz).
- Çıkartmalar (Art.kt STICKERS, sticker_*.png; vm.importSticker → resim olarak eklenir), paletin üstünde son kullanılan renkler (customColors).
- Sınav öncesi plan (ui/StudyTools.kt ExamPlanDialog, AssistantClient.studyPlan → kendi sınavlara task, slot Exam.PLAN_SLOT, hatırlatma yok),
  deneme sınavı (PracticeSetupDialog/PracticeTestScreen, AssistantClient.practiceTest → Question; Dersler'de kart).
- Konu haritası (ui/TopicMap.kt; vm.mastery = kaynak ilerlemesi + deneme puanı, prefs testScores JSON konu → %), ortak katalog
  ön plana gelince 3 saatte bir yenilenir ve yeni kaynak sayısı bildirilir.
- Speaking pratiği (ui/SpeakingScreen.kt; SpeechRecognizer en-US, duraklamada yeniden başlatılır; manifest <queries> RecognitionService;
  AssistantClient.gradeSpeaking 4 ölçüt × 25, EssayFeedback; SpeakingStore filesDir/speaking yedeğe girer).
- Katmanlar (ink/Layers.kt LayerSet, ink/<belge>/layers.json; Stroke.layer, InkStore v6; silgi/lasso sadece etkin katmanda,
  gizli katman çizilmez/dışa aktarılmaz; sadece çizgiler, yazı/resim katmansız).
- Sonsuz tuval: PaperSize.CANVAS 4800×4800 pt tek sayfa; PdfDocumentView canvasMode (genişlik ≥ CANVAS_MIN_PT): A4 ölçeğinde yerleşir,
  minZoom tamamını gösterir, kaldığın yer prefs canvasView_<id>.
- Ana sayfa: tablette (≥900dp) iki sütun. versionName 3.0.<run>, MAJOR = 3.
- 3.0.1 (🧪, build-127+): kalemler yeniden yazıldı. Kullanıcı (ekran görüntüsü): uçlarda kocaman damla, ortada ince, boncuklu çizgi.
  Neden: basınç katsayısı 0.35–1.6 (çok geniş) + kalınlık 12 kademeye bölünüp her kademe ayrı yuvarlak uçlu yol. Şimdi ink/StrokeOutline.kt:
  basınçlı dolma/jel, fırça, kaligrafi tek parça doldurulan dış hat (yakın noktalar atılır, basınç ileri-geri yumuşatılır, penFactor 0.62–1.38,
  uçlarda smoothstep incelme, keskin dönüşte daire); PdfExporter aynı hattı doldurur; ön tampon taper=false. Tahmin noktaları da yumuşatılır.
  Python + Chromium (Skia, nonzero) ile görsel denendi.
- Alıştırma (🧪, build-129+): data/Exercises.kt (ExerciseType MCQ/GAP/REWRITE/OPEN; generate JSON, checkLocally: şık + birebir boşluk,
  grade: kalanları asistan 0/0.5/1 puanlar), ui/ExerciseScreen.kt (kurulum: konu, türler, sayı, mod "Önce cevapla"/"Cevabı göster";
  sonuç konu haritasına recordTestScore; Notlara kaydet). Girişler: Dersler kartı, konu haritası, asistan penceresi (belge bağlamı/not/ders).
- Renkler logoya yaklaştırıldı (🧪, build-130+): açık primary #1E4BF0 (elektrik mavisi), zemin #F2F5FD, mürekkep #0A1233; koyu MIDNIGHT
  zemin #020820, primary #8DA6FF, primaryContainer #0B3BD6; mor tonlar kaldırıldı. Açılış/pencere/widget zemini #020820.
- Logo teması (🧪, build-131+, kullanıcı: "tamamen logoya uygun, mavi tonlarda parlamalı"): MIDNIGHT = "Logo" (zemin #010518,
  primary #5482FF, kart cam gibi: yarı saydam lacivert-mavi degrade + sol üstten ışıyan degrade kenar, Modifier.card); souBackground dikey
  lacivert degrade + 3 elektrik mavisi ışık; hero #2A5BFF→#0A1A6E; açık tema lavanta-mavi kâğıt + mavi ışıltı. Varsayılan tema KOYU (prefs theme yoksa).
- 6 tema (🧪, build-132+; kullanıcının verdiği logo varyantları): ui/Theme.kt AppTheme ELECTRIC/SAPPHIRE/SILVER/MONO/EMERALD/ROSE
  (tohum renkler: bg, glowA, glowB, darkPrimary…, paper, lightPrimary, ink, hero; darkSchemeOf/lightSchemeOf karıştırarak üretir),
  logolar res/drawable-nodpi/logo_<tema>.webp (Elektrik = logo_tile), AppLogo temanın logosunu çizer; SouColors.glowA/glowB/logo;
  card() ve souBackground() temanın ışıklarını kullanır. prefs "logoTheme" (eski "darkPalette": MIDNIGHT/OBSIDIAN → ELECTRIC, GRAPHITE → SILVER).
  Simge ve widget'lar da temayı izler (🧪, build-134+): asıl ekran SouNoteActivity (eski MainActivity); manifestte 6 activity-alias
  (".MainActivity" = Elektrik, eski sürümlerin ana ekran simgesi kaybolmasın diye adı korundu; ".Launcher<Tema>" kapalı başlar),
  AppIcon.apply önce yenisini açar sonra ötekileri kapatır (DONT_KILL_APP). Simgeler mipmap-anydpi-v26/ic_launcher_<tema>.xml +
  drawable-nodpi/ic_launcher_<tema>_bg.webp (logo ortada %64, zemin logonun kenar rengi). Widget: Widgets.style (widget_bg_<tema>.xml,
  widget_logo, vurgu rengi). smoke.sh ".MainActivity" ile açar: tema değiştirmez, takma ad açık kalır.
- Son kullanılanlar temizleme (🧪, build-139+): VM recentsClearedAt + recentHidden (prefs JSON anahtar "d"/"n"/"c"+kimlik → an);
  recentVisible(key, time); ana sayfa "Temizle" (onaylı) + kartta "Listeden kaldır", kütüphane "Son açılanlar"da da; dinamik kısayollar silinir.
- Listening pratiği (🧪, build-147+): ui/ListeningScreen.kt (MediaPlayer; hız 0.75–1.5, ±5 sn, tekrarla, kaldığın yer) + data/Listening.kt
  ListeningStore (filesDir/listening/<driveId>.audio, prefs "listening" pos_/note_); Dersler'de ses satırında "Dinle" → vm.openListening;
  ilgili kaynaklar = aynı hafta+gün ya da konu; asistanla alıştırma = ExerciseScreen(konu). CatalogRepo.download ses için HTML değil mi diye bakar.
- Yan yana belge (🧪, build-147+): ui/SideDocPane.kt, belge ⋮ menüsü "Yan yana belge aç" (≥840dp); ikinci PdfContainer readOnly,
  notlar vm.inkForSide (kaydedilmez), son sayfa hatırlanır.
- Widget'lar (Widgets.kt): TodayWidget (seri, bugünkü süre, prefs dueCount), WeekWidget (weekTasks ile aynı sıra, prefs weekDone),
  QuickWidget (hızlı not, son belge prefs lastDocId/lastDocTitle, tuval, asistan); Widgets.updateAll (ExamWidget dahil) VM'de çağrılır.
  MainActivity ACTION_OPEN_HOME/WEEK/LIBRARY, ACTION_NEW_CANVAS, ACTION_OPEN_ASSISTANT. Simgeler res/drawable/ic_w_*.

Site yenilendi (🧪): kaynak site/ (Vite + TS + Three.js, çerçevesiz), `cd site && npm run build` → docs/ (docs/ elle düzenlenmez).
3B tablet (prosedürel, gerçek ekran görüntüleri), kaydırma koreografisi (.slot[data-slot] yuvaları), 3D İncele, açılış (logo → tablet),
çizim ve bilgi kartı denemeleri, kalite katmanları, WebGL'siz statik görünüm. Logo: "A. Dengeli" (site/brand-src, make_logo.py).
Test: `cd site && npm test` (Playwright, /SouNote/ alt yolu). Ayrıntı: site/README.md. build.yml site/** değişikliğinde APK derlemez.

## Devam eden iş (sonraki oturum)
- Drive düzeni (yapıldı, 23 Ekim): "Ingilizce calismalar" (1f2chdgREDpdIpHoKh4I43oeBRC0tlo8q) altında hafta klasörleri:
  Hafta 01 1_LtxFdJkk_niIhFy6Yb51kEj4imxIf-y · 02 1w5xH2YWfGvsXWFDFMNsw6xyji1EU-Wyh · 03 13B7g3Ggx9iQgfHkNUxvAm2GQznBdv1In ·
  04 1Pey8wrErbhwuiyrnO2XR8WGRjvTMoF_2 · 05 1H1EqjgFYcBzws5FTsLoHCsjBGUIgPzx7 · 06 1ojYUJlwP3PfdDgtm6yDwsao2bwvhi8E- ·
  Diğer 1GGk0lDJ3_zDYWGSBE9nl247k78YATQUM. Hafta 6 (26 Ekim): 23 dosya program PDF'ine göre günlere ve konulara dağıtıldı.
  Yeni hafta: klasör aç ("Hafta 06 · 26 Ekim-1 Kasım"), dosyaları taşı (update_file parentId), WEEKS'e ekle.
- OTOMASYON (🧪, build-137+): uygulama katalog yenilerken (açılış + ön plana gelince 3 saatte bir) Drive kök klasörünü
  (DriveAuto.DEFAULT_ROOT) embeddedfolderview ile anahtarsız tarar ("Hafta NN" klasörleri → hafta, diğerleri 0); katalogda olmayan Drive
  kimlikleri "Yeni gelenler" dersinde (Course id "auto", konu "Hafta N", gün 6 = "Bu hafta") görünür; aynı adlı "(1)" kopyaları ve
  katalogdaki adın PPTX'i atlanır, PDF'i "(PDF)" olarak gelir. filesDir/drive-auto.json son liste. Sınıflandırma: klasörü listele
  (search_files parentId), haftanın "L4 FALL WEEK N PROGRAM.pdf"ini read_file_content ile oku, raw + konular + WEEKS[N]'e ekle,
  `python3 tools/make_catalog.py`, it. Sınıflanan kimlik katalogda olunca "Yeni gelenler"den düşer. Kullanıcı artık her hafta yazmak zorunda değil.
- Kullanıcı her hafta Harmanlanmış Eğitim ekran görüntüsü atar (gün gün + self-study): WEEKS'e ekle, `python3 tools/make_catalog.py`, derle.
  Drive'da olmayanlar "!başlık|tür" olarak girer (uygulamada "Drive'da yok").
- Katalog Drive'a da konabilir (SouNote-katalog.json, herkese açık) ve Catalog.kt REMOTE_URL ile uzaktan güncellenir; klasör herkese açık olmalı.
- Sadece L4: katalogda başka kur yok.

Her adımı küçük tut, çalıştığı doğrulanmadan sonrakine geçme.
