# Setup Firebase untuk Akun & Sinkronisasi

File `app/google-services.json` sudah ada (project `mynotes-30b82`, package `com.catatan.app`).
Yang masih perlu dipastikan di **Firebase Console**:

## 1. Authentication
- Aktifkan **Authentication → Sign-in method → Google**.
- Simpan Web client ID (`client_type: 3`) — sudah dipakai di `BuildConfig.GOOGLE_WEB_CLIENT_ID`.

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

## 3. Storage (media catatan)
- Aktifkan **Storage** (bucket default sudah tercantum di `google-services.json`).
- Pasang rules berikut (**Storage → Rules → Publish**):

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{uid}/media/{file} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

## 4. SHA-1 untuk Google Sign-In
- `google-services.json` sudah memuat SHA-1 **release** (cocok dengan `release.keystore`).
- Untuk menjalankan build **debug**, tambahkan SHA-1 debug di Firebase Console:
  ```
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
  ```
  lalu copy SHA1 ke **Project settings → Your apps → Android app → Add fingerprint**.
  Atau langsung uji dengan build release yang sudah terdaftar.

## 5. Struktur data
```
users/{uid}/notes/{noteId}          -> Note (JSON)
users/{uid}/categories/{id}         -> Category (JSON)
users/{uid}/revisions/{id}          -> Revision (JSON)
users/{uid}/settings                -> AppSettings (JSON)
users/{uid}/media/{nama file}       -> gambar/audio
```

## Catatan batasan versi awal
- Konflik diselesaikan per-item dengan `updatedAt`/`createdAt` terbaru yang menang.
- Hapus permanen belum membuat tombstone lintas perangkat; hapus sementara
  (`isDeleted`) sudah ikut tersinkron.
- Media dibatasi ~20 MB per file saat download.
