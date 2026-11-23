package com.catatan.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catatan.app.BuildConfig
import com.catatan.app.data.AppSettings
import com.catatan.app.data.Backup
import com.catatan.app.data.BackupSummary
import com.catatan.app.data.Category
import com.catatan.app.data.Note
import com.catatan.app.data.Repo
import com.catatan.app.data.Revision
import com.catatan.app.data.Store
import com.catatan.app.update.CheckResult
import com.catatan.app.update.UpdateInfo
import com.catatan.app.update.Updater
import com.catatan.app.util.uid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/* ============================================================
   Navigasi
   ============================================================ */

sealed class Screen(val route: String) {
    data object Notes : Screen("notes")
    data object Favorites : Screen("favorites")
    data object Archive : Screen("archive")
    data object Trash : Screen("trash")
    data object Folders : Screen("folders")
    data object Settings : Screen("settings")
    data object Search : Screen("search")
    data class Folder(val id: String) : Screen("folder/$id")
    data class Tag(val tag: String) : Screen("tag/$tag")
    data class Editor(val noteId: String) : Screen("note/$noteId")
    data class Preview(val noteId: String) : Screen("preview/$noteId")
}

/* ============================================================
   Modal (bottom sheet)
   ============================================================ */

sealed class Modal {
    data class Confirm(
        val title: String,
        val message: String? = null,
        val confirmText: String = "Hapus",
        val danger: Boolean = true,
        val onConfirm: () -> Unit
    ) : Modal()

    data object PageMenu : Modal()
    data object SortSheet : Modal()
    data class NoteMenu(val noteId: String, val fromEditor: Boolean = false) : Modal()
    data class MoveToFolder(val noteIds: List<String>) : Modal()
    data class FolderForm(val categoryId: String? = null) : Modal()
    data class FolderMenu(val categoryId: String) : Modal()
    data class NoteStyle(val noteId: String) : Modal()
    data class NoteInfo(val noteId: String) : Modal()
    data class Versions(val noteId: String) : Modal()
    data class VersionPreview(val noteId: String, val revisionId: String) : Modal()
    data object SearchTips : Modal()
    data object About : Modal()
    data class ImportMode(val summary: BackupSummary, val fileName: String) : Modal()
    data object UpdateInfoDialog : Modal()
}

data class ToastData(
    val id: Long,
    val text: String,
    val kind: String = "success",   // success | error | info
    val duration: Long = 3000
)

/* ============================================================
   AppState — semua state & aksi bersama layar
   ============================================================ */

