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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.data.Category
import com.catatan.app.data.Query
import com.catatan.app.ui.theme.CatatanTheme
import com.catatan.app.ui.theme.parseHex

/** Halaman kelola folder. */
@Composable
fun FoldersScreen(state: AppState) {
    val c = CatatanTheme.colors
    val store by state.repo.store.collectAsState()
    val folders = store.categories
    val counts = remember(store.notes) { Query.folderCounts(store.notes) }
    val withoutFolder = remember(store.notes) {
        store.notes.count { !it.isDeleted && !it.isArchived && it.categoryId == null }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CatatanTopBar(
            title = "Folder",
            subtitle = "${folders.size} folder · ${counts.values.sum()} catatan terorganisir",
            actions = {
                AppIconButton(
                    icon = Icons.Filled.Search,
                    contentDescription = "Cari",
                    onClick = { state.push(Screen.Search) }
                )
                AppIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Folder baru",
                    onClick = { state.openModal(Modal.FolderForm(null)) }
                )
                AppIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "Menu halaman",
                    onClick = { state.openModal(Modal.PageMenu) }
                )
            }
        )

        if (folders.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.FolderOpen,
                title = "Belum ada folder",
                text = "Kelompokkan catatan berdasarkan pekerjaan, ide, atau topik favoritmu.",
                actionText = "Buat Folder",
                onAction = { state.openModal(Modal.FolderForm(null)) }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)) {
                    Text("Semua folder", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.W700)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${folders.size} folder · ${counts.values.sum()} catatan terorganisir",
                        color = c.text3,
                        fontSize = 12.sp
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(folders, key = { it.id }) { folder ->
                        FolderCard(
                            folder = folder,
                            count = counts[folder.id] ?: 0,
                            onOpen = { state.push(Screen.Folder(folder.id)) },
                            onMenu = { state.openModal(Modal.FolderMenu(folder.id)) }
                        )
                    }
                    item(key = "new-folder") {
                        NewFolderCard(onClick = { state.openModal(Modal.FolderForm(null)) })
                    }
                }

                if (withoutFolder > 0) {
                    Text(
                        "$withoutFolder catatan belum masuk folder. Kamu bisa memindahkannya lewat menu catatan.",
                        color = c.text3,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(folder: Category, count: Int, onOpen: () -> Unit, onMenu: () -> Unit) {
    val c = CatatanTheme.colors
    val tint = parseHex(folder.color)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        folderIconVector(folder.icon),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                AppIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "Menu folder",
                    onClick = onMenu,
                    size = 30.dp
                )
            }
            Spacer(Modifier.height(11.dp))
            Text(
                folder.name,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text("$count catatan", color = c.text3, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun NewFolderCard(onClick: () -> Unit) {
    val c = CatatanTheme.colors
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2.copy(alpha = 0.5f))
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .height(126.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CreateNewFolder,
                contentDescription = null,
                tint = c.accentStrong,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(9.dp))
        Text("Folder Baru", color = c.accentStrong, fontSize = 13.sp, fontWeight = FontWeight.W600)
    }
}
