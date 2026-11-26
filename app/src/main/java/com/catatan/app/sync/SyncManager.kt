package com.catatan.app.sync

import android.content.Context
import com.catatan.app.BuildConfig
import com.catatan.app.auth.AuthManager
import com.catatan.app.data.AppSettings
import com.catatan.app.data.Category
import com.catatan.app.data.MediaStore
import com.catatan.app.data.Note
import com.catatan.app.data.Repo
import com.catatan.app.data.Revision
import com.catatan.app.data.Store
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

/* ============================================================
   SyncManager — Realtime Database + Firebase Storage.

   Semua data berada di bawah /users/{uid}. Akses dijaga oleh rules
   Firebase (auth.uid === uid), jadi akun lain tidak bisa membaca.
   Konflik diselesaikan per-item: nilai dengan updatedAt/createdAt
   terbaru yang menang. Catatan terhapus memakai tombstone (isDeleted).
   ============================================================ */

class SyncManager(
    context: Context,
    private val repo: Repo,
    private val authManager: AuthManager
) {

    sealed class SyncState {
        data object Idle : SyncState()
        data object Syncing : SyncState()
        data class Success(val at: Long) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val prefs = context.applicationContext
        .getSharedPreferences("catatan-sync", Context.MODE_PRIVATE)

    var autoSync: Boolean
        get() = prefs.getBoolean("auto_sync", true)
        set(value) { prefs.edit().putBoolean("auto_sync", value).apply() }

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val database by lazy { FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL) }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val mutex = Mutex()

    /** Tarik data cloud, gabungkan dengan lokal, lalu dorong yang lebih baru. */
    suspend fun syncNow(): Result<Unit> {
        val uid = authManager.currentUid
            ?: return Result.failure(IllegalStateException("Belum masuk dengan Google"))
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                _state.value = SyncState.Syncing
                runCatching {
                    runCatching { syncData(uid) }
                        .onFailure { throw IllegalStateException("Database: ${it.message}", it) }
                    runCatching { syncMedia(uid) }
                        .onFailure { throw IllegalStateException("Media: ${it.message}", it) }
                    _state.value = SyncState.Success(System.currentTimeMillis())
                }.onFailure { e ->
                    _state.value = SyncState.Error(e.message ?: "Gagal sinkronisasi")
                }
            }
        }
    }

    /** Hapus salinan cloud akun ini (media ikut terhapus). */
    suspend fun deleteRemoteData(): Result<Unit> {
        val uid = authManager.currentUid
            ?: return Result.failure(IllegalStateException("Belum masuk dengan Google"))
        return withContext(Dispatchers.IO) {
            runCatching {
                database.reference.child("users").child(uid).removeValue().await()
                val listed = runCatching {
                    storage.reference.child("users").child(uid).child("media").listAll().await()
                }
                val listing = listed.getOrNull()
                if (listing != null) {
                    listing.items.forEach { it.delete().await() }
                } else {
                    val e = listed.exceptionOrNull()
                    if (e !is StorageException || e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                        throw e ?: error("Gagal membaca daftar media")
                    }
                }
                _state.value = SyncState.Idle
            }.onFailure { e ->
                _state.value = SyncState.Error(e.message ?: "Gagal menghapus data cloud")
            }
        }
    }

    fun stop() { _state.value = SyncState.Idle }

    /* ----------------------------------------------------------
       Data (notes / categories / revisions / settings)
       ---------------------------------------------------------- */

    private suspend fun syncData(uid: String) {
        val snapshot = database.reference.child("users").child(uid).get().await()

        val remoteNotes = decodeChildren(snapshot.child("notes"), Note.serializer())
        val remoteCategories = decodeChildren(snapshot.child("categories"), Category.serializer())
        val remoteRevisions = decodeChildren(snapshot.child("revisions"), Revision.serializer())
        val remoteSettings = snapshot.child("settings").getValue(String::class.java)
            ?.let { raw -> runCatching { repo.json.decodeFromString(AppSettings.serializer(), raw) }.getOrNull() }

        val local = repo.current
        val mergedNotes = mergeById(
            local.notes, remoteNotes.values.toList(),
            { it.id }, { it.updatedAt }
        )
        val mergedCategories = mergeById(
            local.categories, remoteCategories.values.toList(),
            { it.id }, { it.updatedAt }
        )
        val mergedRevisions = mergeById(
            local.revisions, remoteRevisions.values.toList(),
            { it.id }, { it.createdAt }
        )
        val mergedSettings = when {
            remoteSettings == null -> local.settings
            remoteSettings.settingsUpdatedAt > local.settings.settingsUpdatedAt -> remoteSettings
            else -> local.settings
        }

        val merged = Store(
            notes = mergedNotes,
            categories = mergedCategories,
            revisions = mergedRevisions,
            settings = mergedSettings
        )
        if (merged != local) repo.replaceAll(merged)

        // Dorong hanya item yang berbeda dari cloud (local menang atau belum ada).
        val updates = mutableMapOf<String, Any?>()
        mergedNotes.forEach { item ->
            if (remoteNotes[item.id] != item) {
                updates["notes/${item.id}"] = repo.json.encodeToString(Note.serializer(), item)
            }
        }
        mergedCategories.forEach { item ->
            if (remoteCategories[item.id] != item) {
                updates["categories/${item.id}"] =
                    repo.json.encodeToString(Category.serializer(), item)
            }
        }
        mergedRevisions.forEach { item ->
            if (remoteRevisions[item.id] != item) {
                updates["revisions/${item.id}"] =
                    repo.json.encodeToString(Revision.serializer(), item)
            }
        }
        if (remoteSettings != mergedSettings) {
            updates["settings"] = repo.json.encodeToString(AppSettings.serializer(), mergedSettings)
        }
        if (updates.isNotEmpty()) {
            database.reference.child("users").child(uid).updateChildren(updates).await()
        }
    }

    private fun <T> decodeChildren(parent: DataSnapshot, serializer: KSerializer<T>): Map<String, T> {
        val out = linkedMapOf<String, T>()
        parent.children.forEach { snap ->
            val key = snap.key ?: return@forEach
            val raw = snap.getValue(String::class.java) ?: return@forEach
            runCatching { repo.json.decodeFromString(serializer, raw) }
                .getOrNull()?.let { out[key] = it }
        }
        return out
    }

    private fun <T> mergeById(
        local: List<T>,
        remote: List<T>,
        id: (T) -> String,
        time: (T) -> Long
    ): List<T> {
        val map = linkedMapOf<String, T>()
        remote.forEach { map[id(it)] = it }
        local.forEach { item ->
            val key = id(item)
            val existing = map[key]
            // Remote menang pada timestamp sama agar dua perangkat konvergen.
            if (existing == null || time(item) > time(existing)) map[key] = item
        }
        return map.values.toList()
    }

    /* ----------------------------------------------------------
       Media — Firebase Storage, penunjuk catatan-media:<nama> tetap
       dipakai supaya HTML tidak perlu diubah saat pindah perangkat.
       ---------------------------------------------------------- */

    private suspend fun syncMedia(uid: String) {
        val safeName = Regex("^[A-Za-z0-9._-]+$")
        val remoteDir = storage.reference.child("users").child(uid).child("media")

        // Folder media belum ada -> listAll() bisa gagal dengan OBJECT_NOT_FOUND.
        val listed = runCatching { remoteDir.listAll().await() }
        val listing = listed.getOrNull()
        if (listing == null) {
            val e = listed.exceptionOrNull()
            if (e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) return
            throw e ?: error("Gagal membaca daftar media")
        }

        val remoteItems = listing.items.filter { it.name.matches(safeName) }
        val remoteNames = remoteItems.map { it.name }.toSet()
        val localNames = MediaStore.listNames().toSet()

        for (name in localNames - remoteNames) {
            val file = MediaStore.fileNamed(name) ?: continue
            val uploaded = runCatching { remoteDir.child(name).putBytes(file.readBytes()).await() }
            if (uploaded.isFailure) {
                val e = uploaded.exceptionOrNull()
                if (e !is StorageException || e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e!!
            }
        }
        for (item in remoteItems) {
            if (item.name !in localNames) {
                val downloaded = runCatching { item.getBytes(20L * 1024 * 1024).await() }
                val bytes = downloaded.getOrNull()
                if (bytes == null) {
                    val e = downloaded.exceptionOrNull()
                    if (e !is StorageException || e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                        throw e ?: error("Gagal mengunduh ${item.name}")
                    }
                } else {
                    MediaStore.saveBytes(item.name, bytes)
                }
            }
        }
    }
}
