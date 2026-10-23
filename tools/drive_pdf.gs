/**
 * SouNote: Drive'daki Word/PowerPoint ders dosyalarının PDF kopyalarını aynı klasöre koyar.
 * PDF'ler klasörün paylaşım ayarını alır (originaller gibi); GitHub'a hiçbir şey gitmez.
 *
 * Kurulum (bir kez):
 *  1. script.google.com → "Yeni proje" → bu kodun tamamını yapıştır.
 *  2. Soldaki "Hizmetler +" → "Drive API" → Ekle.
 *  3. Üstte fonksiyon olarak "convertAll" seç → Çalıştır → Google hesabınla izin ver.
 *  4. Yeni haftaların dosyaları da kendiliğinden çevrilsin diye "installDailyTrigger"ı bir kez çalıştır.
 *
 * Bir çalıştırma en fazla ~5 dakika sürer; "Süre doldu" yazarsa convertAll'ı tekrar çalıştır, kaldığı yerden devam eder.
 */
const ROOT_ID = '1f2chdgREDpdIpHoKh4I43oeBRC0tlo8q'; // "Ingilizce calismalar"
const SUFFIX = ' (PDF).pdf';
const LIMIT_MS = 5 * 60 * 1000;
const TYPES = {
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'application/vnd.google-apps.presentation',
  'application/vnd.ms-powerpoint': 'application/vnd.google-apps.presentation',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'application/vnd.google-apps.document',
  'application/msword': 'application/vnd.google-apps.document',
};

function convertAll() {
  const stats = { started: Date.now(), done: 0, skipped: 0, failed: [], timeUp: false };
  walk(DriveApp.getFolderById(ROOT_ID), stats);
  console.log('Çevrildi: ' + stats.done + ' · Zaten vardı: ' + stats.skipped + ' · Hata: ' + stats.failed.length);
  stats.failed.forEach(function (f) { console.log('Hata: ' + f); });
  if (stats.timeUp) console.log('Süre doldu: convertAll\'ı bir kez daha çalıştır, kaldığı yerden devam eder.');
}

function walk(folder, stats) {
  const files = [];
  const it = folder.getFiles();
  while (it.hasNext()) files.push(it.next());
  const names = {};
  files.forEach(function (f) { names[f.getName()] = true; });

  for (let i = 0; i < files.length; i++) {
    const f = files[i];
    const target = TYPES[f.getMimeType()];
    if (!target) continue;
    const pdfName = f.getName().replace(/\.(pptx?|docx?)$/i, '') + SUFFIX;
    if (names[pdfName]) { stats.skipped++; continue; }
    if (Date.now() - stats.started > LIMIT_MS) { stats.timeUp = true; return; }
    let tmpId = null;
    try {
      // Google biçimine çevrilmiş geçici kopya → PDF olarak dışa aktar → geçiciyi çöpe at
      tmpId = Drive.Files.create({ name: 'SouNote geçici', mimeType: target }, f.getBlob()).id;
      const pdf = DriveApp.getFileById(tmpId).getAs(MimeType.PDF).setName(pdfName);
      folder.createFile(pdf).setDescription('SouNote PDF kopyası: ' + f.getId());
      stats.done++;
    } catch (e) {
      stats.failed.push(f.getName() + ': ' + e);
    } finally {
      if (tmpId) DriveApp.getFileById(tmpId).setTrashed(true);
    }
  }

  const subs = folder.getFolders();
  while (subs.hasNext()) {
    if (Date.now() - stats.started > LIMIT_MS) { stats.timeUp = true; return; }
    walk(subs.next(), stats);
  }
}

/** Her gece 03:00 civarı yeni dosyaları çevirir. */
function installDailyTrigger() {
  ScriptApp.getProjectTriggers()
    .filter(function (t) { return t.getHandlerFunction() === 'convertAll'; })
    .forEach(function (t) { ScriptApp.deleteTrigger(t); });
  ScriptApp.newTrigger('convertAll').timeBased().everyDays(1).atHour(3).create();
  console.log('Günlük çeviri kuruldu.');
}