class AppState(val repo: Repo, val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /* --- Navigasi --- */
    var stack by mutableStateOf(listOf<Screen>(Screen.Notes))
        private set

    val screen: Screen get() = stack.lastOrNull() ?: Screen.Notes

    fun push(target: Screen) {
        if (stack.lastOrNull() == target) return
        closeTransient()
        stack = stack + target
    }

    fun replaceTop(target: Screen) {
        closeTransient()
        stack = stack.dropLast(1) + target
    }

    fun resetTo(target: Screen) {
        closeTransient()
        stack = listOf(target)
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
        closeTransient()
    }

    fun popTo(target: Screen) {
        val idx = stack.indexOfFirst { it::class == target::class && it == target }
        stack = if (idx >= 0) stack.subList(0, idx + 1) else listOf(target)
        closeTransient()
    }

    /* --- Transien (drawer/selection) --- */
    var drawerOpen by mutableStateOf(false)
        private set

    var selectionMode by mutableStateOf(false)
    var selected by mutableStateOf(setOf<String>())
        private set

    fun closeTransient() {
        drawerOpen = false
        if (selectionMode) exitSelection()
    }

    fun openDrawer() { drawerOpen = true }
    fun closeDrawer() { drawerOpen = false }

    fun enterSelection(id: String? = null) {
        selectionMode = true
        selected = id?.let { setOf(it) } ?: emptySet()
    }

    fun toggleSelect(id: String) {
        selected = if (selected.contains(id)) selected - id else selected + id
        if (selected.isEmpty() && selectionMode && !selectionFromToolbar) exitSelection()
    }

    var selectionFromToolbar by mutableStateOf(false)
        private set

    fun enterSelectionFromToolbar() {
        selectionFromToolbar = true
        selectionMode = true
        selected = emptySet()
    }

    fun selectAll(ids: List<String>) { selected = ids.toSet() }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
        selectionFromToolbar = false
    }

    /* --- Toast --- */
    var toast by mutableStateOf<ToastData?>(null)
        private set
    private var toastSeq = 0L

    fun showToast(text: String, kind: String = "success", duration: Long = 3000) {
        toast = ToastData(++toastSeq, text, kind, duration)
    }

    fun dismissToast() { toast = null }

    /* --- Modal --- */
    var modal by mutableStateOf<Modal?>(null)
        private set

    fun openModal(m: Modal) { modal = m }

    fun closeModal() { modal = null }

    fun confirm(
        title: String,
        message: String? = null,
        confirmText: String = "Hapus",
        danger: Boolean = true,
        onConfirm: () -> Unit
    ) {
        modal = Modal.Confirm(title, message, confirmText, danger, onConfirm)
    }

    /* --- Pengaturan --- */
    val settings: AppSettings get() = repo.current.settings

    fun updateSettings(block: (AppSettings) -> AppSettings) = repo.updateSettings(block)

    /* --- Aksi catatan --- */

    fun newNote(categoryId: String? = null): String {
        val note = repo.createNote(
            categoryId = categoryId,
            editorFont = repo.current.settings.editorFont
        )
        push(Screen.Editor(note.id))
        return note.id
    }

    fun openNote(note: Note) {
        if (note.isDeleted) push(Screen.Preview(note.id))
        else push(Screen.Editor(note.id))
    }

    fun trashNotes(ids: List<String>) {
        if (ids.isEmpty()) return
        val doIt = {
            repo.trashNotes(ids, System.currentTimeMillis())
            exitSelection()
            showToast(
                if (ids.size == 1) "Catatan dipindahkan ke sampah"
                else "${ids.size} catatan dipindahkan ke sampah"
            )
        }
        if (settings.confirmDelete) {
            val title = if (ids.size == 1) "Pindahkan ke sampah?" else "Hapus ${ids.size} catatan?"
            val message = if (ids.size == 1)
                "Catatan akan dipindahkan ke sampah dan dihapus permanen setelah 30 hari."
            else "Catatan akan dipindahkan ke sampah dan dihapus permanen setelah 30 hari."
            confirm(title, message, "Pindahkan ke sampah", true, doIt)
        } else doIt()
    }

    fun restoreNotes(ids: List<String>) {
        if (ids.isEmpty()) return
        repo.restoreNotes(ids, System.currentTimeMillis())
        exitSelection()
        showToast(if (ids.size == 1) "Catatan dipulihkan" else "${ids.size} catatan dipulihkan")
    }

    fun deletePermanently(ids: List<String>) {
        if (ids.isEmpty()) return
        confirm(
            if (ids.size == 1) "Hapus permanen?" else "Hapus permanen ${ids.size} catatan?",
            "Tindakan ini tidak dapat dibatalkan. Catatan dan riwayat versinya akan dihapus dari perangkat.",
            "Hapus permanen",
            true
        ) {
            repo.deleteNotesPermanently(ids)
            exitSelection()
            showToast(if (ids.size == 1) "Catatan dihapus permanen" else "${ids.size} catatan dihapus permanen")
        }
    }

    fun togglePin(ids: List<String>, value: Boolean) {
        repo.setNoteFlags(ids, System.currentTimeMillis()) { it.copy(isPinned = value) }
        exitSelection()
    }

    fun toggleFavorite(ids: List<String>, value: Boolean) {
        repo.setNoteFlags(ids, System.currentTimeMillis()) { it.copy(isFavorite = value) }
        exitSelection()
    }

    fun toggleArchive(ids: List<String>, value: Boolean) {
        repo.setNoteFlags(ids, System.currentTimeMillis()) { it.copy(isArchived = value) }
        exitSelection()
        showToast(if (ids.size == 1) "Catatan diperbarui" else "${ids.size} catatan diperbarui")
    }

    fun moveToFolder(ids: List<String>, folderId: String?) {
        repo.setNoteFlags(ids, System.currentTimeMillis()) { it.copy(categoryId = folderId) }
        exitSelection()
        showToast(if (ids.size == 1) "Catatan dipindahkan" else "${ids.size} catatan dipindahkan")
    }

    fun emptyTrash() {
        val count = repo.current.notes.count { it.isDeleted }
        if (count == 0) {
            showToast("Sampah sudah kosong.", "info")
            return
        }
        confirm(
            "Kosongkan sampah?",
            "Semua $count catatan di sampah akan dihapus permanen beserta riwayat versinya.",
            "Kosongkan sampah",
            true
        ) {
            repo.emptyTrash()
            showToast("Sampah dikosongkan")
        }
    }

    /* --- Folder --- */

    fun createFolder(name: String, color: String, icon: String) {
        val now = System.currentTimeMillis()
        repo.upsertCategory(
            Category(id = uid(), name = name, color = color, icon = icon, createdAt = now, updatedAt = now)
        )
        showToast("Folder “$name” dibuat")
    }

    fun updateFolder(category: Category) {
        repo.upsertCategory(category.copy(updatedAt = System.currentTimeMillis()))
        showToast("Folder diperbarui")
    }

    fun deleteFolder(category: Category) {
        val count = repo.current.notes.count { it.categoryId == category.id }
        val message = if (count > 0)
            "Folder “${category.name}” akan dihapus. $count catatan di dalamnya tetap aman dan menjadi tanpa folder."
        else "Folder “${category.name}” akan dihapus."
        confirm("Hapus folder?", message, "Hapus folder", true) {
            repo.deleteCategory(category.id)
            if (screen is Screen.Folder && (screen as Screen.Folder).id == category.id) pop()
            showToast("Folder dihapus. Catatan tetap aman.")
        }
    }

    /* --- Revisi --- */

    fun saveRevision(note: Note) {
        repo.addRevision(
            Revision(
                id = uid(),
                noteId = note.id,
                title = note.title,
                content = note.content,
                plainText = note.plainText,
                wordCount = note.wordCount,
                createdAt = System.currentTimeMillis()
            )
        )
        repo.pruneRevisions(note.id, 24)
    }

    /* --- Impor backup --- */

    fun applyImport(summary: BackupSummary, mode: String) {
        val incoming = Backup.toStore(summary.file, mode, repo.current)
        if (mode == "replace") repo.replaceAll(incoming) else repo.mergeAll(incoming)
        repo.purgeExpiredTrash()
        exitSelection()
        resetTo(Screen.Notes)
        showToast("Impor selesai: ${summary.total} catatan", duration = 4000)
    }

    /* ============================================================
       Pembaruan aplikasi (GitHub Releases)
       ============================================================ */

    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateBanner by mutableStateOf(false)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set
    var downloadProgress by mutableStateOf<Float?>(null)
        private set

    private var pendingApk: File? = null
    var permissionLauncher: ActivityResultLauncher<Intent>? = null

    fun checkForUpdate(manual: Boolean) {
        if (checkingUpdate) return
        checkingUpdate = true
        scope.launch {
            val result = Updater.check(BuildConfig.UPDATE_URL, BuildConfig.VERSION_NAME)
            checkingUpdate = false
            when (result) {
                is CheckResult.Available -> {
                    updateInfo = result.info
                    updateBanner = true
                    if (manual) showToast("Pembaruan tersedia.")
                }
                CheckResult.UpToDate ->
                    if (manual) showToast("Kamu sudah memakai versi terbaru.")
                CheckResult.Error ->
                    if (manual) showToast("Gagal memeriksa pembaruan.", "error", 5000)
            }
        }
    }

    fun dismissUpdateBanner() { updateBanner = false }

    fun startUpdate() {
        val info = updateInfo ?: return
        if (downloadProgress != null) return
        downloadProgress = 0f
        showToast("Mengunduh pembaruan…", "info", 2500)
        scope.launch {
            val file = Updater.download(appContext, info.apkUrl) { p ->
                downloadProgress = p
            }
            downloadProgress = null
            if (file == null) {
                showToast("Gagal mengunduh pembaruan.", "error", 5000)
                return@launch
            }
            if (Updater.canInstall(appContext)) {
                updateBanner = false
                Updater.install(appContext, file)
            } else {
                pendingApk = file
                val launcher = permissionLauncher
                if (launcher != null) {
                    launcher.launch(Updater.permissionIntent(appContext))
                } else {
                    showToast("Izinkan pemasangan dari sumber ini, lalu coba lagi.", "error", 5000)
                }
            }
        }
    }

    fun onInstallPermissionResult() {
        val file = pendingApk ?: return
        pendingApk = null
        if (Updater.canInstall(appContext)) {
            updateBanner = false
            Updater.install(appContext, file)
        } else {
            showToast("Izin pemasangan belum diberikan.", "error", 4000)
        }
    }

    fun autoCheckUpdates() {
        scope.launch {
            delay(1800)
            checkForUpdate(manual = false)
        }
    }

    /* --- Penyimpanan --- */
    fun storageInfo(): Pair<Long, Long> = repo.storageUsage()
}

/* ============================================================
   ViewModel pembawa AppState
   ============================================================ */

class AppViewModel(private val repo: Repo, private val appContext: Context) :
    ViewModel() {

    val state = AppState(repo, appContext)

    class Factory(private val repo: Repo, private val context: Context) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(repo, context) as T
    }
}
