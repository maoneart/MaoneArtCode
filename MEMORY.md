# Persistent Memory — MaoneArt Code for Android

> **Repository**: [https://github.com/maoneart/MaoneArtCode](https://github.com/maoneart/MaoneArtCode)  
> **Workspace**: `/sdcard/www/MaoneArtCode`  
> **Target Release APK**: `/sdcard/Download/MaoneArtCode.apk` (v1.0.1)

---

## 📌 Architecture & Technical Decisions
1. **Core VS Code Engine & Open VSX Marketplace**:
   - Ditenagai engine `code-server` (v4.145.2 / Code 1.145.2) yang terpasang secara native di lingkungan Termux aarch64.
   - Menggunakan Open VSX Registry (`open-vsx.org`) untuk unduhan dan instalasi ekstensi (Python, Prettier, ESLint, Cline, Roo Code, dll).
   - Port default dialokasikan pada `127.0.0.1:8090` dengan otentikasi dinonaktifkan (`auth: none`) untuk akses lokal instan.
2. **Native Android Wrapper & Hardware Key Interceptor (`MainActivity.kt`)**:
   - WebView modern terakselerasi hardware (`android:hardwareAccelerated="true"`), dukungan penuh DOM Storage, IndexedDB, WebSocket, dan file chooser upload.
   - Intersepsi tombol keyboard fisik via `dispatchKeyEvent`: memetakan `Ctrl+S`, `Ctrl+P`, `Ctrl+Shift+P`, `Ctrl+W`, `Ctrl+B`, `Ctrl+\`` langsung ke event web tanpa dimakan sistem Android.
   - Floating Translucent Accessory Bar untuk tombol esensial layar sentuh: `ESC`, `TAB`, `CTRL`, `ALT`, `F1 Palet`, `Simpan`, `Terminal`, `Berkas`.
   - Glassmorphic Server Dashboard jika server belum aktif, lengkap dengan tombol 1-tap pemicu Termux dan penyalin perintah `code`.
3. **1-Key Permanent Signing Architecture (`keystore/release.jks`)**:
   - Menggunakan keystore permanen MaoneArt (`maoneart_key_2026`) yang berlaku hingga 2054.
   - CI/CD otomatis via GitHub Actions untuk kompilasi rilis APK ke `/sdcard/Download/MaoneArtCode.apk`.

---

## 📅 Milestones & Changelog
- **Initial Release: Desktop-Grade VS Code on Android (v1.0.1)**:
  - Setup core engine `code-server` di Termux dengan script runner global `code`.
  - Pembuatan aplikasi Android Studio `MaoneArt Code` (`com.maoneart.code`).
  - Implementasi hardware shortcut interception, mobile accessory bar, dan glassmorphic connection dashboard.
  - Setup GitHub Actions CI/CD auto-build release APK.
