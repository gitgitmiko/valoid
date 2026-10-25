# Valoid

Aplikasi Android (Kotlin + Jetpack Compose) untuk melihat toko Valorant.

Fitur: login Google, login email/password dengan reset password, toko harian, Night Market, riwayat toko.

## Setup

1. Buat project di Firebase, tambahkan app Android dengan package `com.valoid.mobile`.
2. Aktifkan Authentication (Email/Password dan Google) dan Cloud Firestore.
3. Tambahkan SHA-1 debug/release ke app Firebase, lalu unduh `google-services.json` ke `app/`.
4. Firestore rules:

   ```
   match /users/{uid}/{document=**} {
     allow read, write: if request.auth != null && request.auth.uid == uid;
   }
   ```
5. Build: `./gradlew :app:assembleDebug` (butuh JDK 17 dan Android SDK 35).

## Rilis Play Store

1. Simpan `keystore.properties` (tidak di-commit) di root proyek:
   `storeFile`, `storePassword`, `keyAlias`, `keyPassword` untuk kunci upload.
2. ID AdMob asli ada di build type `release`; build debug memakai ID uji Google.
3. `gradle :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
4. Setelah Play App Signing aktif, tambahkan SHA-1/SHA-256 kunci penandatanganan aplikasi dari Play Console ke app Firebase agar login Google berfungsi.

Kebijakan privasi: https://gitgitdev.my.id/valoid/privacy/ · Hapus akun: https://gitgitdev.my.id/valoid/hapus-akun/

## Catatan

- Login Riot lewat WebView `auth.riotgames.com`; token hanya di memori, tidak dikirim ke Firestore.
- Endpoint toko Riot tidak resmi dan bisa berubah. Periksa kebijakan Riot sebelum rilis publik.
- Gambar dan nama skin berasal dari valorant-api.com; hak cipta aset milik Riot Games.
