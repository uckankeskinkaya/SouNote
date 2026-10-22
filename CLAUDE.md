# Proje: Kalemle PDF not uygulaması (StarNote benzeri)

Kullanıcıyla Türkçe konuş. Kısa ve net ol.

## Amaç
Ders notlarımı (PDF, Word) içe aktarıp üstünde kalemle çalışacağım kişisel bir Android uygulaması. Ücretli uygulama almak yerine kendim yapıyorum.
Referans: https://play.google.com/store/apps/details?id=com.onyx.galaxy.global.note (StarNote) — incele, temel deneyimi hedefliyoruz, AI özellikleri kapsam dışı.

## Ortam
- Cihaz: Xiaomi Pad 7 Pro + kalem. Bilgisayar yok, her şey tablette.
- Kod tablette yazılır, derleme GitHub Actions'ta yapılır, APK indirilip kurulur.
- Termux'ta yerel Gradle derlemesi şimdilik yok (aapt2 sorunları). AndroidIDE arşivlendi; gerekirse ileride Code on the Go düşünülebilir.

## Teknik kararlar
- Kotlin + native Android (web/Flutter/Expo/FlutterFlow değerlendirildi, kalem akıcılığı yüzünden elendi).
- PDF görüntüleme: Android PdfRenderer.
- Düşük gecikmeli mürekkep: androidx low-latency/ink kütüphanelerini değerlendir.
- Sadece kalem (stylus toolType) çizer; parmak kaydırır ve yakınlaştırır (avuç içi sorunu).
- Notlar cihazda saklanır; işaretli PDF olarak dışa aktarılabilir.
- Word dosyaları uygulamada açılmaz, önce PDF'e çevrilir.

## Yol haritası
1. Boş proje + GitHub Actions workflow'u → APK derlenip tablete kurulsun.
2. PDF içe aktar, sayfaları göster, kaydır/yakınlaştır.
3. Kalemle çizim (basınca duyarlı), silgi, fosforlu kalem.
4. Çizimleri kaydet/yükle, PDF olarak dışa aktar.
5. Sonra: defterler/klasörler, renk seçimi, geri al/ileri al, lasso.

Her adımı küçük tut, çalıştığı doğrulanmadan sonrakine geçme.
