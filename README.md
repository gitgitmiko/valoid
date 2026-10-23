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

## Catatan

- Login Riot lewat WebView `auth.riotgames.com`; token hanya di memori, tidak dikirim ke Firestore.
- Endpoint toko Riot tidak resmi dan bisa berubah. Periksa kebijakan Riot sebelum rilis publik.
- Gambar dan nama skin berasal dari valorant-api.com; hak cipta aset milik Riot Games.
