package com.catatan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.data.Category
import com.catatan.app.data.HtmlCodec
import com.catatan.app.data.Note
import com.catatan.app.data.Query
import com.catatan.app.ui.theme.CatatanTheme

private const val PAGE_SIZE = 60
private const val SEARCH_RESULT_LIMIT = 200

/** Layar daftar: notes · favorites · archive · trash · folder · tag */
@Composable
fun CollectionScreen(state: AppState) {
    val c = CatatanTheme.colors
    val haptics = LocalHapticFeedback.current
    val store by state.repo.store.collectAsState()
    val settings = store.settings
    val screen = state.screen

    val view = when (screen) {
        is Screen.Favorites -> "favorites"
        is Screen.Archive -> "archive"
        is Screen.Trash -> "trash"
        is Screen.Folder -> "folder"
        is Screen.Tag -> "tag"
        else -> "notes"
    }
    val folderId = (screen as? Screen.Folder)?.id
    val tag = (screen as? Screen.Tag)?.tag
    val isTrash = view == "trash"

    val folder: Category? = folderId?.let { id -> store.categories.firstOrNull { it.id == id } }

    val base = remember(store.notes, view, folderId, tag) {
        Query.baseNotes(store.notes, view, folderId, tag)
    }
    val quickCounts = remember(base) { Query.quickCounts(base) }

    var quickFilter by remember(screen) { androidx.compose.runtime.mutableStateOf("all") }
    var limit by remember(screen) { mutableIntStateOf(PAGE_SIZE) }

    val filtered = remember(base, quickFilter) { Query.applyQuickFilter(base, quickFilter) }
    val sorted = remember(filtered, settings.sortBy, isTrash) {
        Query.sortNotes(filtered, settings.sortBy, isTrash)
    }
    val shown = sorted.take(limit)
    val groups = remember(shown, settings.groupByDate, isTrash) {
        Query.groupNotes(shown, settings.groupByDate, isTrash)
    }

    val folderCounts = remember(store.notes) { Query.folderCounts(store.notes) }

    /* --- Judul appbar --- */
    val allCount = base.size
    val (barTitle, barSubtitle) = when (screen) {
        is Screen.Notes -> "Semua Catatan" to "$allCount catatan"
        is Screen.Favorites -> "Favorit" to "$allCount catatan favorit"
        is Screen.Archive -> "Arsip" to "$allCount catatan diarsipkan"
        is Screen.Trash -> "Sampah" to "$allCount catatan di sampah"
        is Screen.Folder -> (folder?.name ?: "Folder") to "$allCount catatan"
        is Screen.Tag -> "#$tag" to "$allCount catatan"
        else -> "Catatan" to "$allCount catatan"
    }

    val backView = screen is Screen.Folder || screen is Screen.Tag

    Column(modifier = Modifier.fillMaxSize()) {
        /* ---------------- Appbar ---------------- */
        if (state.selectionMode) {
            CatatanTopBar(
                title = "${state.selected.size} dipilih",
                subtitle = "Pilih tindakan di bawah",
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconButton(
                            icon = Icons.Filled.CheckCircle,
                            contentDescription = "Semua",
                            onClick = { state.selectAll(shown.map { it.id }) }
                        )
                        TextActionButton("Batal", onClick = { state.exitSelection() })
                    }
                }
            )
        } else {
            CatatanTopBar(
                title = barTitle,
                subtitle = barSubtitle,
                navigationIcon = {
                    if (backView) {
                        AppIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            onClick = { state.pop() }
                        )
                    } else {
                        AppIconButton(
                            icon = Icons.Filled.Menu,
                            contentDescription = "Buka menu",
                            onClick = { state.openDrawer() }
                        )
                    }
                },
                actions = {
                    AppIconButton(
                        icon = Icons.Filled.Search,
                        contentDescription = "Cari",
                        onClick = { state.push(Screen.Search) }
                    )
                    AppIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = "Menu halaman",
                        onClick = { state.openModal(Modal.PageMenu) }
                    )
                }
            )
        }

        /* ---------------- Konten ---------------- */
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                allCount == 0 -> EmptyCollection(view, folder?.name, tag, state)

                shown.isEmpty() -> FilterEmpty(quickFilter) {
                    quickFilter = "all"
                    limit = PAGE_SIZE
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    // Banner sampah
                    if (isTrash) {
                        TrashBanner(state)
                    }

                    // Chip filter cepat
                    if (!isTrash && !state.selectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            QuickChip("Semua", quickCounts["all"] ?: 0, quickFilter == "all") {
                                quickFilter = "all"; limit = PAGE_SIZE
                            }
                            QuickChip("Checklist", quickCounts["checklist"] ?: 0, quickFilter == "checklist") {
                                quickFilter = "checklist"; limit = PAGE_SIZE
                            }
                            QuickChip("Gambar", quickCounts["image"] ?: 0, quickFilter == "image") {
                                quickFilter = "image"; limit = PAGE_SIZE
                            }
                            QuickChip("Rekaman", quickCounts["audio"] ?: 0, quickFilter == "audio") {
                                quickFilter = "audio"; limit = PAGE_SIZE
                            }
                        }
                    }

                    // Banner folder
                    if (view == "folder" && folder != null) {
                        FolderBanner(folder, allCount)
                    }

                    // Daftar
                    val categories = store.categories
                    when (settings.viewMode) {
                        "grid" -> GridList(
                            groups = groups,
                            categories = categories,
                            query = "",
                            state = state,
                            isTrash = isTrash,
                            swipeEnabled = settings.swipeActions,
                            limit = limit,
                            total = sorted.size,
                            onLoadMore = { limit += PAGE_SIZE }
                        )
                        else -> ListList(
                            groups = groups,
                            categories = categories,
                            query = "",
                            state = state,
                            isTrash = isTrash,
                            swipeEnabled = settings.swipeActions,
                            limit = limit,
                            total = sorted.size,
                            onLoadMore = { limit += PAGE_SIZE }
                        )
                    }
                }
            }
        }

        /* ---------------- Bar seleksi (menggantikan tab bar) ---------------- */
        if (state.selectionMode) {
            SelectionBar(state)
        }
    }
}

