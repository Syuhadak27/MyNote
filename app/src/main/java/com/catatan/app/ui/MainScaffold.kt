package com.catatan.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.data.HtmlCodec
import com.catatan.app.ui.theme.CatatanTheme
import com.catatan.app.util.formatBytes

/* ============================================================
   Kerangka utama: layar + drawer + tab bar + FAB + banner
   ============================================================ */

@Composable
fun MainScaffold(state: AppState, onRootExit: () -> Unit) {
    val c = CatatanTheme.colors
    val screen = state.screen

    // Sistem back (editor menangani back-nya sendiri di dalam EditorScreen)
    BackHandler(enabled = screen !is Screen.Editor) {
        when {
            state.modal != null -> state.closeModal()
            state.drawerOpen -> state.closeDrawer()
            state.selectionMode -> state.exitSelection()
            state.stack.size > 1 -> state.pop()
            else -> onRootExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .imePadding()
    ) {
        if (screen is Screen.Editor) {
            EditorScreen(state)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (screen) {
                        is Screen.Preview -> PreviewScreen(state)
                        is Screen.Search -> SearchScreen(state)
                        is Screen.Settings -> SettingsScreen(state)
                        is Screen.Folders -> FoldersScreen(state)
                        else -> CollectionScreen(state)
                    }
                }
                if (!state.selectionMode) {
                    TabBar(state)
                }
            }

            val showFab = !state.selectionMode && when (screen) {
                is Screen.Notes, is Screen.Favorites, is Screen.Folder, is Screen.Tag -> true
                else -> false
            }
            if (showFab) Fab(state)
        }

        DrawerHost(state)
        UpdateBanner(state)
        ToastHost(state)
    }

    ModalHost(state)
}

/* ============================================================
   Drawer samping
   ============================================================ */

@Composable
private fun DrawerHost(state: AppState) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.drawerOpen,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { state.closeDrawer() }
            )
        }
        AnimatedVisibility(
            visible = state.drawerOpen,
            enter = slideInHorizontally(tween(230)) { -it } + fadeIn(tween(230)),
            exit = slideOutHorizontally(tween(190)) { -it } + fadeOut(tween(150))
        ) {
            DrawerPanel(state)
        }
    }
}

