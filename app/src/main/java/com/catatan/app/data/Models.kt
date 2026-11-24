package com.catatan.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/* ============================================================
   Model data — kontrak sama dengan versi web (normalizeNote)
   ============================================================ */

@Serializable
data class Note(
    val id: String,
    val title: String = "",
    val content: String = "",          // HTML (sumber kebenaran isi catatan)
    val plainText: String = "",
    val categoryId: String? = null,
    val tags: List<String> = emptyList(),
    val color: String? = null,         // warna kertas, null = tanpa warna
    val paper: String = "blank",       // blank | lines | grid | dots
    val font: String? = null,          // sans | serif | mono
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val isLocked: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    val revAt: Long = 0,
    val wordCount: Int = 0
) {
    val isTrashed: Boolean get() = isDeleted
}

@Serializable
data class Category(
    val id: String,
    val name: String = "Folder",
    val color: String = "#5b7cfa",
    val icon: String = "folder",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class Revision(
    val id: String,
    val noteId: String,
    val title: String = "",
    val content: String = "",
    val plainText: String = "",
    val wordCount: Int = 0,
    val createdAt: Long = 0
)

@Serializable
data class AppSettings(
    val theme: String = "system",          // light | dark | system
    val accent: String = "#5b7cfa",
    val viewMode: String = "list",         // list | grid
    val sortBy: String = "updatedAt",      // updatedAt | createdAt | title | oldest
    val groupByDate: Boolean = true,
    val fontSize: Int = 17,                // 14..24
    val editorFont: String = "sans",       // sans | serif | mono
    val haptics: Boolean = true,
    val confirmDelete: Boolean = true,
    val swipeActions: Boolean = true,
    val autoPurgeTrash: Boolean = true,
    val moveChecked: Boolean = false,
    val spellcheck: Boolean = true,
    val enterCreatesBlock: Boolean = false,
    val lastBackupAt: Long = 0,
    val recentSearches: List<String> = emptyList()
)

/** Seluruh isi penyimpanan (pengganti IndexedDB). */
@Serializable
data class Store(
    val notes: List<Note> = emptyList(),
    val categories: List<Category> = emptyList(),
    val revisions: List<Revision> = emptyList(),
    val settings: AppSettings = AppSettings()
)

/* ============================================================
   Format backup — kompatibel dengan versi web
   ============================================================ */

@Serializable
data class BackupCounts(
    val notes: Int = 0,
    val categories: Int = 0,
    val revisions: Int = 0,
    val settings: Int = 0
)

@Serializable
data class BackupSettingRow(
    val key: String,
    val value: JsonElement? = null
)

@Serializable
data class BackupData(
    val notes: List<Note> = emptyList(),
    val categories: List<Category> = emptyList(),
    val settings: List<BackupSettingRow> = emptyList(),
    val revisions: List<Revision> = emptyList()
)

@Serializable
data class BackupFile(
    val app: String = "Catatan Premium",
    val version: Int = 2,
    val appVersion: String = "2.0.0",
    val exportedAt: String = "",
    val counts: BackupCounts = BackupCounts(),
    val data: BackupData = BackupData()
)

/** Hasil ringkasan backup untuk dialog impor. */
data class BackupSummary(
    val total: Int,
    val active: Int,
    val archived: Int,
    val trash: Int,
    val favorites: Int,
    val pinned: Int,
    val withImages: Int,
    val categories: Int,
    val settings: Int,
    val revisions: Int,
    val exportedAt: String,
    val version: Int,
    val file: BackupFile
)
