# Proje: Kalemle PDF not uygulaması (StarNote benzeri)

Kullanıcıyla Türkçe konuş. Kısa ve net ol.

## Amaç
Ders notlarımı (PDF, Word) içe aktarıp üstünde kalemle çalışacağım kişisel bir Android uygulaması. Ücretli uygulama almak yerine kendim yapıyorum.
Referans: https://play.google.com/store/apps/details?id=com.onyx.galaxy.global.note (StarNote) — incele, temel deneyimi hedefliyoruz, AI özellikleri kapsam dışı.

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
1. ✅ Boş proje + GitHub Actions workflow'u → APK derlenip tablete kurulsun (Releases'tan indirilir).
2. 🧪 PDF içe aktar (tek dosya + "klasör bağla": bağlı klasördeki PDF'ler otomatik görünür, alt klasörler klasör olur), kütüphane ızgarası, sayfaları göster, kaydır/yakınlaştır (yakınlaşınca net).
3. Kalem çekirdeği: basınca duyarlı kalem, hassas kalınlık (0.1 mm), silgi (çizgi + hassas), fosforlu, hazır renkler. "Parmakla çiz" anahtarı (açıkken tek parmak çizer, iki parmak kaydırır/yakınlaştırır; kalemsiz arkadaşlar için). Bağlı klasördeki PDF'e ilk not alınınca kopyası uygulamaya alınır.
4. Otomatik kayıt, geri al/ileri al, metin kutusu (klavyeyle yazı; taşı, boyut, renk), PDF form alanlarını doldurma (Android 15+ PdfRenderer form API'si), işaretli PDF olarak dışa aktar.
5. Gelişmiş kalem: kalem türleri, favori kalemler, basınç eğrisi, şekil/düz çizgi düzeltme, tam renk seçici.
6. Düzenleme: lasso (taşı/boyutlandır/renk/kopyala/sil), resim ekleme.
7. Sayfalar: sayfa paneli, boş defter + şablonlar, araya boş sayfa, yer imleri, PDF içinde arama, yatayda çift sayfa.
8. Kütüphane: klasörler, arama/sıralama, yedekleme, karanlık mod.

Arayüz dili Türkçe. Paket: com.sounote.app. compile/targetSdk 36, minSdk 29.
İmza anahtarı keystore/sounote.jks — değiştirme, yoksa güncelleme kurulmaz ve notlar gider.

Her adımı küçük tut, çalıştığı doğrulanmadan sonrakine geçme.
