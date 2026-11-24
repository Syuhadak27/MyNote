package com.catatan.app

import android.app.Application
import com.catatan.app.data.MediaStore
import com.catatan.app.data.Repo

/**
 * Kelas Application — memuat penyimpanan tunggal sekali di awal.
 * (Nama ini juga tercantum di AndroidManifest: android:name=".CatatanApp")
 */
class CatatanApp : Application() {

    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repo(this)
        repo.load()
        MediaStore.init(this)
        MediaStore.prune(repo.current.notes)
        if (repo.current.settings.autoPurgeTrash) repo.purgeExpiredTrash()
    }

    companion object {
        lateinit var instance: CatatanApp
            private set
    }
}