/* ============================================================
   Daftar mode list
   ============================================================ */

private sealed interface RowEntry {
    data class Header(val label: String, val count: Int) : RowEntry
    data class NoteEntry(val note: Note) : RowEntry
    data class CardPair(val notes: List<Note>) : RowEntry
}

@Composable
private fun ListList(
    groups: List<Pair<String, List<Note>>>,
    categories: List<Category>,
    query: String,
    state: AppState,
    isTrash: Boolean,
    swipeEnabled: Boolean,
    limit: Int,
    total: Int,
    onLoadMore: () -> Unit
) {
    val entries = remember(groups) {
        buildList {
            groups.forEach { (label, notes) ->
                add(RowEntry.Header(label, notes.size))
                notes.forEach { add(RowEntry.NoteEntry(it)) }
            }
        }
    }
    val catMap = remember(categories) { categories.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 26.dp)
    ) {
        itemsIndexed(entries, key = { _, e ->
            when (e) {
                is RowEntry.Header -> "h:${e.label}"
                is RowEntry.NoteEntry -> e.note.id
                else -> "card"
            }
        }) { _, entry ->
            when (entry) {
                is RowEntry.Header -> GroupHeader(entry.label, entry.count)
                is RowEntry.CardPair -> Unit
                is RowEntry.NoteEntry -> {
                    val note = entry.note
                    val cat = note.categoryId?.let { catMap[it] }
                    Spacer(Modifier.height(3.dp))
                    NoteRow(
                        note = note,
                        folderName = cat?.name,
                        folderColor = cat?.color,
                        query = query,
                        selectionMode = state.selectionMode,
                        selected = state.selected.contains(note.id),
                        swipeEnabled = swipeEnabled,
                        isTrash = isTrash,
                        onOpen = { state.openNote(note) },
                        onToggleSelect = { state.toggleSelect(note.id) },
                        onMenu = { state.openModal(Modal.NoteMenu(note.id)) },
                        onSwipeRight = {
                            val ids = listOf(note.id)
                            state.togglePin(ids, !note.isPinned)
                        },
                        onSwipeLeft = {
                            if (isTrash) state.deletePermanently(listOf(note.id))
                            else state.trashNotes(listOf(note.id))
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        if (limit < total) {
            item(key = "load-more") {
                LoadMoreButton(total - limit, onLoadMore)
            }
        }
    }
}

/* ============================================================
   Daftar mode grid
   ============================================================ */

@Composable
private fun GridList(
    groups: List<Pair<String, List<Note>>>,
    categories: List<Category>,
    query: String,
    state: AppState,
    isTrash: Boolean,
    swipeEnabled: Boolean,
    limit: Int,
    total: Int,
    onLoadMore: () -> Unit
) {
    val rows = remember(groups) {
        buildList {
            groups.forEach { (label, notes) ->
                add(RowEntry.Header(label, notes.size))
                notes.chunked(2).forEach { pair -> add(RowEntry.CardPair(pair)) }
            }
        }
    }
    val catMap = remember(categories) { categories.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 26.dp)
    ) {
        itemsIndexed(rows, key = { i, e ->
            when (e) {
                is RowEntry.Header -> "h:${e.label}"
                is RowEntry.CardPair -> "p:${e.notes.joinToString { it.id }}"
                else -> "x$i"
            }
        }) { _, entry ->
            when (entry) {
                is RowEntry.Header -> GroupHeader(entry.label, entry.count)
                is RowEntry.CardPair -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    entry.notes.forEach { note ->
                        Box(modifier = Modifier.weight(1f)) {
                            val cat = note.categoryId?.let { catMap[it] }
                            NoteGridCard(
                                note = note,
                                folderName = cat?.name,
                                folderColor = cat?.color,
                                query = query,
                                selectionMode = state.selectionMode,
                                selected = state.selected.contains(note.id),
                                swipeEnabled = swipeEnabled,
                                isTrash = isTrash,
                                onOpen = { state.openNote(note) },
                                onToggleSelect = { state.toggleSelect(note.id) },
                                onMenu = { state.openModal(Modal.NoteMenu(note.id)) },
                                onSwipeRight = { state.togglePin(listOf(note.id), !note.isPinned) },
                                onSwipeLeft = {
                                    if (isTrash) state.deletePermanently(listOf(note.id))
                                    else state.trashNotes(listOf(note.id))
                                }
                            )
                        }
                    }
                    if (entry.notes.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                else -> Unit
            }
        }
        if (limit < total) {
            item(key = "load-more") {
                LoadMoreButton(total - limit, onLoadMore)
            }
        }
    }
}

@Composable
private fun LoadMoreButton(remaining: Int, onLoadMore: () -> Unit) {
    val c = CatatanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        AppButton("Muat $remaining catatan lagi", onLoadMore, BtnType.GHOST, small = true)
    }
}

/* ============================================================
   Komponen pendukung
   ============================================================ */

@Composable
private fun QuickChip(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Pill(label = label, count = count, active = active, onClick = onClick)
}

@Composable
private fun TrashBanner(state: AppState) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(37.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.dangerSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Catatan di sampah", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.W600)
            Text(
                "Otomatis dihapus permanen setelah 30 hari. Kamu masih bisa memulihkannya.",
                color = c.text3,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        AppButton("Kosongkan", { state.emptyTrash() }, BtnType.GHOST, small = true)
    }
}

@Composable
private fun FolderBanner(folder: Category, count: Int) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.accentSofter)
            .border(1.dp, c.border, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(com.catatan.app.ui.theme.parseHex(folder.color).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = com.catatan.app.ui.theme.parseHex(folder.color),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.W700, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count catatan", color = c.text3, fontSize = 11.5.sp)
        }
    }
}

/* ============================================================
   Empty state per halaman
   ============================================================ */

@Composable
private fun EmptyCollection(view: String, folderName: String?, tag: String?, state: AppState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(24.dp))
        when (view) {
            "favorites" -> EmptyState(
                icon = Icons.Filled.Star,
                title = "Belum ada favorit",
                text = "Tandai catatan penting sebagai favorit agar mudah ditemukan lagi.",
                actionText = "Jelajahi catatan",
                onAction = { state.resetTo(Screen.Notes) }
            )
            "archive" -> EmptyState(
                icon = Icons.Filled.Archive,
                title = "Arsip kosong",
                text = "Catatan yang kamu arsipkan akan tersimpan rapi di sini.",
                actionText = "Kembali ke catatan",
                onAction = { state.resetTo(Screen.Notes) }
            )
            "trash" -> EmptyState(
                icon = Icons.Filled.Delete,
                title = "Sampah kosong",
                text = "Catatan yang dihapus akan muncul di sini selama 30 hari sebelum dibersihkan otomatis.",
                actionText = "Kembali ke catatan",
                onAction = { state.resetTo(Screen.Notes) }
            )
            "folder" -> EmptyState(
                icon = Icons.Filled.FolderOpen,
                title = "Folder ${folderName ?: ""} masih kosong".trim(),
                text = "Mulai tulis catatan pertama di folder ini.",
                actionText = "Catatan Baru",
                onAction = { state.newNote((state.screen as? Screen.Folder)?.id) }
            )
            "tag" -> EmptyState(
                icon = Icons.Filled.Tag,
                title = "Tidak ada catatan",
                text = "Belum ada catatan dengan tag #$tag.",
                actionText = "Lihat semua catatan",
                onAction = { state.resetTo(Screen.Notes) }
            )
            else -> EmptyState(
                icon = Icons.Filled.Note,
                title = "Belum ada catatan",
                text = "Tulis ide, pengingat, atau hal penting lainnya. Semua tersimpan di perangkatmu.",
                actionText = "Buat Catatan",
                onAction = { state.newNote() }
            )
        }
    }
}

