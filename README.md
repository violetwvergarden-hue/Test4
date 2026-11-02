# Manga Translator ID (Android Screen Translator)

Aplikasi Android ringan untuk menerjemahkan seluruh layar manga (Jepang), manhwa (Korea), dan komik (Inggris) langsung ke **Bahasa Indonesia** hanya dengan **1x ketuk tombol melayang (*floating bubble*)**.

## Fitur Utama
- **3 Bahasa Sumber ke Bahasa Indonesia**:
  - **Jepang (`JA`) -> Indonesia (`ID`)** menggunakan Google ML Kit Japanese Text Recognition v2.
  - **Korea (`KO`) -> Indonesia (`ID`)** menggunakan Google ML Kit Korean Text Recognition v2.
  - **Inggris (`EN`) -> Indonesia (`ID`)** menggunakan Google ML Kit Latin Text Recognition v2.
- **Tombol Melayang Sekali Ketuk (*1-Tap Full Screen Translate*)**:
  - Tombol melayang bisa digeser ke posisi mana pun di layar.
  - Ketuk 1x untuk langsung memindai seluruh layar dan menumpuk balon teks komik dengan terjemahan Bahasa Indonesia.
  - Ketuk layar di mana saja untuk menutup lapisan terjemahan dan lanjut *scroll* baca halaman berikutnya.
- **Bisa Offline**: Setelah kamus diunduh pertama kali di dalam aplikasi, proses OCR dan terjemahan berjalan 100% di HP tanpa batas kuota API.

---

## Panduan Step-by-Step Membuat File `.apk` di GitHub (Bisa Lewat HP maupun Laptop)

### Cara A: Lewat Browser HP Android (Paling Praktis, Tanpa Perlu Ekstrak Folder!)
1. Login ke [https://github.com](https://github.com) lewat browser HP (Chrome), ketuk ikon **`+`** di pojok kanan atas -> **New repository**.
2. Beri nama repository (misal: `manga-translator-apk`), pilih **Public**, centang **Add a README file**, lalu ketuk **Create repository**.
3. Di halaman repository, ketuk **Add file** -> **Upload files**, lalu pilih file **`MangaScreenTranslator-Android.zip`** (tidak perlu diekstrak), lalu ketuk **Commit changes**.
4. Sekarang buat pemicu GitHub Actions: ketuk **Add file** -> **Create new file**.
5. Pada kotak nama file, ketik persis: `.github/workflows/build-apk.yml`
6. Tempel (*copy-paste*) kode workflow singkat yang ada di panduan chat, lalu ketuk **Commit changes**.
7. Buka tab **Actions**, tunggu **2–4 menit** hingga muncul **centang hijau**, klik nama workflow-nya, gulir ke bawah ke bagian **Artifacts**, dan unduh **`MangaScreenTranslator-APK`** (di dalamnya terdapat file `app-debug.apk` siap pasang!).

### Cara B: Lewat Laptop / PC
1. Ekstrak file **`MangaScreenTranslator-Android.zip`** di komputer kamu.
2. Buat repository **Public** baru di [https://github.com/new](https://github.com/new).
3. Klik **Add file** -> **Upload files**, lalu tarik (*drag & drop*) semua isi folder hasil ekstrak (`.github`, `app`, `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`), lalu klik **Commit changes**.
4. Buka tab **Actions**, tunggu 2–4 menit sampai centang hijau, lalu unduh **`MangaScreenTranslator-APK`** di bagian **Artifacts**.
