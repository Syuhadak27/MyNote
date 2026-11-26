# Setup Firebase untuk Akun & Sinkronisasi

File `app/google-services.json` sudah ada (project `mynotes-30b82`, package `com.catatan.app`).
Sinkronisasi **hanya memakai Realtime Database** — Cloud Storage tidak dipakai.
Gambar/audio tetap tersimpan lokal di perangkat masing-masing.

Yang perlu dipastikan di **Firebase Console**:

## 1. Authentication
- Aktifkan **Authentication → Sign-in method → Google**.
- Web client ID (`client_type: 3`) sudah dipakai di `BuildConfig.GOOGLE_WEB_CLIENT_ID`.

## 2. Realtime Database
- Buat database Realtime Database, lalu salin URL-nya ke `gradle.properties`:
  ```
  firebaseDatabaseUrl=https://mynotes-30b82-default-rtdb.<region>.firebasedatabase.app
  ```
- Pasang rules berikut (**Realtime Database → Rules → Publish**):

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid"
      }
    }
  }
}
```

Ini yang menjamin akun A tidak bisa membaca/menulis data akun B. Client hanya boleh
mengakses `/users/<uid>` miliknya sendiri dengan ID token Firebase.

## 3. SHA-1 untuk Google Sign-In
- `google-services.json` sudah memuat SHA-1 **release** (cocok dengan `release.keystore`).
- Untuk build **debug**, tambahkan SHA-1 debug di Firebase Console:
  ```
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
  ```
  lalu copy SHA1 ke **Project settings → Your apps → Android app → Add fingerprint**.
  Atau uji langsung dengan build release yang sudah terdaftar.

## 4. Struktur data
```
users/{uid}/notes/{noteId}          -> Note (JSON)
users/{uid}/categories/{id}         -> Category (JSON)
users/{uid}/revisions/{id}          -> Revision (JSON)
users/{uid}/settings                -> AppSettings (JSON)
```

## Troubleshooting singkat
- Pesan `Database: Permission denied` berarti rules RTDB belum di-Publish atau
  `firebaseDatabaseUrl` di `gradle.properties` bukan URL database yang dipakai.
  Mengubah rules tidak perlu build ulang; mengubah URL perlu build ulang
  (nilainya masuk `BuildConfig`).
- Cek di **Realtime Database → Data**: harus ada node `users/<uid>` sesuai UID di
  **Authentication → Users**. Kalau kosong, berarti data belum terdorong.

## Catatan batasan versi awal
- Konflik diselesaikan per-item dengan `updatedAt`/`createdAt` terbaru yang menang.
- Hapus permanen belum membuat tombstone lintas perangkat; hapus sementara
  (`isDeleted`) sudah ikut tersinkron.
- Media (gambar/rekaman) **tidak disinkronkan**, hanya tersimpan di perangkat.
  Backup JSON/Markdown tetap bisa dipakai untuk memindahkan media secara manual.