/* ============================================================
   Empty state filter cepat
   ============================================================ */

@Composable
private fun FilterEmpty(quickFilter: String, onShowAll: () -> Unit) {
    when (quickFilter) {
        "checklist" -> EmptyState(
            icon = Icons.Filled.Checklist,
            title = "Tidak ada checklist",
            text = "Belum ada catatan yang memuat checklist. Tambahkan checklist lewat editor catatan.",
            actionText = "Tampilkan semua",
            onAction = onShowAll
        )
        "image" -> EmptyState(
            icon = Icons.Filled.Image,
            title = "Tidak ada gambar",
            text = "Catatan dengan gambar akan muncul di sini.",
            actionText = "Tampilkan semua",
            onAction = onShowAll
        )
        "audio" -> EmptyState(
            icon = Icons.Filled.Mic,
            title = "Belum ada rekaman",
            text = "Rekam suara langsung dari editor catatan.",
            actionText = "Tampilkan semua",
            onAction = onShowAll
        )
        else -> EmptyState(
            icon = Icons.Filled.Note,
            title = "Tidak ada catatan",
            text = "Belum ada catatan pada filter ini.",
            actionText = "Tampilkan semua",
            onAction = onShowAll
        )
    }
}

/* ============================================================
   Tab bar bawah (mobile)
   ============================================================ */