@Composable
private fun DrawerPanel(state: AppState) {
    val c = CatatanTheme.colors
    val store by state.repo.store.collectAsState()
    val cfg = LocalConfiguration.current
    val width = (cfg.screenWidthDp * 0.86f).dp.coerceAtMost(310.dp)
    val screen = state.screen

    val notes = store.notes
    val activeCount = notes.count { !it.isDeleted && !it.isArchived }
    val favCount = notes.count { it.isFavorite && !it.isDeleted && !it.isArchived }
    val archCount = notes.count { it.isArchived && !it.isDeleted }
    val trashCount = notes.count { it.isDeleted }
    val folders = store.categories
    val folderCounts = remember(notes) { com.catatan.app.data.Query.folderCounts(notes) }
    val tagCounts = remember(notes) {
        val map = linkedMapOf<String, Int>()
        for (n in notes) {
            if (n.isDeleted || n.isArchived) continue
            for (t in HtmlCodec.noteTags(n)) map[t] = (map[t] ?: 0) + 1
        }
        map.entries.sortedByDescending { it.value }.take(10)
    }
    val (used, total) = state.storageInfo()

    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        color = c.bg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 13.dp, end = 13.dp, top = 16.dp, bottom = 16.dp)
        ) {
            /* --- Brand --- */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Note, contentDescription = null, tint = c.accentFg, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Catatan", color = c.text, fontSize = 16.5.sp, fontWeight = FontWeight.W700)
                    Text("Offline · Milikmu", color = c.text3, fontSize = 10.5.sp)
                }
                AppIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Tutup menu",
                    onClick = { state.closeDrawer() },
                    size = 34.dp
                )
            }

            Spacer(Modifier.height(15.dp))

            /* --- Catatan Baru --- */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .clickable {
                        state.closeDrawer()
                        state.newNote()
                    }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = c.accentFg, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text("Catatan Baru", color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.W600)
            }

            /* --- Perpustakaan --- */
            SectionLabel("Perpustakaan")
            DrawerRow(Icons.Filled.Note, "Semua Catatan", activeCount, screen is Screen.Notes) {
                state.resetTo(Screen.Notes)
            }
            DrawerRow(Icons.Filled.Star, "Favorit", favCount, screen is Screen.Favorites) {
                state.resetTo(Screen.Favorites)
            }
            if (archCount > 0) {
                DrawerRow(Icons.Filled.Archive, "Arsip", archCount, screen is Screen.Archive) {
                    state.resetTo(Screen.Archive)
                }
            }
            if (trashCount > 0) {
                DrawerRow(Icons.Filled.Delete, "Sampah", trashCount, screen is Screen.Trash) {
                    state.resetTo(Screen.Trash)
                }
            }

            /* --- Folder --- */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionLabel("Folder")
                }
                AppIconButton(
                    icon = Icons.Filled.CreateNewFolder,
                    contentDescription = "Folder baru",
                    onClick = {
                        state.closeDrawer()
                        state.openModal(Modal.FolderForm(null))
                    },
                    size = 30.dp
                )
            }

            if (folders.isEmpty()) {
                Text(
                    "Buat folder pertama",
                    color = c.accentStrong,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable {
                            state.closeDrawer()
                            state.openModal(Modal.FolderForm(null))
                        }
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                )
            } else {
                folders.forEach { cat ->
                    DrawerFolderRow(
                        name = cat.name,
                        color = cat.color,
                        count = folderCounts[cat.id] ?: 0,
                        active = screen is Screen.Folder && (screen as Screen.Folder).id == cat.id
                    ) {
                        state.closeDrawer()
                        state.push(Screen.Folder(cat.id))
                    }
                }
                DrawerRow(Icons.Filled.GridView, "Kelola folder", null, screen is Screen.Folders) {
                    state.resetTo(Screen.Folders)
                }
            }

            /* --- Tag --- */
            if (tagCounts.isNotEmpty()) {
                SectionLabel("Tag")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tagCounts.forEach { (tag, count) ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(c.surface)
                                .border(1.dp, c.border, RoundedCornerShape(99.dp))
                                .clickable {
                                    state.closeDrawer()
                                    state.push(Screen.Tag(tag))
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#$tag", color = c.text2, fontSize = 11.5.sp, maxLines = 1)
                            Spacer(Modifier.width(5.dp))
                            Text("$count", color = c.text3, fontSize = 10.sp, fontWeight = FontWeight.W600)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            AppDivider()
            Spacer(Modifier.height(6.dp))

            /* --- Pengaturan --- */
            DrawerRow(Icons.Filled.Settings, "Pengaturan", null, screen is Screen.Settings) {
                state.resetTo(Screen.Settings)
            }

            /* --- Penyimpanan --- */
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Penyimpanan", color = c.text3, fontSize = 10.5.sp, fontWeight = FontWeight.W700, letterSpacing = 0.7.sp)
                Spacer(Modifier.weight(1f))
                Text(formatBytes(used), color = c.text3, fontSize = 10.5.sp, fontWeight = FontWeight.W500)
            }
            Spacer(Modifier.height(6.dp))
            ThinProgress(if (total > 0) used.toFloat() / total.toFloat() else 0f)
        }
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    count: Int?,
    active: Boolean,
    onClick: () -> Unit
) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) c.accentStrong else c.text2,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (active) c.accentStrong else c.text,
            fontSize = 13.5.sp,
            fontWeight = if (active) FontWeight.W600 else FontWeight.W500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Text("$count", color = c.text3, fontSize = 11.sp, fontWeight = FontWeight.W500)
        }
    }
}

@Composable
private fun DrawerFolderRow(
    name: String,
    color: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit
) {
    val c = CatatanTheme.colors
    val folderColor = com.catatan.app.ui.theme.parseHex(color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(folderColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = folderColor, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            name,
            color = c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.W500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("$count", color = c.text3, fontSize = 11.sp, fontWeight = FontWeight.W500)
    }
}

/* ============================================================
   Banner pembaruan (GitHub Releases)
   ============================================================ */

@Composable
fun UpdateBanner(state: AppState) {
    if (!state.updateBanner) return
    val info = state.updateInfo ?: return
    val c = CatatanTheme.colors
    val progress = state.downloadProgress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 66.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = c.accentStrong, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Pembaruan tersedia", color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.W700)
                Text(
                    "Versi baru Catatan sudah siap.",
                    color = c.text3,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    ThinProgress(progress)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Mengunduh ${info.version} · ${(progress * 100).toInt()}%",
                        color = c.accentStrong,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.W600
                    )
                } else {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Versi ${info.version} · ${info.apkName}",
                        color = c.text3,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                AppButton(
                    "Perbarui",
                    { state.startUpdate() },
                    BtnType.PRIMARY,
                    small = true,
                    enabled = progress == null
                )
                Spacer(Modifier.height(4.dp))
                AppButton(
                    "Nanti",
                    { state.dismissUpdateBanner() },
                    BtnType.GHOST,
                    small = true,
                    enabled = progress == null
                )
            }
        }
    }
}
