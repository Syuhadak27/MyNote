package com.catatan.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.BuildConfig
import com.catatan.app.data.Backup
import com.catatan.app.data.Block
import com.catatan.app.data.Category
import com.catatan.app.data.HtmlCodec
import com.catatan.app.data.Query
import com.catatan.app.data.Revision
import com.catatan.app.ui.theme.CatatanTheme
import com.catatan.app.ui.theme.fontFamilyOf
import com.catatan.app.ui.theme.parseHex
import com.catatan.app.ui.theme.rgba
import com.catatan.app.util.fmtDate
import com.catatan.app.util.fmtDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ============================================================
   Konstanta bersama
   ============================================================ */

private val FOLDER_COLORS = listOf(
    "#5b7cfa", "#8b5cf6", "#ec4899", "#ef4444", "#f97316",
    "#f59e0b", "#10b981", "#06b6d4", "#64748b", "#0ea5e9"
)

private val FOLDER_ICON_KEYS = listOf(
    "folder", "folderOpen", "note", "notes", "star", "tag",
    "home", "archive", "image", "calendar", "clock", "sparkles"
)

private val PAPER_COLORS = listOf(
    "#fde68a", "#fbcfe8", "#bfdbfe", "#bbf7d0",
    "#ddd6fe", "#fed7aa", "#fecaca", "#e2e8f0"
)

private val SORT_OPTIONS = listOf(
    "updatedAt" to ("Terakhir diperbarui" to "Terbaru di atas"),
    "createdAt" to ("Terbaru dibuat" to "Berdasarkan tanggal dibuat"),
    "title" to ("Judul A–Z" to "Urut abjad"),
    "oldest" to ("Paling lama" to "Terlama di atas")
)

/** Ikon nyata untuk nama ikon folder yang tersimpan pada Category.icon. */
fun folderIconVector(name: String): ImageVector = when (name) {
    "folderOpen" -> Icons.Filled.FolderOpen
    "note" -> Icons.AutoMirrored.Filled.Note
    "notes" -> Icons.AutoMirrored.Filled.Notes
    "star" -> Icons.Filled.Star
    "tag" -> Icons.Filled.Tag
    "home" -> Icons.AutoMirrored.Filled.Article
    "archive" -> Icons.Filled.Archive
    "image" -> Icons.Filled.Image
    "calendar" -> Icons.Filled.CalendarToday
    "clock" -> Icons.Filled.Schedule
    "sparkles" -> Icons.Filled.AutoAwesome
    else -> Icons.Filled.Folder
}

/* ============================================================
   Aksi backup (dipakai menu halaman & layar pengaturan)
   ============================================================ */

class BackupActions(
    val exportBackup: () -> Unit,
    val exportMarkdown: (String) -> Unit,
    val importBackup: () -> Unit
)

@Composable
fun rememberBackupActions(state: AppState): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var markdownText by remember { mutableStateOf("") }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val target = uri ?: return@rememberLauncherForActivityResult
        val repo = state.repo
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val text = Backup.encode(Backup.build(repo.current))
                val stream = context.contentResolver.openOutputStream(target)
                    ?: error("Tidak dapat menulis file backup.")
                stream.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
            }
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    repo.updateSettings { s -> s.copy(lastBackupAt = System.currentTimeMillis()) }
                    state.showToast("Backup berhasil dibuat")
                }.onFailure {
                    state.showToast(it.message ?: "Gagal membuat backup.", "error", 5000)
                }
            }
        }
    }

    val markdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val target = uri ?: return@rememberLauncherForActivityResult
        val text = markdownText
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val stream = context.contentResolver.openOutputStream(target)
                    ?: error("Tidak dapat menulis file Markdown.")
                stream.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { state.showToast("File Markdown dibuat") }
                    .onFailure { state.showToast(it.message ?: "Gagal menulis file.", "error", 5000) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                Backup.decode(readLimitedText(context, target))
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { file ->
                    state.openModal(Modal.ImportMode(Backup.summarize(file), documentName(context, target)))
                }.onFailure {
                    state.showToast(it.message ?: "Gagal mengimpor backup.", "error", 5000)
                }
            }
        }
    }

    return BackupActions(
        exportBackup = { jsonLauncher.launch(Backup.fileName()) },
        exportMarkdown = { text ->
            markdownText = text
            markdownLauncher.launch(Backup.markdownFileName())
        },
        importBackup = { importLauncher.launch(arrayOf("application/json")) }
    )
}

/** Baca teks URI; tolak berkas > 100 MB. */
private fun readLimitedText(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Tidak dapat membaca file.")
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    var total = 0L
    input.use { stream ->
        while (true) {
            val read = stream.read(chunk)
            if (read <= 0) break
            total += read
            if (total > 100L * 1024 * 1024)
                throw IllegalArgumentException("Ukuran file terlalu besar (maksimal 100 MB).")
            buffer.write(chunk, 0, read)
        }
    }
    return buffer.toString("UTF-8")
}

private fun documentName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "backup.json"

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Catatan", text))
}