@Composable
fun TabBar(state: AppState) {
    val c = CatatanTheme.colors
    val store by state.repo.store.collectAsState()

    val currentRoute = state.screen.route
    val activeIndex = when {
        state.screen is Screen.Folders -> 1
        state.screen is Screen.Favorites -> 2
        state.screen is Screen.Settings -> 3
        else -> 0
    }

    val allCount = store.notes.count { !it.isDeleted && !it.isArchived }
    val folderCount = store.categories.size
    val favCount = store.notes.count { it.isFavorite && !it.isDeleted && !it.isArchived }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .height(58.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabItem(Icons.Filled.Note, "Catatan", activeIndex == 0, allCount) {
            state.resetTo(Screen.Notes)
        }
        TabItem(Icons.Filled.Folder, "Folder", activeIndex == 1, folderCount) {
            state.resetTo(Screen.Folders)
        }
        TabItem(Icons.Filled.Star, "Favorit", activeIndex == 2, favCount) {
            state.resetTo(Screen.Favorites)
        }
        TabItem(Icons.Filled.Settings, "Atur", activeIndex == 3, null) {
            state.resetTo(Screen.Settings)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    count: Int?,
    onClick: () -> Unit
) {
    val c = CatatanTheme.colors
    val tint = if (active) c.accentStrong else c.text3
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.W500)
    }
}

/* ============================================================
   FAB
   ============================================================ */

@Composable
fun Fab(state: AppState) {
    val c = CatatanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 58.dp + 16.dp)
                .size(56.dp)
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(c.accent)
                .clickable { state.newNote() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Catatan baru", tint = c.accentFg, modifier = Modifier.size(26.dp))
        }
    }
}

/* ============================================================
   Baris seleksi bawah
   ============================================================ */

@Composable
fun SelectionBar(state: AppState) {
    val c = CatatanTheme.colors
    val store by state.repo.store.collectAsState()
    val ids = state.selected.toList()
    val isTrash = state.screen is Screen.Trash
    val notes = store.notes.filter { it.id in ids }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface.copy(alpha = 0.97f))
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(c.accentSoft)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("${ids.size}", color = c.accentStrong, fontSize = 12.sp, fontWeight = FontWeight.W700)
        }
        Spacer(Modifier.width(4.dp))

        if (isTrash) {
            SelAction(Icons.Filled.Restore, "Pulihkan") { state.restoreNotes(ids) }
            SelAction(Icons.Filled.Delete, "Hapus", danger = true) { state.deletePermanently(ids) }
        } else {
            val allPinned = notes.isNotEmpty() && notes.all { it.isPinned }
            val allFav = notes.isNotEmpty() && notes.all { it.isFavorite }
            val allArchived = notes.isNotEmpty() && notes.all { it.isArchived }
            SelAction(Icons.Filled.PushPin, "Pin") { state.togglePin(ids, !allPinned) }
            SelAction(Icons.Filled.Star, "Favorit") { state.toggleFavorite(ids, !allFav) }
            SelAction(Icons.Filled.Archive, "Arsip") { state.toggleArchive(ids, !allArchived) }
            SelAction(Icons.Filled.Folder, "Pindah") { state.openModal(Modal.MoveToFolder(ids)) }
            SelAction(Icons.Filled.Delete, "Hapus", danger = true) { state.trashNotes(ids) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SelAction(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val c = CatatanTheme.colors
    val tint = if (danger) c.danger else c.text2
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = tint, fontSize = 9.5.sp, fontWeight = FontWeight.W500, maxLines = 1)
    }
}
