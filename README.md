<p align="center">
  <img src="docs/img/icon.png" width="96" alt="SouNote simgesi">
</p>

<h1 align="center">SouNote</h1>

<p align="center">
  <b>Ders notların, kalemin ve sınavların tek yerde.</b><br>
  PDF'lerin üstüne kalemle yaz, haftalık kaynaklarını ve sınav takvimini takip et. Ücretsiz, reklamsız Android uygulaması.
</p>

<p align="center">
  <a href="https://github.com/uckankeskinkaya/SouNote/releases/latest/download/SouNote.apk"><b>⬇ APK'yı indir</b></a>
  ·
  <a href="https://uckankeskinkaya.github.io/SouNote/">İndirme sayfası</a>
  ·
  <a href="https://github.com/uckankeskinkaya/SouNote/releases">Tüm sürümler</a>
</p>

<p align="center">
  <img src="docs/img/home.png" width="200" alt="Ana sayfa">
  <img src="docs/img/courses.png" width="200" alt="Dersler">
  <img src="docs/img/exams.png" width="200" alt="Sınavlar">
  <img src="docs/img/grades.png" width="200" alt="Not ortalaması">
</p>

## Özellikler

| | |
|---|---|
| ✍️ **Kalem** | Basınca duyarlı kalem türleri, fosforlu, silgi, şekil düzeltme, lasso, metin kutusu, resim, avuç içi engelleme |
| 📄 **PDF** | İçe aktar ya da klasör bağla, yakınlaşınca net çizim, form doldurma, PDF içinde arama, notlu PDF olarak dışa aktar |
| 📚 **Dersler** | Haftalık kaynaklar konu ve hafta hafta; bitirdiklerini işaretle, haftanın ilerlemesini gör |
| 📅 **Sınavlar** | Takvim, geri sayım, hatırlatıcı bildirimler, ana ekran widget'ı |
| 🃏 **Bilgi kartları** | Sayfadan seçip karta çevir ya da asistana ürettir; aralıklı tekrar (Leitner) |
| 🤖 **Asistan** | Açık belgeyi okur; özet, kelime listesi, soru, essay değerlendirmesi (kendi API anahtarınla) |
| 🔎 **Genel arama** | PDF'lerin içinde sayfa sayfa, yazılı notlarda ve kartlarda |
| ✍️ **Writing pratiği** | Opinion essay/paragraph yaz; asistan L4 ölçütleriyle 100 üzerinden puanlar, hataları ve geliştirilmiş hali gösterir |
| 🔁 **Bugünkü tekrar** | Bütün belgelerde zamanı gelen bilgi kartları tek oturumda |
| 📊 **Not ortalaması** | Puanlarını gir, geçmek için kalan sınavlardan kaç alman gerektiğini gör |
| 📝 **Yazılı notlar** | Klavyeyle ya da kalemle yazma, yapılacaklar, çizim alanı |
| 📦 **Paylaşma** | Notlu belgeyi `.sounote` paketi olarak gönder; arkadaşında notlarıyla açılır |
| ☁️ **Yedek** | Otomatik günlük yedek, Drive'a yedek, geri yükleme |

## Kurulum

1. [SouNote.apk](https://github.com/uckankeskinkaya/SouNote/releases/latest/download/SouNote.apk)'yı indir.
2. Dosyaya dokun; "bilinmeyen uygulamalar" uyarısında bu kaynağa izin ver.
3. **Yükle**'ye bas. Sonraki sürümleri uygulama kendisi haber verir; üstüne kurunca notlar silinmez.

Android 10 ve üstü, tablet ve telefon.

## Geliştirme

- Kotlin + Jetpack Compose, `compileSdk 36`, `minSdk 29`. PDF: Android `PdfRenderer` + [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android).
- Her push'ta GitHub Actions APK'yı derler, emülatörde açıp ekranları gezerek **çökme testi** yapar; test geçerse [Releases](https://github.com/uckankeskinkaya/SouNote/releases)'a yükler.
- Ders kataloğu `app/src/main/assets/catalog.json` (`tools/make_catalog.py` ile üretilir); her sürümle birlikte yayınlanır, uygulama açılışta en yenisini çeker.
- İmza anahtarı `keystore/sounote.jks` sabittir; değişirse güncellemeler eski sürümün üstüne kurulmaz.
- Ayrıntılı teknik notlar: [`CLAUDE.md`](CLAUDE.md).
