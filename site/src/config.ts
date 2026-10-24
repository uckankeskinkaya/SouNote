// Sitenin tek gerçek kaynağı: bağlantılar, ekran görüntüleri ve 3B bilgi noktaları.

export const LINKS = {
  apk: "https://github.com/uckankeskinkaya/SouNote/releases/latest/download/SouNote.apk",
  releases: "https://github.com/uckankeskinkaya/SouNote/releases",
  repo: "https://github.com/uckankeskinkaya/SouNote",
  profile: "https://github.com/uckankeskinkaya",
  latestApi: "https://api.github.com/repos/uckankeskinkaya/SouNote/releases/latest",
} as const;

export type Orientation = "portrait" | "landscape";

export interface Hotspot {
  /** Ekran görüntüsü üzerindeki konum (0..1, sol üstten). */
  u: number;
  v: number;
  title: string;
  text: string;
}

export interface ScreenDef {
  id: string;
  label: string;
  orientation: Orientation;
  /** public/ altındaki göreli yol; "splash" için yok (logo programla çizilir). */
  src?: string;
  hotspots?: Hotspot[];
}

// Hepsi uygulamanın gerçek ekran görüntüleri (CI emülatöründen ve README'den).
export const SCREENS: ScreenDef[] = [
  { id: "splash", label: "SouNote", orientation: "portrait" },
  {
    id: "home", label: "Ana sayfa", orientation: "portrait", src: "screens/home.webp",
    hotspots: [
      { u: 0.5, v: 0.03, title: "Genel arama", text: "Belge adlarında, PDF'lerin içinde sayfa sayfa, yazılı notlarda ve bilgi kartlarında arar." },
      { u: 0.5, v: 0.2, title: "Günün özeti", text: "Karşılama, bugünkü çalışma süren ve çalışma serin." },
      { u: 0.5, v: 0.6, title: "Hızlı başla", text: "Yeni defter, hızlı not, Writing pratiği, asistan ve not ortalaması tek dokunuşta." },
    ],
  },
  {
    id: "notebook", label: "Defter", orientation: "portrait", src: "screens/notebook.webp",
    hotspots: [
      { u: 0.3, v: 0.105, title: "Kalem araçları", text: "Kalem, fosforlu, silgi, metin kutusu; lasso ve şekil aracı da burada." },
      { u: 0.62, v: 0.155, title: "Renkler", text: "Hazır renkler ve tam renk seçici. Kalem türü ve kalınlığı soldaki menüden." },
      { u: 0.5, v: 0.55, title: "Sayfa", text: "Düz, çizgili, kareli, noktalı ya da Cornell şablonu. PDF'lerde notlar sayfanın üstüne kaydedilir." },
    ],
  },
  {
    id: "courses-land", label: "Dersler (yatay)", orientation: "landscape", src: "screens/courses-land.webp",
    hotspots: [
      { u: 0.04, v: 0.35, title: "Yan menü", text: "Tablet yatay tutulunca alt menü yerine yan menü çıkar." },
      { u: 0.33, v: 0.3, title: "Konular ve haftalar", text: "Kaynakları konuya ya da haftaya göre gör." },
      { u: 0.55, v: 0.75, title: "Ders kartları", text: "Her konuda kaç kaynak olduğu; dokununca liste açılır." },
    ],
  },
  {
    id: "courses", label: "Dersler", orientation: "portrait", src: "screens/courses.webp",
    hotspots: [{ u: 0.5, v: 0.5, title: "Konu konu kaynaklar", text: "Grammar, Vocabulary, Reading, Listening, Writing ve Speaking." }],
  },
  {
    id: "exams", label: "Sınavlar", orientation: "portrait", src: "screens/exams.webp",
    hotspots: [
      { u: 0.5, v: 0.24, title: "Hatırlatıcılar", text: "3 gün ve 1 gün önce akşam 19:00'da, sınavdan 2 saat önce bildirim." },
      { u: 0.2, v: 0.4, title: "Geri sayım", text: "Her sınavın tarihi ve kaç gün kaldığı." },
      { u: 0.78, v: 0.84, title: "Sınav ekle", text: "Kendi sınavlarını ve ödevlerini de ekleyebilirsin." },
    ],
  },
  { id: "writing", label: "Writing pratiği", orientation: "portrait", src: "screens/writing.webp",
    hotspots: [{ u: 0.5, v: 0.17, title: "Yeni yazı", text: "Konu seç, süreyle yaz; asistan 100 üzerinden puanlar. Asistan için kendi servis anahtarın gerekir." }] },
  { id: "grades", label: "Not ortalaması", orientation: "portrait", src: "screens/grades.webp",
    hotspots: [{ u: 0.5, v: 0.17, title: "Geçmek için kalan", text: "Kalan sınavlardan ortalama kaç alman gerektiğini gösterir." }] },
  { id: "week", label: "Haftalık liste", orientation: "portrait", src: "screens/week.webp" },
  { id: "assistant", label: "Asistan kurulumu", orientation: "portrait", src: "screens/assistant.webp",
    hotspots: [{ u: 0.5, v: 0.3, title: "Adım adım kurulum", text: "Anahtar uygulamaya gömülü değil; kendi anahtarını girersin, sadece cihazında durur." }] },
  { id: "onboarding", label: "Tanıtım", orientation: "portrait", src: "screens/onboarding.webp" },
];

export const screenById = (id: string): ScreenDef => SCREENS.find((s) => s.id === id) ?? SCREENS[0];
