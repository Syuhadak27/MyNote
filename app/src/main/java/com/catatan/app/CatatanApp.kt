package com.catatan.app

import android.app.Application
import com.catatan.app.auth.AuthManager
import com.catatan.app.data.MediaStore
import com.catatan.app.data.Repo
import com.catatan.app.sync.SyncManager

/**
 * Kelas Application — memuat penyimpanan tunggal sekali di awal.
 * (Nama ini juga tercantum di AndroidManifest: android:name=".CatatanApp")
 */
class CatatanApp : Application() {

    lateinit var repo: Repo
        private set
    lateinit var authManager: AuthManager
        private set
    lateinit var syncManager: SyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        authManager = AuthManager(this)
        repo = Repo(this)

        // Sesi login yang masih aktif memakai file akun masing-masing.
        // Kalau ada keputusan yang tertunda (dialog gabungkan data lokal),
        // scope sengaja belum diganti — AppState akan menanyakan lagi.
        val uid = authManager.currentUid
        if (uid != null && authManager.pendingUid == null) repo.switchScope(uid)

        MediaStore.init(this, repo.mediaDirName)
        MediaStore.prune(repo.current.notes)
        syncManager = SyncManager(this, repo, authManager)
        if (repo.current.settings.autoPurgeTrash) repo.purgeExpiredTrash()
    }

    companion object {
        lateinit var instance: CatatanApp
            private set
    }
}
