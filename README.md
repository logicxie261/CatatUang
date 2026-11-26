# CatatUang

Aplikasi pencatat keuangan (WebView). Paket: `com.logicxie.keuangan`, versi 1.0.0.

## Cara build APK di GitHub
1. Upload seluruh isi folder ini ke repository GitHub (branch `main`).
2. Buka tab **Actions** -> workflow **Build APK** berjalan otomatis (atau klik *Run workflow*).
3. Setelah selesai, unduh **CatatUang-APK** di bagian *Artifacts*.
4. Untuk membuat Release: `git tag v1.0.0 && git push origin v1.0.0` - APK terlampir otomatis.

## Struktur
- `app/src/main/assets/index.html` - isi aplikasi (edit di sini)
- `app/src/main/java/.../MainActivity.java` - WebView + simpan file ke Download
- `app/build.gradle` - versionCode / versionName
