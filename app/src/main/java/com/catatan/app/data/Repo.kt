package com.catatan.app.data

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/* ============================================================
   Repo — penyimpanan tunggal (JSON) + seluruh operasi data
   Pengganti IndexedDB pada versi web.
   ============================================================ */

class Repo(context: Context, initialScope: String = "local") {

    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    /* ----------------------------------------------------------
       Scope penyimpanan: "local" atau uid akun Firebase.
       Setiap akun punya file & folder media sendiri, sehingga akun
       berbeda tidak pernah membaca data akun lain di perangkat yang sama.
       ---------------------------------------------------------- */

    private var scopeKey: String = normalizeScope(initialScope)
    private var file: File = fileForScope(scopeKey)

    val scope: String get() = scopeKey
    val isAccountScope: Boolean get() = scopeKey != "local"
    val mediaDirName: String get() = if (isAccountScope) "media-$scopeKey" else "media"

    private fun normalizeScope(value: String): String {
        val clean = value.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (clean.isEmpty() || clean == "local") "local" else clean.take(80)
    }

    private fun fileForScope(key: String): File = File(
        appContext.filesDir,
        if (key == "local") "catatan-store.json" else "catatan-store-$key.json"
    )

    /** Ganti scope akun. Data scope lama tetap utuh di file masing-masing. */
    fun switchScope(next: String) {
        val normalized = normalizeScope(next)
        if (normalized == scopeKey) return
        flushNow()
        scopeKey = normalized
        file = fileForScope(scopeKey)
        load()
    }

    /** Tulis file segera (dipakai sebelum pindah scope / logout). */
    fun flushNow() {
        runBlocking { writeMutex.withLock { writeToDisk() } }
    }

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    private val _store = MutableStateFlow(Store())
    val store: StateFlow<Store> = _store.asStateFlow()

    val current: Store get() = _store.value

    /* ----------------------------------------------------------
       Muat & simpan
       ---------------------------------------------------------- */

    fun load() {
        val loaded = if (file.exists()) {
            runCatching { json.decodeFromString(Store.serializer(), file.readText()) }
                .getOrNull()
        } else null
        _store.value = loaded ?: Store()
    }

    private fun scheduleSave() {
        ioScope.launch {
            writeMutex.withLock { writeToDisk() }
        }
    }

