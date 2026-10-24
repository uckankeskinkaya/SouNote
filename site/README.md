# SouNote sitesi (kaynak)

GitHub Pages'teki tanıtım/indirme sitesi bu klasörden üretilir: `site/` → `npm run build` → `docs/` (Pages: main dalı, `/docs`).
**`docs/` elle düzenlenmez**; her derlemede baştan yazılır.

## Komutlar

```bash
cd site
npm ci              # bağımlılıklar
npm run dev         # yerel geliştirme (http://localhost:5173)
npm run build       # tip denetimi + üretim derlemesi → ../docs
npm run preview     # derlenmiş siteyi /SouNote/ alt yolunda önizle
npm test            # derlenmiş siteyi /SouNote/ altında Chromium'da gezen duman testi (Playwright gerekir)
```

Test, ekran görüntülerini `site/tests/out/` içine yazar. `?intro=0` açılışı atlar, `?quality=high|medium|low` kalite katmanını zorlar,
`?debug` 3B durumunu `window.__sn` olarak açar.

## Mimari

- **Vite + TypeScript, çerçevesiz.** Bütün metin `index.html` içinde anlamsal HTML olarak durur: arama motorları, WebGL'siz
  tarayıcılar ve ekran okuyucular içeriğin tamamını görür. React/R3F eklenmedi; sahne tek bir tablet olduğundan
  doğrudan Three.js daha küçük ve tek elden yönetilebilir.
- **Tek kaydırma kaynağı** (`src/core/scroll.ts`): tek `requestAnimationFrame` döngüsü. DOM'daki `--p` ilerleme değişkenlerini
  ve 3B pozu aynı karede hesaplar. GSAP/ScrollTrigger kullanılmadı; aynı özelliğe iki animasyon yazmaz.
- **Kalıcı WebGL tuvali** (`src/three/stage.ts`, ayrı parça olarak ilk boyamadan sonra yüklenir): sayfa boyunca tek tuval, tek tablet.
  - `tablet.ts`: prosedürel model (yuvarlatılmış titanyum gövde, ince çerçeve, cam yansıması, arka kamera adası, arkada logo).
  - `screen.ts`: gerçek ekran görüntülerini oranı korunarak tuvale çizer, iki katman arasında geçiş yapar. Yatay görüntü,
    tablet -90° döndüğünde düz görünecek şekilde döndürülerek çizilir.
  - `choreography.ts`: her bölümde görünmez bir **yuva** (`.slot[data-slot]`) var; yerini ve boyunu CSS belirler (masaüstü/telefon ayrı).
    Duraklar "şu bölümün şu noktasında tablet şu yuvada, şu açıda" der; araları kaydırma konumuyla karıştırılır.
    Poz kaydırmanın saf fonksiyonu olduğundan ileri/geri ve hızlı kaydırmada sonuç aynıdır.
  - Keşif modu ("3D İncele"): sürükleme yalnızca bu moddayken ve yalnızca tuvalin üstünde yakalanır; düğmeler ve ok tuşlarıyla da çalışır.
  - Uyarlanır kalite: başlangıç katmanı cihazdan (bellek, çekirdek, dokunmatik, yazılımsal GPU); 3 sn'lik iki pencere 42 fps altında
    kalırsa bir kademe düşer (yükselmez, gidip gelmez). Görünmeyen sahnede çizim yapılmaz.
- **Açılış** (`src/ui/intro.ts`): logo kâğıt ve kalem katmanlarından birleşir, ışık geçer, logo tabletin ekranındaki logoya iner.
  ~2,9 sn, oturumda bir kez; gezinme ve indirme bu sırada da çalışır. Hareket azaltılmışsa oynamaz.
- **Denemeler** yalnızca düğmeye basılınca yüklenir: `src/demos/drawing.ts` (Pointer Events, basınç, DPR, vektör geri al, PNG),
  `src/demos/flashcards.ts` (çevirme, kaydırma, yay benzetimi, puan/seri, mini test).
- **Yayın bilgisi** (`src/ui/release.ts`): GitHub API'den en son sürüm, boyut, tarih ve SHA-256. Okunamazsa sabit
  `releases/latest/download/SouNote.apk` bağlantısı çalışmaya devam eder.

## Varlıklar

- `public/screens/`: uygulamanın gerçek ekran görüntüleri (CI emülatörü, `screenshots` dalı; `week.webp` eski README görüntüsü).
- `public/brand/`: "A. Dengeli" logosu. Kaynak `brand-src/logo-a-kaynak.png`; varyantlar `python3 site/brand-src/make_logo.py` ile üretilir
  (karo, saydam simge, açılış için kâğıt/kalem katmanları, favicon boyutları).
- `public/img/`, `public/video/`: eski sayfadan; README `docs/img/*.png` yollarını kullanır, bu yüzden aynı yolda kalırlar.

## Yayınlama

Site `main` dalının `/docs` klasöründen yayınlanır. Bu dalda değişiklik yapıp `npm run build` çalıştır, `site/` ve `docs/`'u birlikte commit'le,
main'e birleştir. `site/**` ve `docs/**` değişiklikleri APK derlemesini tetiklemez (`.github/workflows/build.yml`).

## Kontrol listesi

- [x] Depo incelemesi, ayrı dal, mevcut içerik korunarak taşındı (SSS, Play Protect, kurulum, video, yayın bilgisi)
- [x] Tasarım sistemi (tokenlar, Space Grotesk + Geist yerel, Türkçe karakterler), uyarlanır gezinme
- [x] Gerçek zamanlı 3B tablet, gerçek ekranlar, dikey/yatay, açılış
- [x] Kaydırma koreografisi (hikâye → karar → sonuç → geliştirici → özellikler → indirme)
- [x] Keşif modu, bilgi noktaları, klavye ve düğme kontrolleri
- [x] Çizim ve bilgi kartı denemeleri (fare + dokunma test edildi)
- [x] Karşılaştırma (ad vermeden, eksikler dahil), indirme finali, SSS
- [x] Kalite katmanları, hareket azaltma, WebGL'siz statik görünüm
- [x] Duman testi (52 kontrol), /SouNote/ alt yolu, yatay taşma (360–1440 px)
- [ ] Gerçek bir Android cihazda kare hızı ve açılış akıcılığı (bu ortamda yalnızca yazılımsal GPU vardı)
- [ ] Tablet çözünürlüğünde ekran görüntüleri (şu an telefon görüntüleri tablete oranı korunarak yerleştiriliyor)
- [ ] Depoya açık kaynak lisansı (LICENSE) eklenmesi
