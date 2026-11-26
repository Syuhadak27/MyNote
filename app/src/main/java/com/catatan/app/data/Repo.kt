package com.catatan.app.data

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/* ============================================================
   Repo — penyimpanan tunggal (JSON) + seluruh operasi data
   Pengganti IndexedDB pada versi web.

   Catatan aktif dan revisi sengaja disimpan di dua file agar
   menyimpan satu catatan tidak perlu menulis ulang semua revisi.
   ============================================================ */

private const val WRITE_DEBOUNCE_MS = 900L

class Repo(context: Context, initialScope: String = "local") {

    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    @Volatile
    private var pendingWrite = false
    private var writeJob: Job? = null

    /* ----------------------------------------------------------
       Scope penyimpanan: "local" atau uid akun Firebase.
       Setiap akun punya file & folder media sendiri, sehingga akun
       berbeda tidak pernah membaca data akun lain di perangkat yang sama.
       ---------------------------------------------------------- */

    private var scopeKey: String = normalizeScope(initialScope)
    private var file: File = fileForScope(scopeKey)
    private var revisionsFile: File = revisionsFileForScope(scopeKey)

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

    private fun revisionsFileForScope(key: String): File = File(
        appContext.filesDir,
        if (key == "local") "catatan-revisions.json" else "catatan-revisions-$key.json"
    )

    /** Ganti scope akun. Data scope lama tetap utuh di file masing-masing. */
    fun switchScope(next: String) {
        val normalized = normalizeScope(next)
        if (normalized == scopeKey) return
        flushNow()
        scopeKey = normalized
        file = fileForScope(scopeKey)
        revisionsFile = revisionsFileForScope(scopeKey)
        load()
    }

    /** Tulis file segera (dipakai sebelum pindah scope / logout). */
    fun flushNow() {
        runBlocking { flushPendingWrite() }
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

    /** Migrasi sekali dari format lama yang menyimpan revisi di file utama. */
    private fun migrateLegacy() {
        if (!file.exists() || revisionsFile.exists()) return
        val legacy = runCatching { json.decodeFromString(Store.serializer(), file.readText()) }
            .getOrNull() ?: return
        if (legacy.revisions.isEmpty()) return
        runCatching {
            atomicWrite(
                revisionsFile,
                json.encodeToString(ListSerializer(Revision.serializer()), legacy.revisions)
            )
            atomicWrite(
                file,
                json.encodeToString(Store.serializer(), legacy.copy(revisions = emptyList()))
            )
        }
    }

    fun load() {
        migrateLegacy()
        val main = if (file.exists()) {
            runCatching { json.decodeFromString(Store.serializer(), file.readText()) }.getOrNull()
        } else null
        val revisions = if (revisionsFile.exists()) {
            runCatching {
                json.decodeFromString(ListSerializer(Revision.serializer()), revisionsFile.readText())
            }.getOrNull() ?: emptyList()
        } else emptyList()
        _store.value = (main ?: Store()).copy(revisions = revisions)
    }

    private fun scheduleSave() {
        pendingWrite = true
        if (writeJob?.isActive == true) return
        writeJob = ioScope.launch {
            delay(WRITE_DEBOUNCE_MS)
            writeMutex.withLock {
                if (pendingWrite) {
                    writeToDisk()
                    pendingWrite = false
                }
            }
        }
    }

    private fun atomicWrite(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    private fun writeToDisk() {
        runCatching {
            val s = _store.value
            atomicWrite(
                file,
                json.encodeToString(Store.serializer(), s.copy(revisions = emptyList()))
            )
            atomicWrite(
                revisionsFile,
                json.encodeToString(ListSerializer(Revision.serializer()), s.revisions)
            )
        }
    }

    private suspend fun flushPendingWrite() {
        writeMutex.withLock {
            writeToDisk()
            pendingWrite = false
        }
    }

    /** Simpan sinkron (dipakai saat flush penting, mis. keluar editor). */
    fun saveNow() {
        ioScope.launch { flushPendingWrite() }
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
        if (notes.isEmpty()) s else {
            val map = notes.associateBy { it.id }
            val existingIds = s.notes.mapTo(HashSet(s.notes.size)) { it.id }
            val kept = s.notes.map { map[it.id] ?: it }
            val fresh = notes.filter { it.id !in existingIds }
            s.copy(notes = fresh + kept)
        }
    }

    fun deleteNotesPermanently(ids: List<String>) = mutate { s ->
        if (ids.isEmpty()) s else {
            val idSet = ids.toSet()
            s.copy(
                notes = s.notes.filterNot { it.id in idSet },
                revisions = s.revisions.filterNot { it.noteId in idSet }
            )
        }
    }

    /** Pindahkan ke sampah. */
    fun trashNotes(ids: List<String>, now: Long) = mutate { s ->
        if (ids.isEmpty()) s else {
            val idSet = ids.toSet()
            s.copy(notes = s.notes.map { n ->
                if (n.id in idSet && !n.isDeleted)
                    n.copy(isDeleted = true, deletedAt = now, isArchived = false)
                else n
            })
        }
    }

    /** Pulihkan dari sampah. */
    fun restoreNotes(ids: List<String>, now: Long) = mutate { s ->
        if (ids.isEmpty()) s else {
            val idSet = ids.toSet()
            s.copy(notes = s.notes.map { n ->
                if (n.id in idSet && n.isDeleted)
                    n.copy(isDeleted = false, deletedAt = null, updatedAt = now)
                else n
            })
        }
    }

    fun setNoteFlags(ids: List<String>, now: Long, block: (Note) -> Note) = mutate { s ->
        if (ids.isEmpty()) s else {
            val idSet = ids.toSet()
            s.copy(notes = s.notes.map { n -> if (n.id in idSet) block(n) else n })
        }
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
        val noteIds = s.notes.mapTo(HashSet(s.notes.size)) { it.id }
        val catIds = s.categories.mapTo(HashSet(s.categories.size)) { it.id }
        val revIds = s.revisions.mapTo(HashSet(s.revisions.size)) { it.id }
        s.copy(
            notes = incoming.notes.filter { it.id !in noteIds } + s.notes.map { noteMap[it.id] ?: it },
            categories = incoming.categories.filter { it.id !in catIds } +
                s.categories.map { catMap[it.id] ?: it },
            revisions = incoming.revisions.filter { it.id !in revIds } +
                s.revisions.map { revMap[it.id] ?: it },
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

    fun storeFileSize(): Long = runCatching {
        (if (file.exists()) file.length() else 0L) +
            (if (revisionsFile.exists()) revisionsFile.length() else 0L)
    }.getOrDefault(0L)
}
