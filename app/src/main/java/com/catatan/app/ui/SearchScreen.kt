package com.catatan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.data.Query
import com.catatan.app.data.UiFilters
import com.catatan.app.ui.theme.CatatanTheme
import kotlinx.coroutines.delay

/** Layar pencarian (port dari renderSearch). */
@Composable
fun SearchScreen(state: AppState) {
    val c = CatatanTheme.colors
    val store by state.repo.store.collectAsState()

    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(UiFilters()) }
    var folderId by remember { mutableStateOf<String?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
    }

    // simpan pencarian terakhir (debounce 1,5 dtk)
    LaunchedEffect(query) {
        delay(1500)
        if (query.trim().isNotEmpty()) state.repo.pushRecentSearch(query.trim())
    }

    val categories = store.categories
    val folderMap = remember(categories) { categories.associateBy { it.id } }
    val hasInput = query.isNotBlank() || !filters.isEmpty || folderId != null

    val results = remember(store.notes, query, filters, folderId, categories, hasInput) {
        if (!hasInput) emptyList()
        else Query.search(store.notes, query, filters, folderId, categories).take(200)
    }

    val recent = store.settings.recentSearches

    Column(modifier = Modifier.fillMaxSize()) {
        /* ---------------- Appbar pencarian ---------------- */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg.copy(alpha = 0.92f))
                .statusBarsPadding()
                .height(62.dp)
                .padding(start = 6.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                onClick = { state.pop() }
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.surface2)
                    .border(1.dp, c.border, RoundedCornerShape(11.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = c.text3, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Cari catatan, tag, isi…", color = c.text3, fontSize = 14.sp, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.surface3)
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Hapus pencarian",
                            tint = c.text2,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            TextActionButton("Batal", onClick = { state.pop() })
        }

        /* ---------------- Konten ---------------- */
        if (!hasInput) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 26.dp)
            ) {
                item {
                    Spacer(Modifier.height(26.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(c.accentSofter)
                                .border(1.dp, c.border, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = c.accentStrong,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Cari apa pun", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.W700)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cari judul, isi catatan, tag, atau folder. Gunakan operator untuk pencarian lebih presisi.",
                            color = c.text3,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Pill("Favorit", active = filters.favorite, onClick = { filters = filters.copy(favorite = !filters.favorite) })
                        Pill("Checklist", active = filters.checklist, onClick = { filters = filters.copy(checklist = !filters.checklist) })
                        Pill("Gambar", active = filters.image, onClick = { filters = filters.copy(image = !filters.image) })
                        Pill("Rekaman", active = filters.audio, onClick = { filters = filters.copy(audio = !filters.audio) })
                        Pill("Arsip", active = filters.archived, onClick = { filters = filters.copy(archived = !filters.archived) })
                    }

                    if (recent.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel("Pencarian terakhir")
                            TextActionButton("Hapus", onClick = { state.repo.clearRecentSearches() })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            recent.forEach { term ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(c.surface)
                                        .border(1.dp, c.border, RoundedCornerShape(99.dp))
                                        .clickable { query = term }
                                        .padding(horizontal = 11.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = c.text3, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(term, color = c.text2, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.surface)
                            .border(1.dp, c.border, RoundedCornerShape(14.dp))
                            .clickable { state.openModal(Modal.SearchTips) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(33.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(c.accentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = c.accentStrong, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tips pencarian", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.W600)
                            Text(
                                "Gunakan #tag, is:favorite, has:checklist, before:2026-01-01",
                                color = c.text3,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            KeyboardArrowRight,
                            contentDescription = null,
                            tint = c.text3,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // chip filter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Pill(
                        "Semua",
                        active = filters.isEmpty && folderId == null,
                        leadingIcon = Icons.Filled.Search,
                        onClick = { filters = UiFilters(); folderId = null }
                    )
                    Pill("Favorit", active = filters.favorite, leadingIcon = Icons.Filled.Star, onClick = { filters = filters.copy(favorite = !filters.favorite) })
                    Pill("Checklist", active = filters.checklist, leadingIcon = Icons.Filled.Checklist, onClick = { filters = filters.copy(checklist = !filters.checklist) })
                    Pill("Gambar", active = filters.image, leadingIcon = Icons.Filled.Image, onClick = { filters = filters.copy(image = !filters.image) })
                    Pill("Rekaman", active = filters.audio, leadingIcon = Icons.Filled.Mic, onClick = { filters = filters.copy(audio = !filters.audio) })
                    Pill("Arsip", active = filters.archived, leadingIcon = Icons.Filled.Archive, onClick = { filters = filters.copy(archived = !filters.archived) })
                    Pill("Folder", active = folderId != null, leadingIcon = Icons.Filled.Folder, onClick = { showFolderPicker = true })
                }

                // ringkasan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${results.size} hasil" + if (query.isNotBlank()) " untuk “$query”" else "",
                        color = c.text2,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.W500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!filters.isEmpty || folderId != null) {
                        TextActionButton(
                            "Hapus filter",
                            accent = true,
                            onClick = { filters = UiFilters(); folderId = null }
                        )
                    }
                }

                if (results.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = "Tidak ditemukan",
                        text = "Coba kata kunci lain, periksa ejaan, atau gunakan filter yang lebih luas.",
                        actionText = "Hapus pencarian",
                        onAction = { query = ""; filters = UiFilters(); folderId = null }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 26.dp)
                    ) {
                        item(key = "header") {
                            GroupHeader("Hasil pencarian", results.size)
                        }
                        items(results, key = { it.id }) { note ->
                            val cat = note.categoryId?.let { folderMap[it] }
                            NoteRow(
                                note = note,
                                folderName = cat?.name,
                                folderColor = cat?.color,
                                query = query,
                                selectionMode = false,
                                selected = false,
                                swipeEnabled = store.settings.swipeActions,
                                isTrash = note.isDeleted,
                                onOpen = { state.openNote(note) },
                                onToggleSelect = {},
                                onMenu = { state.openModal(Modal.NoteMenu(note.id)) },
                                onSwipeRight = { state.togglePin(listOf(note.id), !note.isPinned) },
                                onSwipeLeft = {
                                    if (note.isDeleted) state.deletePermanently(listOf(note.id))
                                    else state.trashNotes(listOf(note.id))
                                }
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    // pemilih folder untuk pencarian
    if (showFolderPicker) {
        AppModal(
            icon = Icons.Filled.Folder,
            title = "Cari di folder",
            subtitle = "Pilih folder untuk mempersempit hasil",
            onClose = { showFolderPicker = false }
        ) {
            PickerRow("Semua folder", folderId == null) {
                folderId = null
                showFolderPicker = false
            }
            categories.forEach { cat ->
                PickerRow(cat.name, folderId == cat.id) {
                    folderId = cat.id
                    showFolderPicker = false
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) c.accentStrong else c.text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.W600 else FontWeight.W400,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = c.accentStrong,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
