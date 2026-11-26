package com.catatan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.data.Backup
import com.catatan.app.sync.SyncManager
import com.catatan.app.ui.theme.CatatanTheme
import com.catatan.app.ui.theme.parseHex
import com.catatan.app.util.fmtDateTime
import com.catatan.app.util.formatBytes

/** Warna aksen bawaan (sama dengan ACCENTS pada versi web). */
private val ACCENT_COLORS = listOf(
    "#5b7cfa", "#8b5cf6", "#ec4899", "#ef4444",
    "#f59e0b", "#10b981", "#06b6d4", "#64748b"
)

/* ============================================================
   Layar Pengaturan
   ============================================================ */

@Composable
fun SettingsScreen(state: AppState) {
    val c = CatatanTheme.colors
    val haptics = LocalHapticFeedback.current
    val store by state.repo.store.collectAsState()
    val settings = store.settings
    val backup = rememberBackupActions(state)
    val user by state.authManager.user.collectAsState()
    val syncState by state.syncManager.state.collectAsState()

    val syncStatus = when (syncState) {
        SyncManager.SyncState.Syncing -> "Sedang sinkronisasi…"
        is SyncManager.SyncState.Success ->
            "Terakhir sinkronisasi: " + fmtDateTime((syncState as SyncManager.SyncState.Success).at)
        is SyncManager.SyncState.Error ->
            "Sinkronisasi gagal: " + (syncState as SyncManager.SyncState.Error).message
        SyncManager.SyncState.Idle ->
            if (state.autoSync) "Sinkronisasi otomatis aktif" else "Sinkronisasi manual"
    }

    var refreshTick by remember { mutableIntStateOf(0) }
    val (usedBytes, totalBytes) = remember(refreshTick) { state.storageInfo() }
    val tenths = if (totalBytes > 0) usedBytes * 1000 / totalBytes else 0L
    val percentText = "${tenths / 10}.${tenths % 10}"
    val progress = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f

    val themeLabel = when (settings.theme) {
        "light" -> "Terang"
        "dark" -> "Gelap"
        else -> "Sistem"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        /* ---------------- App bar ---------------- */
        CatatanTopBar(
            title = "Pengaturan",
            subtitle = "Sesuaikan pengalaman menulismu",
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

        /* ---------------- Konten ---------------- */
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 30.dp)
        ) {
            /* ---------------- 0. Akun & sinkronisasi ---------------- */
            SectionLabel("Akun & sinkronisasi")
            SettingsCard {
                if (user != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(c.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = c.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    user?.email ?: "Akun Google",
                                    color = c.text,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.W600,
                                    maxLines = 1
                                )
                                Text(
                                    "Terhubung · data per akun dipisah",
                                    color = c.text3,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(11.dp))
                        Text(
                            syncStatus,
                            color = if (syncState is SyncManager.SyncState.Error) c.danger else c.text3,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(
                                text = "Sinkronkan sekarang",
                                onClick = { state.syncNow() },
                                type = BtnType.PRIMARY,
                                icon = Icons.Filled.Refresh,
                                small = true,
                                enabled = syncState !is SyncManager.SyncState.Syncing
                            )
                            AppButton(
                                text = "Keluar akun",
                                onClick = { state.signOut() },
                                type = BtnType.GHOST,
                                small = true
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        TextActionButton(
                            text = "Hapus data cloud akun ini",
                            accent = false,
                            onClick = {
                                state.confirm(
                                    title = "Hapus data cloud?",
                                    message = "Semua catatan, folder, riwayat, dan media akun ini di cloud akan dihapus. Salinan akun di perangkat ini juga dihapus, lalu kamu keluar dari akun. Data mode lokal tetap aman.",
                                    confirmText = "Hapus data cloud",
                                    danger = true
                                ) { state.deleteCloudData() }
                            }
                        )
                    }
                    SwitchRow(
                        title = "Sinkronisasi otomatis",
                        subtitle = "Saat aplikasi dibuka dan setelah catatan berubah",
                        checked = state.autoSync
                    ) { value -> state.updateAutoSync(value) }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp)
                    ) {
                        Text(
                            "Masuk dengan Google untuk menyinkronkan catatan antarperangkat. " +
                                "Tanpa akun pun aplikasi tetap bisa dipakai penuh.",
                            color = c.text3,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        AppButton(
                            text = "Masuk dengan Google",
                            onClick = { state.signInWithGoogle() },
                            type = BtnType.PRIMARY,
                            icon = Icons.Filled.Person,
                            block = true
                        )
                    }
                }
            }

            /* ---------------- 1. Tampilan ---------------- */
            SectionLabel("Tampilan")
            SettingsCard {
                SegmentedRow(
                    title = "Tema",
                    subtitle = "Saat ini: $themeLabel",
                    options = listOf(
                        "light" to "Terang",
                        "dark" to "Gelap",
                        "system" to "Sistem"
                    ),
                    value = settings.theme
                ) { value ->
                    state.updateSettings { s -> s.copy(theme = value) }
                }

                AccentSwatches(value = settings.accent) { value ->
                    state.updateSettings { s -> s.copy(accent = value) }
                }

                SegmentedRow(
                    title = "Tampilan daftar",
                    subtitle = "Baris ringkas atau kartu",
                    options = listOf("list" to "Daftar", "grid" to "Grid"),
                    value = settings.viewMode
                ) { value ->
                    state.updateSettings { s -> s.copy(viewMode = value) }
                }

                SwitchRow(
                    title = "Kelompokkan per tanggal",
                    subtitle = "Hari ini, kemarin, dan seterusnya",
                    checked = settings.groupByDate
                ) { value ->
                    state.updateSettings { s -> s.copy(groupByDate = value) }
                }
            }

            /* ---------------- 2. Editor ---------------- */
            SectionLabel("Editor")
            SettingsCard {
                SliderRow(
                    title = "Ukuran teks editor",
                    subtitle = "Nyaman untuk membaca dan menulis",
                    value = settings.fontSize,
                    range = 14..24,
                    suffix = "px"
                ) { value ->
                    state.updateSettings { s -> s.copy(fontSize = value) }
                }

                SegmentedRow(
                    title = "Jenis huruf",
                    subtitle = "Huruf bawaan untuk catatan baru",
                    options = listOf(
                        "sans" to "Sans",
                        "serif" to "Serif",
                        "mono" to "Mono"
                    ),
                    value = settings.editorFont
                ) { value ->
                    state.updateSettings { s -> s.copy(editorFont = value) }
                }

                SwitchRow(
                    title = "Pindahkan tugas selesai ke bawah",
                    subtitle = "Saat checklist dicentang",
                    checked = settings.moveChecked
                ) { value ->
                    state.updateSettings { s -> s.copy(moveChecked = value) }
                }

                SwitchRow(
                    title = "Periksa ejaan",
                    subtitle = "Garis bawah pada kata yang salah",
                    checked = settings.spellcheck
                ) { value ->
                    state.updateSettings { s -> s.copy(spellcheck = value) }
                }

                SwitchRow(
                    title = "Entar buat blok baru",
                    subtitle = "Tekan Enter untuk memisah paragraf, bukan baris baru",
                    checked = settings.enterCreatesBlock
                ) { value ->
                    state.updateSettings { s -> s.copy(enterCreatesBlock = value) }
                }
            }

            /* ---------------- 3. Perilaku ---------------- */
            SectionLabel("Perilaku")
            SettingsCard {
                SwitchRow(
                    title = "Getaran halus",
                    subtitle = "Umpan balik saat memilih dan menggeser",
                    checked = settings.haptics
                ) { value ->
                    state.updateSettings { s -> s.copy(haptics = value) }
                    if (value) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }

                SwitchRow(
                    title = "Geser untuk tindakan",
                    subtitle = "Geser kanan untuk pin, kiri untuk hapus",
                    checked = settings.swipeActions
                ) { value ->
                    state.updateSettings { s -> s.copy(swipeActions = value) }
                }

                SwitchRow(
                    title = "Konfirmasi sebelum menghapus",
                    subtitle = "Mencegah terhapus tanpa sengaja",
                    checked = settings.confirmDelete
                ) { value ->
                    state.updateSettings { s -> s.copy(confirmDelete = value) }
                }

                SwitchRow(
                    title = "Bersihkan sampah otomatis",
                    subtitle = "Hapus permanen setelah 30 hari",
                    checked = settings.autoPurgeTrash
                ) { value ->
                    state.updateSettings { s -> s.copy(autoPurgeTrash = value) }
                }
            }

            /* ---------------- 4. Data & cadangan ---------------- */
            SectionLabel("Data & cadangan")
            SettingsCard {
                StorageCard(
                    used = usedBytes,
                    total = totalBytes,
                    percentText = percentText,
                    progress = progress,
                    noteCount = store.notes.size,
                    folderCount = store.categories.size,
                    onRefresh = { refreshTick++ }
                )

                LinkRow(
                    title = "Ekspor backup JSON",
                    subtitle = "Semua catatan, folder, pengaturan, dan riwayat",
                    icon = Icons.Filled.Download
                ) { backup.exportBackup() }

                LinkRow(
                    title = "Ekspor Markdown",
                    subtitle = "File .md yang bisa dibuka di aplikasi lain",
                    icon = Icons.AutoMirrored.Filled.Article
                ) {
                    backup.exportMarkdown(
                        Backup.toMarkdown(
                            state.repo.exportableStore().notes,
                            store.categories
                        )
                    )
                }

                LinkRow(
                    title = "Impor backup",
                    subtitle = "Gabungkan atau ganti data di perangkat ini",
                    icon = Icons.Filled.Upload
                ) { backup.importBackup() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = c.text3,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Terakhir backup: " +
                            (if (settings.lastBackupAt > 0) fmtDateTime(settings.lastBackupAt)
                            else "Belum pernah") +
                            (if (user != null)
                                ". Catatan tersinkron dengan akun ${user?.email}."
                            else ". Data hanya tersimpan di perangkat ini."),
                        color = c.text3,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            /* ---------------- 5. Aplikasi ---------------- */
            SectionLabel("Aplikasi")
            SettingsCard {
                LinkRow(
                    title = "Tentang Catatan",
                    subtitle = "Versi 2.0 · offline-first",
                    icon = Icons.Filled.Info
                ) { state.openModal(Modal.About) }

                LinkRow(
                    title = "Periksa pembaruan",
                    subtitle = "Cari versi terbaru aplikasi",
                    icon = Icons.Filled.Refresh
                ) { state.checkForUpdate(manual = true) }

                LinkRow(
                    title = "Atur ulang tampilan",
                    subtitle = "Kembalikan pengaturan ke bawaan",
                    icon = Icons.Filled.Restore
                ) {
                    state.confirm(
                        title = "Atur ulang tampilan?",
                        message = "Semua pengaturan akan dikembalikan ke bawaan. Catatan dan folder tidak akan terpengaruh.",
                        confirmText = "Atur ulang",
                        danger = false
                    ) {
                        state.repo.resetSettings()
                        state.showToast("Pengaturan diatur ulang")
                    }
                }

                LinkRow(
                    title = "Hapus semua data",
                    subtitle = "Catatan, folder, sampah, dan pengaturan",
                    icon = Icons.Filled.Delete,
                    danger = true
                ) {
                    state.confirm(
                        title = "Hapus semua data?",
                        message = "Catatan, folder, sampah, riwayat versi, dan pengaturan akan dihapus dari perangkat ini. Tindakan ini tidak dapat dibatalkan.",
                        confirmText = "Hapus semua data",
                        danger = true
                    ) {
                        state.repo.clearEverything()
                        state.resetTo(Screen.Notes)
                        state.showToast("Semua data dihapus")
                    }
                }
            }

            /* ---------------- Footer ---------------- */
            Spacer(Modifier.height(26.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accentSoft)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Note,
                        contentDescription = null,
                        tint = c.accentStrong,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Catatan 2.0",
                        color = c.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W700
                    )
                    Text(
                        "Dibuat untuk tulisanmu. Semua data milikmu.",
                        color = c.text3,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }
}

/* ============================================================
   Potongan UI pengaturan
   ============================================================ */

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val c = CatatanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(vertical = 6.dp),
        content = content
    )
}

@Composable
private fun AccentSwatches(value: String, onChange: (String) -> Unit) {
    val c = CatatanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text("Warna aksen", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.W500)
        Text(
            "Warna tombol dan sorotan",
            color = c.text3,
            fontSize = 11.5.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            ACCENT_COLORS.forEach { hex ->
                val active = hex.equals(value, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(parseHex(hex))
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) c.text else c.border,
                            shape = CircleShape
                        )
                        .clickable { onChange(hex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (active) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCard(
    used: Long,
    total: Long,
    percentText: String,
    progress: Float,
    noteCount: Int,
    folderCount: Int,
    onRefresh: () -> Unit
) {
    val c = CatatanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Penyimpanan lokal", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.W500)
                Text(
                    "$percentText% dari ${formatBytes(total)} tersedia",
                    color = c.text3,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                formatBytes(used),
                color = c.accentStrong,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.W600
            )
        }
        Spacer(Modifier.height(10.dp))
        ThinProgress(progress = progress)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$noteCount catatan · $folderCount folder",
                color = c.text3,
                fontSize = 11.5.sp
            )
            TextActionButton("Segarkan", onClick = onRefresh)
        }
    }
}