/* ============================================================
   Potongan UI menu
   ============================================================ */

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (danger) c.dangerSoft else c.accentSofter),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) c.danger else c.accentStrong,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (danger) c.danger else c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                lineHeight = 19.sp
            )
            if (sub != null) {
                Text(
                    sub,
                    color = c.text3,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RadioRow(
    selected: Boolean,
    label: String,
    sub: String,
    onClick: () -> Unit
) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) c.accentStrong else c.text3,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.W500)
            Text(sub, color = c.text3, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun FolderPickRow(
    icon: ImageVector,
    color: String?,
    label: String,
    sub: String?,
    checked: Boolean,
    onClick: () -> Unit
) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (color != null) rgba(color, 0.16f) else c.surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color?.let { parseHex(it) } ?: c.text2,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.W500)
            if (sub != null) Text(sub, color = c.text3, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (checked) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Terpilih",
                tint = c.accentStrong,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ModalCancelButton(label: String = "Batal", onClose: () -> Unit) {
    AppButton(label, onClose, BtnType.GHOST, block = true)
}

/* ============================================================
   ModalHost
   ============================================================ */

@Composable
fun ModalHost(state: AppState) {
    val backup = rememberBackupActions(state)
    val modal = state.modal ?: return

    when (modal) {
        is Modal.Confirm -> ConfirmModal(state, modal)
        Modal.PageMenu -> PageMenuModal(state, backup)
        Modal.SortSheet -> SortSheetModal(state)
        is Modal.NoteMenu -> NoteMenuModal(state, backup, modal)
        is Modal.MoveToFolder -> MoveToFolderModal(state, modal)
        is Modal.FolderForm -> FolderFormModal(state, modal)
        is Modal.FolderMenu -> FolderMenuModal(state, modal)
        is Modal.NoteStyle -> NoteStyleModal(state, modal)
        is Modal.NoteInfo -> NoteInfoModal(state, modal)
        is Modal.Versions -> VersionsModal(state, modal)
        is Modal.VersionPreview -> VersionPreviewModal(state, modal)
        Modal.SearchTips -> SearchTipsModal(state)
        Modal.About -> AboutModal(state)
        is Modal.ImportMode -> ImportModeModal(state, modal)
        Modal.UpdateInfoDialog -> UpdateInfoModal(state)
        else -> Unit
    }
}

/* ============================================================
   1. Konfirmasi
   ============================================================ */

@Composable
private fun ConfirmModal(state: AppState, modal: Modal.Confirm) {
    val c = CatatanTheme.colors
    val close: () -> Unit = { state.closeModal() }

    AppModal(
        title = modal.title,
        onClose = close,
        footer = {
            ModalFooterButtons(
                confirmText = modal.confirmText,
                confirmType = if (modal.danger) BtnType.DANGER else BtnType.PRIMARY,
                onConfirm = {
                    modal.onConfirm()
                    state.closeModal()
                },
                cancelText = "Batal",
                onCancel = close
            )
        }
    ) {
        if (!modal.message.isNullOrBlank()) {
            Text(
                modal.message,
                color = c.text2,
                fontSize = 13.5.sp,
                lineHeight = 20.sp
            )
        }
    }
}

/* ============================================================
   2. Menu halaman
   ============================================================ */

@Composable
private fun PageMenuModal(state: AppState, backup: BackupActions) {
    val close: () -> Unit = { state.closeModal() }
    val screen = state.screen

    AppModal(
        icon = Icons.Filled.Menu,
        title = "Menu halaman",
        onClose = close,
        footer = { ModalCancelButton("Batal", close) }
    ) {
        when {
            screen is Screen.Settings -> {
                MenuItem(Icons.Filled.Download, "Ekspor backup") {
                    close()
                    backup.exportBackup()
                }
                MenuItem(Icons.Filled.Upload, "Impor backup") { backup.importBackup() }
                AppDivider(Modifier.padding(vertical = 6.dp))
                MenuItem(Icons.Filled.Info, "Tentang aplikasi") { state.openModal(Modal.About) }
            }

            screen is Screen.Folders -> {
                MenuItem(Icons.Filled.CreateNewFolder, "Folder baru") {
                    state.openModal(Modal.FolderForm(null))
                }
                MenuItem(Icons.Filled.Add, "Catatan baru") {
                    close()
                    state.newNote()
                }
                MenuItem(Icons.AutoMirrored.Filled.Sort, "Urutkan & tampilan") {
                    state.openModal(Modal.SortSheet)
                }
                AppDivider(Modifier.padding(vertical = 6.dp))
                MenuItem(Icons.Filled.Download, "Ekspor backup") {
                    close()
                    backup.exportBackup()
                }
            }

            else -> {
                if (!state.selectionMode) {
                    MenuItem(Icons.Filled.CheckCircle, "Pilih catatan") {
                        close()
                        state.enterSelectionFromToolbar()
                    }
                }
                MenuItem(Icons.AutoMirrored.Filled.Sort, "Urutkan & tampilan") {
                    state.openModal(Modal.SortSheet)
                }
                MenuItem(Icons.Filled.GridView, "Ubah tampilan daftar") {
                    close()
                    state.updateSettings { s ->
                        s.copy(viewMode = if (s.viewMode == "list") "grid" else "list")
                    }
                }
                MenuItem(Icons.Filled.Add, "Catatan baru") {
                    close()
                    state.newNote()
                }
                MenuItem(Icons.Filled.CreateNewFolder, "Folder baru") {
                    state.openModal(Modal.FolderForm(null))
                }
                AppDivider(Modifier.padding(vertical = 6.dp))
                MenuItem(Icons.Filled.Download, "Ekspor backup") {
                    close()
                    backup.exportBackup()
                }
                MenuItem(Icons.Filled.Print, "Cetak / simpan PDF") {
                    close()
                    state.showToast("Tidak tersedia di perangkat ini", "info")
                }
                if (screen is Screen.Trash) {
                    AppDivider(Modifier.padding(vertical = 6.dp))
                    MenuItem(Icons.Filled.DeleteSweep, "Kosongkan sampah", danger = true) {
                        close()
                        state.emptyTrash()
                    }
                }
            }
        }
    }
}

/* ============================================================
   3. Urutkan & tampilan
   ============================================================ */

@Composable
private fun SortSheetModal(state: AppState) {
    val c = CatatanTheme.colors
    val settings = state.settings
    var sort by remember { mutableStateOf(settings.sortBy) }
    var view by remember { mutableStateOf(settings.viewMode) }
    var grouped by remember { mutableStateOf(settings.groupByDate) }
    val close: () -> Unit = { state.closeModal() }

    AppModal(
        icon = Icons.AutoMirrored.Filled.Sort,
        title = "Urutkan & tampilan",
        onClose = close,
        footer = {
            ModalFooterButtons(
                confirmText = "Terapkan",
                confirmType = BtnType.PRIMARY,
                onConfirm = {
                    state.updateSettings { s ->
                        s.copy(sortBy = sort, viewMode = view, groupByDate = grouped)
                    }
                    state.closeModal()
                },
                cancelText = "Batal",
                onCancel = close
            )
        }
    ) {
        SectionLabel("Urutkan berdasarkan")
        SORT_OPTIONS.forEach { (value, texts) ->
            RadioRow(
                selected = sort == value,
                label = texts.first,
                sub = texts.second
            ) { sort = value }
        }

        AppDivider(Modifier.padding(vertical = 8.dp))

        Text(
            "Tampilan",
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        SegmentedControl(
            options = listOf("list" to "Daftar", "grid" to "Grid"),
            value = view,
            onChange = { view = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(6.dp))

        SwitchRow(
            title = "Kelompokkan per tanggal",
            subtitle = "Hari ini, kemarin, dan seterusnya",
            checked = grouped,
            onChange = { grouped = it }
        )
    }
}

/* ============================================================
   4. Menu catatan
   ============================================================ */

@Composable
private fun NoteMenuModal(state: AppState, backup: BackupActions, modal: Modal.NoteMenu) {
    val c = CatatanTheme.colors
    val context = LocalContext.current
    val repo = state.repo
    val note = repo.note(modal.noteId) ?: return
    val store = repo.current
    val category: Category? = note.categoryId?.let { id ->
        store.categories.firstOrNull { it.id == id }
    }
    val tags = HtmlCodec.noteTags(note)
    val subtitle = note.title.ifBlank { tags.joinToString(" ") { "#$it" } }
        .ifBlank { "Tanpa judul" }
    val close: () -> Unit = { state.closeModal() }

    fun noteText(): String = buildString {
        if (note.title.isNotBlank()) append(note.title).append("\n\n")
        append(note.plainText.ifBlank { HtmlCodec.htmlToText(note.content) })
    }

    fun copyNote() {
        copyToClipboard(context, noteText())
        state.showToast("Teks catatan disalin")
    }

    fun shareNote() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Catatan" })
            putExtra(Intent.EXTRA_TEXT, noteText())
        }
        val launched = runCatching {
            context.startActivity(Intent.createChooser(intent, "Bagikan catatan"))
        }.isSuccess
        if (!launched) copyNote()
    }

    AppModal(
        icon = if (note.isDeleted) Icons.Filled.Delete else Icons.AutoMirrored.Filled.Note,
        title = "Opsi catatan",
        subtitle = subtitle,
        onClose = close,
        footer = { ModalCancelButton("Batal", close) }
    ) {
        if (note.isDeleted) {
            MenuItem(Icons.Filled.Restore, "Pulihkan catatan", "Kembalikan ke daftar catatan") {
                close()
                state.restoreNotes(listOf(note.id))
            }
            MenuItem(Icons.Filled.Description, "Salin teks", "Salin judul dan isi catatan") {
                copyNote()
                close()
            }
            AppDivider(Modifier.padding(vertical = 6.dp))
            MenuItem(
                Icons.Filled.DeleteForever,
                "Hapus permanen",
                sub = "Tidak dapat dikembalikan",
                danger = true
            ) {
                close()
                state.deletePermanently(listOf(note.id))
            }
            return@AppModal
        }

        if (!modal.fromEditor) {
            MenuItem(Icons.Filled.Edit, "Buka catatan") {
                close()
                state.openNote(note)
            }
        }
        MenuItem(
            Icons.Filled.PushPin,
            if (note.isPinned) "Lepas sematan" else "Sematkan di atas"
        ) {
            close()
            state.togglePin(listOf(note.id), !note.isPinned)
        }
        MenuItem(
            Icons.Filled.Star,
            if (note.isFavorite) "Hapus dari favorit" else "Tambahkan ke favorit"
        ) {
            close()
            state.toggleFavorite(listOf(note.id), !note.isFavorite)
        }
        MenuItem(
            Icons.Filled.Archive,
            if (note.isArchived) "Keluarkan dari arsip" else "Arsipkan"
        ) {
            close()
            state.toggleArchive(listOf(note.id), !note.isArchived)
        }
        MenuItem(
            Icons.Filled.Folder,
            "Pindahkan ke folder",
            sub = category?.name ?: "Tanpa folder"
        ) {
            state.openModal(Modal.MoveToFolder(listOf(note.id)))
        }

        AppDivider(Modifier.padding(vertical = 6.dp))

        MenuItem(Icons.Filled.Palette, "Ganti tampilan", "Warna, kertas, dan huruf") {
            state.openModal(Modal.NoteStyle(note.id))
        }
        MenuItem(Icons.Filled.History, "Riwayat versi", "Lihat perubahan sebelumnya") {
            state.openModal(Modal.Versions(note.id))
        }
        MenuItem(Icons.Filled.ContentCopy, "Buat salinan") {
            close()
            repo.duplicateNote(note.id, System.currentTimeMillis())
            state.showToast("Salinan catatan dibuat")
        }
        MenuItem(Icons.Filled.Description, "Salin teks", "Salin judul dan isi catatan") {
            copyNote()
            close()
        }
        MenuItem(Icons.Filled.Share, "Bagikan") { shareNote() }
        MenuItem(Icons.AutoMirrored.Filled.Article, "Ekspor Markdown") {
            close()
            backup.exportMarkdown(Backup.htmlToMarkdown(note.content))
        }
        MenuItem(Icons.Filled.Info, "Info catatan") { state.openModal(Modal.NoteInfo(note.id)) }

        AppDivider(Modifier.padding(vertical = 6.dp))

        MenuItem(Icons.Filled.Delete, "Pindahkan ke sampah", danger = true) {
            close()
            state.trashNotes(listOf(note.id))
        }
    }
}

/* ============================================================
   5. Pindahkan ke folder
   ============================================================ */

@Composable
private fun MoveToFolderModal(state: AppState, modal: Modal.MoveToFolder) {
    val store = state.repo.current
    val notes = store.notes.filter { it.id in modal.noteIds }
    val distinctFolders = notes.map { it.categoryId }.distinct()
    val currentId: String? =
        if (distinctFolders.size == 1) distinctFolders.first() else null
    val noneChecked = distinctFolders.size == 1 && distinctFolders.first() == null
    val counts = Query.folderCounts(store.notes)
    val title = if (modal.noteIds.size == 1) "Pindahkan ke folder"
    else "Pindahkan ${modal.noteIds.size} catatan"
    val close: () -> Unit = { state.closeModal() }

    AppModal(
        icon = Icons.Filled.Folder,
        title = title,
        onClose = close,
        footer = { ModalCancelButton("Batal", close) }
    ) {
        FolderPickRow(
            icon = Icons.Filled.FolderOpen,
            color = null,
            label = "Tanpa folder",
            sub = null,
            checked = noneChecked
        ) {
            close()
            state.moveToFolder(modal.noteIds, null)
        }
        store.categories.forEach { category ->
            FolderPickRow(
                icon = Icons.Filled.Folder,
                color = category.color,
                label = category.name,
                sub = "${counts[category.id] ?: 0} catatan",
                checked = category.id == currentId
            ) {
                close()
                state.moveToFolder(modal.noteIds, category.id)
            }
        }
    }
}

/* ============================================================
   6. Folder baru / ubah folder
   ============================================================ */

@Composable
private fun FolderFormModal(state: AppState, modal: Modal.FolderForm) {
    val c = CatatanTheme.colors
    val existing: Category? = modal.categoryId?.let { id ->
        state.repo.current.categories.firstOrNull { it.id == id }
    }
    val editing = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: FOLDER_COLORS.first()) }
    var iconName by remember { mutableStateOf(existing?.icon ?: "folder") }
    var showError by remember { mutableStateOf(false) }
    val close: () -> Unit = { state.closeModal() }

    val onSave: () -> Unit = {
        if (name.isBlank()) {
            showError = true
        } else {
            val clean = name.trim()
            if (existing != null) {
                state.updateFolder(existing.copy(name = clean, color = color, icon = iconName))
            } else {
                state.createFolder(clean, color, iconName)
            }
            state.closeModal()
        }
    }

    AppModal(
        icon = Icons.Filled.CreateNewFolder,
        title = if (editing) "Ubah folder" else "Folder baru",
        onClose = close,
        footer = {
            ModalFooterButtons(
                confirmText = if (editing) "Simpan perubahan" else "Buat folder",
                confirmType = BtnType.PRIMARY,
                onConfirm = onSave,
                cancelText = "Batal",
                onCancel = close
            )
        }
    ) {
        // Pratinjau kartu folder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(rgba(color, 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    folderIconVector(iconName),
                    contentDescription = null,
                    tint = parseHex(color),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    name.trim().ifBlank { "Folder baru" },
                    color = c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Tampilan folder", color = c.text3, fontSize = 11.5.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Nama folder
        Text(
            "Nama folder",
            color = c.text2,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = 7.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(c.surface2)
                .border(
                    1.dp,
                    if (showError && name.isBlank()) c.danger else c.border,
                    RoundedCornerShape(11.dp)
                )
                .padding(horizontal = 13.dp, vertical = 13.dp)
        ) {
            BasicTextField(
                value = name,
                onValueChange = { value ->
                    name = value.take(60)
                    showError = false
                },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                cursorBrush = SolidColor(c.accent),
                decorationBox = { inner ->
                    if (name.isEmpty()) {
                        Text("Contoh: Kuliah, Ide, Pekerjaan", color = c.text3, fontSize = 14.sp)
                    }
                    inner()
                }
            )
        }
        if (showError && name.isBlank()) {
            Text(
                "Nama folder belum diisi.",
                color = c.danger,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 5.dp, start = 2.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Warna
        Text(
            "Warna",
            color = c.text2,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FOLDER_COLORS.chunked(5).forEach { rowColors ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowColors.forEach { hex ->
                    val active = hex == color
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (active) rgba(hex, 0.18f) else c.surface2)
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) parseHex(hex) else c.border,
                                RoundedCornerShape(11.dp)
                            )
                            .clickable {
                                color = hex
                                showError = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(parseHex(hex))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Ikon
        Text(
            "Ikon",
            color = c.text2,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FOLDER_ICON_KEYS.chunked(6).forEach { rowKeys ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowKeys.forEach { key ->
                    val active = key == iconName
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (active) c.accentSoft else c.surface2)
                            .border(
                                1.dp,
                                if (active) c.accent else c.border,
                                RoundedCornerShape(11.dp)
                            )
                            .clickable { iconName = key },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            folderIconVector(key),
                            contentDescription = key,
                            tint = if (active) c.accentStrong else c.text2,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                repeat(6 - rowKeys.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ============================================================
   7. Menu folder
   ============================================================ */

@Composable
private fun FolderMenuModal(state: AppState, modal: Modal.FolderMenu) {
    val context = LocalContext.current
    val store = state.repo.current
    val category: Category = store.categories.firstOrNull { it.id == modal.categoryId } ?: return
    val count = Query.folderCounts(store.notes)[category.id] ?: 0
    val close: () -> Unit = { state.closeModal() }

    AppModal(
        icon = folderIconVector(category.icon),
        title = "Opsi folder",
        subtitle = category.name,
        onClose = close,
        footer = { ModalCancelButton("Batal", close) }
    ) {
        MenuItem(Icons.Filled.Add, "Catatan baru di folder ini") {
            close()
            state.newNote(category.id)
        }
        MenuItem(Icons.Filled.Edit, "Ubah nama & tampilan") {
            state.openModal(Modal.FolderForm(category.id))
        }
        MenuItem(Icons.Filled.ContentCopy, "Salin nama folder") {
            copyToClipboard(context, category.name)
            close()
            state.showToast("Nama folder disalin")
        }
        AppDivider(Modifier.padding(vertical = 6.dp))
        MenuItem(
            Icons.Filled.Delete,
            "Hapus folder",
            sub = if (count > 0) "$count catatan tidak ikut terhapus" else "Folder kosong",
            danger = true
        ) {
            close()
            state.deleteFolder(category)
        }
    }
}

/* ============================================================
   8. Tampilan catatan
   ============================================================ */

@Composable
private fun NoteStyleModal(state: AppState, modal: Modal.NoteStyle) {
    val c = CatatanTheme.colors
    val note = state.repo.note(modal.noteId) ?: return
    var color by remember { mutableStateOf(note.color) }
    var paper by remember { mutableStateOf(note.paper) }
    var font by remember { mutableStateOf(note.font ?: state.settings.editorFont) }
    val close: () -> Unit = { state.closeModal() }

    AppModal(
        icon = Icons.Filled.Palette,
        title = "Tampilan catatan",
        onClose = close,
        footer = {
            ModalFooterButtons(
                confirmText = "Terapkan",
                confirmType = BtnType.PRIMARY,
                onConfirm = {
                    state.repo.upsertNote(
                        note.copy(color = color, paper = paper, font = font)
                    )
                    state.showToast("Tampilan catatan diperbarui")
                    state.closeModal()
                },
                cancelText = "Batal",
                onCancel = close
            )
        }
    ) {
        // Pratinjau
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color?.let { parseHex(it) } ?: c.surface)
                .border(1.dp, c.border, RoundedCornerShape(14.dp))
        ) {
            if (paper == "lines" || paper == "grid") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 58.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(c.border)
                        )
                        Spacer(Modifier.height(17.dp))
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Contoh judul catatan",
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    fontFamily = fontFamilyOf(font)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Ini pratinjau tampilan catatanmu.",
                    color = c.text2,
                    fontSize = 12.5.sp,
                    fontFamily = fontFamilyOf(font)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Tulis, atur, dan baca dengan nyaman.",
                    color = c.text3,
                    fontSize = 11.5.sp,
                    fontFamily = fontFamilyOf(font)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Warna kertas
        SectionLabel("Warna kertas")
        val colorOptions: List<String?> = listOf(null) + PAPER_COLORS
        colorOptions.chunked(5).forEach { rowColors ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowColors.forEach { hex ->
                    val active = hex == color
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (active) c.accentSoft else c.surface2)
                            .border(
                                1.dp,
                                if (active) c.accent else c.border,
                                RoundedCornerShape(11.dp)
                            )
                            .clickable { color = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (hex == null) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Tanpa warna",
                                tint = if (active) c.accentStrong else c.text3,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(parseHex(hex))
                            )
                        }
                    }
                }
                repeat(5 - rowColors.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // Jenis kertas
        SectionLabel("Jenis kertas")
        SegmentedControl(
            options = listOf(
                "blank" to "Polos",
                "lines" to "Garis",
                "grid" to "Kotak",
                "dots" to "Titik"
            ),
            value = paper,
            onChange = { paper = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )

        // Huruf
        SectionLabel("Huruf")
        SegmentedControl(
            options = listOf("sans" to "Sans", "serif" to "Serif", "mono" to "Mono"),
            value = font,
            onChange = { font = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )
    }
}

/* ============================================================
   9. Info catatan
   ============================================================ */

@Composable
private fun NoteInfoModal(state: AppState, modal: Modal.NoteInfo) {
    val c = CatatanTheme.colors
    val store = state.repo.current
    val note = store.notes.firstOrNull { it.id == modal.noteId } ?: return
    val category: Category? = note.categoryId?.let { id ->
        store.categories.firstOrNull { it.id == id }
    }
    val tags = HtmlCodec.noteTags(note)
    val plain = note.plainText.ifBlank { HtmlCodec.htmlToText(note.content) }
    val words = if (note.wordCount > 0) note.wordCount else HtmlCodec.countWords(plain)
    val images = Regex("<img[\\s>]", RegexOption.IGNORE_CASE)
        .findAll(note.content).count()
    val audios = Regex("<audio[\\s>]", RegexOption.IGNORE_CASE)
        .findAll(note.content).count()
    val close: () -> Unit = { state.closeModal() }

    val rows = listOf(
        "Dibuat" to fmtDateTime(note.createdAt),
        "Diperbarui" to fmtDateTime(note.updatedAt),
        "Folder" to (category?.name ?: "Tanpa folder"),
        "Tag" to (if (tags.isEmpty()) "—" else tags.joinToString(", ") { "#$it" }),
        "Jumlah kata" to "$words kata",
        "Karakter" to "${plain.length} karakter",
        "Media" to "$images gambar · $audios rekaman",
        "ID catatan" to note.id
    )

    AppModal(
        icon = Icons.Filled.Info,
        title = "Info catatan",
        subtitle = note.title.ifBlank { "Tanpa judul" },
        onClose = close,
        footer = { ModalCancelButton("Tutup", close) }
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 9.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    label,
                    color = c.text3,
                    fontSize = 12.sp,
                    modifier = Modifier.width(104.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    value,
                    color = c.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.W500,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/* ============================================================
   10. Riwayat versi
   ============================================================ */

private fun restoreRevision(state: AppState, noteId: String, revision: Revision) {
    state.closeModal()
    state.confirm(
        "Pulihkan versi ini?",
        "Isi catatan saat ini akan disimpan sebagai versi lama sebelum diganti.",
        "Pulihkan versi",
        false
    ) {
        val current = state.repo.note(noteId)
        if (current != null) {
            if (current.title.isNotBlank() || current.content.isNotBlank()) {
                state.saveRevision(current)
            }
            val plain = HtmlCodec.htmlToText(revision.content)
            val now = System.currentTimeMillis()
            state.repo.upsertNote(
                current.copy(
                    title = revision.title,
                    content = revision.content,
                    plainText = plain,
                    wordCount = HtmlCodec.countWords(plain),
                    updatedAt = now,
                    revAt = now
                )
            )
        }
        state.showToast("Versi lama dipulihkan")
    }
}

@Composable
private fun VersionsModal(state: AppState, modal: Modal.Versions) {
    val c = CatatanTheme.colors
    val note = state.repo.note(modal.noteId) ?: return
    val revisions = state.repo.revisionsFor(modal.noteId)
    val close: () -> Unit = { state.closeModal() }

    AppModal(
        icon = Icons.Filled.History,
        title = "Riwayat versi",
        subtitle = note.title.ifBlank { "Tanpa judul" },
        onClose = close,
        footer = { ModalCancelButton("Tutup", close) }
    ) {
        // Versi saat ini
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(c.accentSofter)
                .border(1.dp, c.accent.copy(alpha = 0.28f), RoundedCornerShape(13.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = c.accentStrong,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Versi saat ini", color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.W600)
                Text(
                    "${fmtDateTime(note.updatedAt)} · ${note.wordCount} kata",
                    color = c.text3,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .height(21.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.accentSoft)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Aktif", color = c.accentStrong, fontSize = 10.5.sp, fontWeight = FontWeight.W600)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (revisions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.History, contentDescription = null, tint = c.text3, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(10.dp))
                Text("Belum ada versi lama.", color = c.text2, fontSize = 13.5.sp, fontWeight = FontWeight.W600)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Versi otomatis dibuat saat kamu mengedit.",
                    color = c.text3,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            revisions.forEach { revision ->
                val words = if (revision.wordCount > 0) revision.wordCount
                else HtmlCodec.countWords(
                    revision.plainText.ifBlank { HtmlCodec.htmlToText(revision.content) }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(13.dp))
                        .padding(horizontal = 11.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(c.surface3),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = c.text2,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            revision.title.ifBlank { "Tanpa judul" },
                            color = c.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W600,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${fmtDateTime(revision.createdAt)} · $words kata",
                            color = c.text3,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    TextActionButton(
                        "Lihat",
                        onClick = { state.openModal(Modal.VersionPreview(note.id, revision.id)) }
                    )
                    TextActionButton(
                        "Pulihkan",
                        accent = true,
                        onClick = { restoreRevision(state, note.id, revision) }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/* ============================================================
   11. Pratinjau versi
   ============================================================ */

@Composable
private fun VersionPreviewModal(state: AppState, modal: Modal.VersionPreview) {
    val c = CatatanTheme.colors
    val revision = state.repo.current.revisions.firstOrNull { it.id == modal.revisionId }
        ?: return
    val words = if (revision.wordCount > 0) revision.wordCount
    else HtmlCodec.countWords(
        revision.plainText.ifBlank { HtmlCodec.htmlToText(revision.content) }
    )
    val blocks = remember(revision.content) { HtmlCodec.parse(revision.content) }
    val close: () -> Unit = { state.closeModal() }

    AppModal(
        icon = Icons.Filled.History,
        title = revision.title.ifBlank { "Tanpa judul" },
        subtitle = "${fmtDateTime(revision.createdAt)} · $words kata",
        onClose = close,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End)
            ) {
                AppButton("Tutup", close, BtnType.GHOST)
                AppButton(
                    "Pulihkan versi ini",
                    { restoreRevision(state, modal.noteId, revision) },
                    BtnType.PRIMARY,
                    icon = Icons.Filled.Restore
                )
            }
        }
    ) {
        if (revision.content.isBlank()) {
            Text("Versi ini tidak memiliki isi.", color = c.text3, fontSize = 13.sp)
        } else {
            PreviewBlockList(blocks = blocks, plainTextColor = c.text)
        }
    }
}

/* ============================================================
   12. Tips pencarian
   ============================================================ */

@Composable
private fun SearchTipsModal(state: AppState) {
    val c = CatatanTheme.colors
    val close: () -> Unit = { state.closeModal() }
    val tips = listOf(
        Triple("#tag", "Cari catatan dengan tag tertentu.", "#kuliah"),
        Triple("\"frasa persis\"", "Cari frasa persis dengan tanda kutip.", "\"ide proyek\""),
        Triple(
            "is:pinned|favorite|archived|trash|locked",
            "Saring catatan berdasarkan status.",
            "is:pinned"
        ),
        Triple(
            "has:image|checklist|link|table|audio",
            "Catatan yang punya jenis isi tertentu.",
            "has:image"
        ),
        Triple("before:YYYY-MM-DD", "Catatan sebelum tanggal tertentu.", "before:2026-01-01"),
        Triple("after:YYYY-MM-DD", "Catatan setelah tanggal tertentu.", "after:2026-06-01"),
        Triple("kata1 kata2", "Semua kata harus cocok (AND).", "resep ayam")
    )

    AppModal(
        icon = Icons.Filled.AutoAwesome,
        title = "Tips pencarian",
        subtitle = "Temukan catatan lebih cepat dengan operator.",
        onClose = close,
        footer = { ModalCancelButton("Mengerti", close) }
    ) {
        tips.forEach { (code, desc, example) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    code,
                    color = c.accentStrong,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(c.accentSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(7.dp))
                Text(desc, color = c.text, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Contoh: $example",
                    color = c.text3,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/* ============================================================
   13. Tentang
   ============================================================ */

@Composable
private fun AboutModal(state: AppState) {
    val c = CatatanTheme.colors
    val close: () -> Unit = { state.closeModal() }
    val features = listOf(
        Triple(Icons.Filled.Info, "Lokal", "Data tersimpan di perangkat"),
        Triple(Icons.Filled.Refresh, "Offline", "Bekerja tanpa internet"),
        Triple(Icons.Filled.Download, "Bisa diekspor", "JSON & Markdown"),
        Triple(Icons.Filled.Palette, "Personal", "Tanpa akun, tanpa server")
    )

    AppModal(
        icon = Icons.Filled.Info,
        title = "Tentang Catatan",
        onClose = close,
        footer = { ModalCancelButton("Tutup", close) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(parseHex("#5b8cff"), parseHex("#6d5dfc"))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Note,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Catatan", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.W700)
                Text(
                    "Versi ${BuildConfig.VERSION_NAME} · offline-first",
                    color = c.text3,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "Catatan adalah aplikasi menulis pribadi yang cepat, tenang, dan sepenuhnya offline.",
            color = c.text2,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(14.dp))

        features.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowItems.forEach { (featureIcon, title, sub) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(13.dp))
                            .background(c.surface2)
                            .border(1.dp, c.border, RoundedCornerShape(13.dp))
                            .padding(11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                featureIcon,
                                contentDescription = null,
                                tint = c.accentStrong,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                title,
                                color = c.text,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.W600,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(sub, color = c.text3, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

/* ============================================================
   14. Impor backup
   ============================================================ */

@Composable
private fun ImportModeModal(state: AppState, modal: Modal.ImportMode) {
    val c = CatatanTheme.colors
    val s = modal.summary
    val close: () -> Unit = { state.closeModal() }

    AppModal(
        icon = Icons.Filled.Upload,
        title = "Impor backup",
        subtitle = modal.fileName,
        onClose = close,
        footer = { ModalCancelButton("Batal", close) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                s.total to "catatan",
                s.favorites to "favorit",
                s.categories to "folder",
                s.revisions to "versi"
            ).forEach { (value, label) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .padding(vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        value.toString(),
                        color = c.accentStrong,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W700
                    )
                    Text(label, color = c.text3, fontSize = 10.5.sp)
                }
            }
        }

        Spacer(Modifier.height(13.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.dangerSoft)
                .border(1.dp, c.danger.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
                .padding(11.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = c.danger,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "Data saat ini akan diganti sepenuhnya jika memilih \"Ganti semua\". " +
                    "Pastikan kamu sudah membuat backup.",
                color = c.text2,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(13.dp))

        MenuItem(Icons.Filled.ContentCopy, "Gabungkan") {
            close()
            state.applyImport(s, "merge")
        }
        Text(
            "Tambahkan catatan dari file. Catatan dengan ID sama akan diperbarui.",
            color = c.text3,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = 53.dp, end = 8.dp, bottom = 8.dp)
        )
        MenuItem(Icons.Filled.Refresh, "Ganti semua", danger = true) {
            state.confirm(
                "Ganti semua data?",
                "Seluruh catatan, folder, pengaturan, dan riwayat versi di perangkat ini " +
                    "akan diganti dengan isi backup.",
                "Ya, ganti semua",
                true
            ) { state.applyImport(s, "replace") }
        }
        Text(
            "Hapus data saat ini lalu pulihkan isi backup.",
            color = c.text3,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = 53.dp, end = 8.dp, bottom = 10.dp)
        )

        Text(
            "File backup berisi ${s.total} catatan, ${s.archived} diarsipkan, " +
                "dan ${s.trash} di sampah.",
            color = c.text3,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
    }
}

/* ============================================================
   15. Pembaruan tersedia
   ============================================================ */

@Composable
private fun UpdateInfoModal(state: AppState) {
    val c = CatatanTheme.colors
    val info = state.updateInfo
    val close: () -> Unit = { state.closeModal() }

    val releaseDate: String = runCatching {
        val raw = info?.publishedAt.orEmpty()
        if (raw.isBlank()) "—"
        else fmtDate(java.time.Instant.parse(raw).toEpochMilli())
    }.getOrNull() ?: "—"

    AppModal(
        icon = Icons.Filled.Download,
        title = "Pembaruan tersedia",
        subtitle = info?.let { "Versi ${it.version} sudah tersedia" } ?: "Versi baru sudah tersedia",
        onClose = close,
        footer = {
            ModalFooterButtons(
                confirmText = "Perbarui",
                confirmType = BtnType.PRIMARY,
                onConfirm = {
                    state.startUpdate()
                    state.closeModal()
                },
                cancelText = "Nanti",
                onCancel = close
            )
        }
    ) {
        ReleaseRow(label = "Tanggal rilis", value = releaseDate)
        AppDivider()
        ReleaseRow(
            label = "Catatan rilis",
            value = info?.notes?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Tidak ada catatan rilis."
        )
        AppDivider()
        ReleaseRow(label = "Berkas", value = info?.apkName?.takeIf { it.isNotBlank() } ?: "—")

        val progress = state.downloadProgress
        if (progress != null) {
            Spacer(Modifier.height(14.dp))
            ThinProgress(progress, large = true)
            Spacer(Modifier.height(7.dp))
            Text(
                "${(progress * 100).toInt()}%",
                color = c.accentStrong,
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ReleaseRow(label: String, value: String) {
    val c = CatatanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
    ) {
        Text(label, color = c.text3, fontSize = 11.sp, fontWeight = FontWeight.W600)
        Spacer(Modifier.height(3.dp))
        Text(value, color = c.text, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

/* ============================================================
   Pratinjau isi versi lama (baca saja)
   ============================================================ */

@Composable
private fun PreviewBlockList(blocks: List<Block>, plainTextColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is Block.Paragraph -> Text(
                    block.text, color = plainTextColor,
                    fontSize = 14.sp, lineHeight = 21.sp
                )
                is Block.Heading -> Text(
                    block.text, color = plainTextColor,
                    fontSize = when (block.level) { 1 -> 20.sp; 2 -> 17.sp; else -> 15.sp },
                    fontWeight = FontWeight.W700
                )
                is Block.Quote -> Text(
                    block.text, color = plainTextColor,
                    fontSize = 14.sp, fontStyle = FontStyle.Italic
                )
                is Block.Code -> Text(
                    block.text, color = plainTextColor,
                    fontSize = 12.5.sp, fontFamily = FontFamily.Monospace
                )
                is Block.Checklist -> block.items.forEach { item ->
                    Text(
                        (if (item.done) "✓ " else "○ ") + item.text,
                        color = plainTextColor, fontSize = 13.sp
                    )
                }
                is Block.BulletList -> block.items.forEach { item ->
                    Text("• ${item.text}", color = plainTextColor, fontSize = 13.sp)
                }
                is Block.NumberedList -> block.items.forEachIndexed { i, item ->
                    Text("${i + 1}. ${item.text}", color = plainTextColor, fontSize = 13.sp)
                }
                is Block.Divider -> AppDivider()
                is Block.Image -> Text(
                    "[Gambar]", color = plainTextColor.copy(alpha = 0.6f), fontSize = 12.sp
                )
                is Block.Audio -> Text(
                    "[Rekaman audio]", color = plainTextColor.copy(alpha = 0.6f), fontSize = 12.sp
                )
                is Block.Table -> block.rows.forEach { row ->
                    Text(row.joinToString("  |  "), color = plainTextColor, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