    private fun writeToDisk() {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Store.serializer(), _store.value))
            if (!tmp.renameTo(file)) {
                file.writeText(json.encodeToString(Store.serializer(), _store.value))
                tmp.delete()
            }
        }
    }

    /** Simpan sinkron (dipakai saat flush penting, mis. keluar editor). */
    fun saveNow() {
        ioScope.launch {
            writeMutex.withLock { writeToDisk() }
        }
    }

    private fun mutate(transform: (Store) -> Store) {
        _store.update(transform)
        scheduleSave()
    }

    /* ----------------------------------------------------------
       Catatan
       ---------------------------------------------------------- */

    fun note(id: String): Note? = current.notes.firstOrNull { it.id == id }

    /** Store untuk backup: media lokal dikembalikan ke data URL (kompatibel web). */
    fun exportableStore(): Store = current.copy(
        notes = current.notes.map { note ->
            note.copy(content = MediaStore.exportContent(note.content))
        }
    )

    fun upsertNote(note: Note) = mutate { s ->
        s.copy(notes = if (s.notes.any { it.id == note.id }) {
            s.notes.map { if (it.id == note.id) note else it }
        } else listOf(note) + s.notes)
    }

    fun upsertNotes(notes: List<Note>) = mutate { s ->
        val map = notes.associateBy { it.id }
        val kept = s.notes.map { map[it.id] ?: it }
        val fresh = notes.filter { n -> s.notes.none { it.id == n.id } }
        s.copy(notes = fresh + kept)
    }

    fun deleteNotesPermanently(ids: List<String>) = mutate { s ->
        s.copy(
            notes = s.notes.filterNot { it.id in ids },
            revisions = s.revisions.filterNot { it.noteId in ids }
        )
    }

    /** Pindahkan ke sampah. */
    fun trashNotes(ids: List<String>, now: Long) = mutate { s ->
        s.copy(notes = s.notes.map { n ->
            if (n.id in ids && !n.isDeleted)
                n.copy(isDeleted = true, deletedAt = now, isArchived = false)
            else n
        })
    }

    /** Pulihkan dari sampah. */
    fun restoreNotes(ids: List<String>, now: Long) = mutate { s ->
        s.copy(notes = s.notes.map { n ->
            if (n.id in ids && n.isDeleted)
                n.copy(isDeleted = false, deletedAt = null, updatedAt = now)
            else n
        })
    }

    fun setNoteFlags(ids: List<String>, now: Long, block: (Note) -> Note) = mutate { s ->
        s.copy(notes = s.notes.map { n -> if (n.id in ids) block(n) else n })
    }

    fun createNote(
        categoryId: String? = null,
        tags: List<String> = emptyList(),
        editorFont: String = "sans",
        now: Long = System.currentTimeMillis()
    ): Note {
        val note = Note(
            id = com.catatan.app.util.uid(),
            title = "",
            content = "",
            plainText = "",
            categoryId = categoryId,
            tags = tags,
            paper = "blank",
            font = editorFont,
            createdAt = now,
            updatedAt = now
        )
        mutate { s -> s.copy(notes = listOf(note) + s.notes) }
        return note
    }

    fun duplicateNote(id: String, now: Long): Note? {
        val src = note(id) ?: return null
        val copy = src.copy(
            id = com.catatan.app.util.uid(),
            title = src.title + " (salinan)",
            isPinned = false,
            isDeleted = false,
            deletedAt = null,
            revAt = 0,
            createdAt = now,
            updatedAt = now
        )
        mutate { s -> s.copy(notes = listOf(copy) + s.notes) }
        return copy
    }

    /* ----------------------------------------------------------
       Folder
       ---------------------------------------------------------- */

    fun upsertCategory(category: Category) = mutate { s ->
        s.copy(categories = if (s.categories.any { it.id == category.id }) {
            s.categories.map { if (it.id == category.id) category else it }
        } else s.categories + category)
    }

    fun deleteCategory(id: String) = mutate { s ->
        s.copy(
            categories = s.categories.filterNot { it.id == id },
            notes = s.notes.map { if (it.categoryId == id) it.copy(categoryId = null) else it }
        )
    }

    /* ----------------------------------------------------------
       Revisi
       ---------------------------------------------------------- */

    fun addRevision(rev: Revision) = mutate { s ->
        s.copy(revisions = listOf(rev) + s.revisions)
    }

    fun pruneRevisions(noteId: String, keep: Int = 24) = mutate { s ->
        val mine = s.revisions.filter { it.noteId == noteId }
            .sortedByDescending { it.createdAt }
        if (mine.size <= keep) s
        else {
            val drop = mine.drop(keep).map { it.id }.toSet()
            s.copy(revisions = s.revisions.filterNot { it.id in drop })
        }
    }

    fun deleteRevisions(noteId: String) = mutate { s ->
        s.copy(revisions = s.revisions.filterNot { it.noteId == noteId })
    }

    fun revisionsFor(noteId: String): List<Revision> =
        current.revisions.filter { it.noteId == noteId }.sortedByDescending { it.createdAt }

    /* ----------------------------------------------------------
       Pengaturan
       ---------------------------------------------------------- */

    fun updateSettings(block: (AppSettings) -> AppSettings) = mutate { s ->
        s.copy(settings = block(s.settings).copy(settingsUpdatedAt = System.currentTimeMillis()))
    }

    fun resetSettings(preserveBackup: Boolean = true) = mutate { s ->
        val def = AppSettings()
        s.copy(settings = def.copy(
            lastBackupAt = if (preserveBackup) s.settings.lastBackupAt else 0,
            recentSearches = s.settings.recentSearches,
            settingsUpdatedAt = System.currentTimeMillis()
        ))
    }

    fun pushRecentSearch(term: String) = mutate { s ->
        val t = term.trim()
        if (t.isEmpty()) s else {
            val list = (listOf(t) + s.settings.recentSearches.filterNot { it.equals(t, true) })
                .distinct()
                .take(8)
            s.copy(settings = s.settings.copy(
                recentSearches = list,
                settingsUpdatedAt = System.currentTimeMillis()
            ))
        }
    }

    fun clearRecentSearches() = mutate { s ->
        s.copy(settings = s.settings.copy(
            recentSearches = emptyList(),
            settingsUpdatedAt = System.currentTimeMillis()
        ))
    }

    /* ----------------------------------------------------------
       Sampah
       ---------------------------------------------------------- */

    fun purgeExpiredTrash(now: Long = System.currentTimeMillis()): Int {
        val s = current
        if (!s.settings.autoPurgeTrash) return 0
        val cutoff = now - 30L * 86_400_000
        val expired = s.notes.filter {
            it.isDeleted && (it.deletedAt ?: 0) in 1 until cutoff
        }
        if (expired.isEmpty()) return 0
        val ids = expired.map { it.id }.toSet()
        mutate { st -> st.copy(
            notes = st.notes.filterNot { it.id in ids },
            revisions = st.revisions.filterNot { it.noteId in ids }
        ) }
        return expired.size
    }

    fun emptyTrash() = mutate { s ->
        val ids = s.notes.filter { it.isDeleted }.map { it.id }.toSet()
        s.copy(
            notes = s.notes.filterNot { it.isDeleted },
            revisions = s.revisions.filterNot { it.noteId in ids }
        )
    }

    /* ----------------------------------------------------------
       Impor / ekspor
       ---------------------------------------------------------- */

    fun replaceAll(store: Store) {
        _store.value = store
        scheduleSave()
    }

    fun mergeAll(incoming: Store) = mutate { s ->
        val noteMap = incoming.notes.associateBy { it.id }
        val catMap = incoming.categories.associateBy { it.id }
        val revMap = incoming.revisions.associateBy { it.id }
        s.copy(
            notes = (incoming.notes.filter { n -> s.notes.none { it.id == n.id } } +
                s.notes.map { noteMap[it.id] ?: it }),
            categories = (incoming.categories.filter { c -> s.categories.none { it.id == c.id } } +
                s.categories.map { catMap[it.id] ?: it }),
            revisions = (incoming.revisions.filter { r -> s.revisions.none { it.id == r.id } } +
                s.revisions.map { revMap[it.id] ?: it }),
            settings = incoming.settings
        )
    }

    fun clearEverything() = mutate { Store() }

    /* ----------------------------------------------------------
       Penyimpanan perangkat
       ---------------------------------------------------------- */

    fun storageUsage(): Pair<Long, Long> = runCatching {
        val stat = StatFs(appContext.filesDir.path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val used = appContext.filesDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        used to (total - free + used).coerceAtLeast(total)
    }.getOrDefault(0L to 0L)

    fun storeFileSize(): Long = runCatching { file.length() }.getOrDefault(0L)
}
