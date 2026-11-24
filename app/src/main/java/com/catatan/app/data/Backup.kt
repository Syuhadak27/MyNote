package com.catatan.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/* ============================================================
   Backup — ekspor/impor JSON (kompatibel versi web) + Markdown
   ============================================================ */

object Backup {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
        coerceInputValues = true
    }

    private const val APP_ID = "Catatan Premium"
    private const val BACKUP_VERSION = 2
    private const val APP_VERSION = "2.0.0"

    /* ----------------------------------------------------------
       Ekspor
       ---------------------------------------------------------- */

    fun build(store: Store): BackupFile {
        val rows = settingsRows(store.settings)
        return BackupFile(
            app = APP_ID,
            version = BACKUP_VERSION,
            appVersion = APP_VERSION,
            exportedAt = java.time.Instant.now().toString(),
            counts = BackupCounts(
                notes = store.notes.size,
                categories = store.categories.size,
                revisions = store.revisions.size,
                settings = rows.size
            ),
            data = BackupData(
                notes = store.notes,
                categories = store.categories,
                settings = rows,
                revisions = store.revisions
            )
        )
    }

    fun encode(file: BackupFile): String =
        json.encodeToString(BackupFile.serializer(), file)

    fun fileName(): String {
        val now = LocalDateTime.now()
        val stamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
        return "catatan-backup-$stamp.json"
    }

    fun markdownFileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return "catatan-$stamp.md"
    }

    private fun settingsRows(s: AppSettings): List<BackupSettingRow> = listOf(
        BackupSettingRow("theme", JsonPrimitive(s.theme)),
        BackupSettingRow("accent", JsonPrimitive(s.accent)),
        BackupSettingRow("viewMode", JsonPrimitive(s.viewMode)),
        BackupSettingRow("sortBy", JsonPrimitive(s.sortBy)),
        BackupSettingRow("groupByDate", JsonPrimitive(s.groupByDate)),
        BackupSettingRow("fontSize", JsonPrimitive(s.fontSize)),
        BackupSettingRow("editorFont", JsonPrimitive(s.editorFont)),
        BackupSettingRow("haptics", JsonPrimitive(s.haptics)),
        BackupSettingRow("confirmDelete", JsonPrimitive(s.confirmDelete)),
        BackupSettingRow("swipeActions", JsonPrimitive(s.swipeActions)),
        BackupSettingRow("autoPurgeTrash", JsonPrimitive(s.autoPurgeTrash)),
        BackupSettingRow("moveChecked", JsonPrimitive(s.moveChecked)),
        BackupSettingRow("spellcheck", JsonPrimitive(s.spellcheck)),
        BackupSettingRow("enterCreatesBlock", JsonPrimitive(s.enterCreatesBlock)),
        BackupSettingRow("lastBackupAt", JsonPrimitive(s.lastBackupAt)),
        BackupSettingRow(
            "recentSearches",
            JsonArray(s.recentSearches.map { JsonPrimitive(it) })
        )
    )

    /* ----------------------------------------------------------
       Impor
       ---------------------------------------------------------- */

    /** Baca & validasi; melempar IllegalArgumentException berbahasa Indonesia. */
    fun decode(text: String): BackupFile {
        val parsed = runCatching { json.parseToJsonElement(text) }.getOrElse {
            throw IllegalArgumentException("File JSON rusak atau tidak dapat dibaca.")
        }
        val obj = parsed as? kotlinx.serialization.json.JsonObject
            ?: throw IllegalArgumentException("File bukan objek JSON yang valid.")

        val app = obj["app"]?.let { (it as? JsonPrimitive)?.content }
        if (app != APP_ID && app != "Catatan")
            throw IllegalArgumentException("File ini bukan backup aplikasi Catatan.")

        val version = (obj["version"] as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("Versi backup tidak dikenali.")

        val data = obj["data"] as? kotlinx.serialization.json.JsonObject
            ?: throw IllegalArgumentException("Struktur data backup tidak dikenali.")

        val notesEl = data["notes"] as? JsonArray
            ?: throw IllegalArgumentException("Data catatan pada backup tidak valid.")
        val catsEl = data["categories"] as? JsonArray
            ?: throw IllegalArgumentException("Struktur data backup tidak dikenali.")
        val settingsEl = data["settings"] as? JsonArray
            ?: throw IllegalArgumentException("Struktur data backup tidak dikenali.")
        val revsEl = data["revisions"] as? JsonArray

        val file = runCatching {
            json.decodeFromString(BackupFile.serializer(), text)
        }.getOrElse {
            throw IllegalArgumentException("Data catatan pada backup tidak valid.")
        }

        if (file.data.notes.any { it.id.isBlank() })
            throw IllegalArgumentException("Ditemukan catatan tanpa ID yang valid pada backup.")
        if (file.data.categories.any { it.id.isBlank() })
            throw IllegalArgumentException("Struktur data backup tidak dikenali.")
        if (revsEl != null && file.data.revisions.any { it.id.isBlank() })
            throw IllegalArgumentException("Struktur data backup tidak dikenali.")

        // bersihkan isi HTML agar aman
        return file.copy(
            version = version,
            data = file.data.copy(
                notes = file.data.notes.map {
                    it.copy(content = HtmlCodec.clean(it.content))
                }
            ),
            counts = BackupCounts(
                notes = notesEl.size,
                categories = catsEl.size,
                revisions = revsEl?.size ?: 0,
                settings = settingsEl.size
            )
        )
    }

    fun summarize(file: BackupFile): BackupSummary {
        val notes = file.data.notes
        return BackupSummary(
            total = notes.size,
            active = notes.count { !it.isDeleted && !it.isArchived },
            archived = notes.count { it.isArchived && !it.isDeleted },
            trash = notes.count { it.isDeleted },
            favorites = notes.count { it.isFavorite && !it.isDeleted },
            pinned = notes.count { it.isPinned && !it.isDeleted && !it.isArchived },
            withImages = notes.count { HtmlCodec.hasImage(it.content) },
            categories = file.data.categories.size,
            settings = file.data.settings.size,
            revisions = file.data.revisions.size,
            exportedAt = file.exportedAt,
            version = file.version,
            file = file
        )
    }

    /** Terapkan impor. mode: "merge" | "replace" */
    fun toStore(file: BackupFile, mode: String, existing: Store): Store {
        val settings = settingsFromRows(file.data.settings, existing.settings)
        val incoming = Store(
            notes = file.data.notes.map { normalizeImported(it) },
            categories = file.data.categories,
            revisions = file.data.revisions,
            settings = settings
        )
        if (mode == "replace") return incoming

        val noteMap = incoming.notes.associateBy { it.id }
        val catMap = incoming.categories.associateBy { it.id }
        val revMap = incoming.revisions.associateBy { it.id }
        return Store(
            notes = incoming.notes.filter { n -> existing.notes.none { it.id == n.id } } +
                existing.notes.map { noteMap[it.id] ?: it },
            categories = incoming.categories.filter { c -> existing.categories.none { it.id == c.id } } +
                existing.categories.map { catMap[it.id] ?: it },
            revisions = incoming.revisions.filter { r -> existing.revisions.none { it.id == r.id } } +
                existing.revisions.map { revMap[it.id] ?: it },
            settings = settings
        )
    }

    private fun normalizeImported(n: Note): Note {
        val content = HtmlCodec.clean(n.content)
        val plain = HtmlCodec.htmlToText(content)
        return n.copy(
            content = content,
            plainText = plain,
            tags = n.tags.map { it.trim().removePrefix("#").lowercase() }
                .filter { it.isNotEmpty() }.distinct().take(30),
            paper = if (n.paper in setOf("blank", "lines", "grid", "dots")) n.paper else "blank",
            font = n.font?.takeIf { it in setOf("sans", "serif", "mono") },
            wordCount = HtmlCodec.countWords(plain)
        )
    }

    private fun settingsFromRows(
        rows: List<BackupSettingRow>,
        fallback: AppSettings
    ): AppSettings {
        val map = rows.associate { it.key to it.value }

        fun raw(key: String): String? = (map[key] as? JsonPrimitive)?.content
        fun bool(key: String, def: Boolean): Boolean =
            (map[key] as? JsonPrimitive)?.booleanOrNull ?: def
        fun int(key: String, def: Int): Int =
            (map[key] as? JsonPrimitive)?.intOrNull ?: def
        fun pick(key: String, def: String, allowed: Set<String>): String =
            raw(key)?.takeIf { it in allowed } ?: def

        val recent = (map["recentSearches"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?.filter { it.isNotBlank() }
            ?.take(8) ?: fallback.recentSearches

        return AppSettings(
            theme = pick("theme", fallback.theme, setOf("light", "dark", "system")),
            accent = raw("accent")
                ?.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: fallback.accent,
            viewMode = pick("viewMode", fallback.viewMode, setOf("list", "grid")),
            sortBy = pick("sortBy", fallback.sortBy,
                setOf("updatedAt", "createdAt", "title", "oldest")),
            groupByDate = bool("groupByDate", fallback.groupByDate),
            fontSize = int("fontSize", fallback.fontSize).coerceIn(14, 24),
            editorFont = pick("editorFont", fallback.editorFont,
                setOf("sans", "serif", "mono")),
            haptics = bool("haptics", fallback.haptics),
            confirmDelete = bool("confirmDelete", fallback.confirmDelete),
            swipeActions = bool("swipeActions", fallback.swipeActions),
            autoPurgeTrash = bool("autoPurgeTrash", fallback.autoPurgeTrash),
            moveChecked = bool("moveChecked", fallback.moveChecked),
            spellcheck = bool("spellcheck", fallback.spellcheck),
            enterCreatesBlock = bool("enterCreatesBlock", fallback.enterCreatesBlock),
            lastBackupAt = raw("lastBackupAt")?.toLongOrNull() ?: fallback.lastBackupAt,
            recentSearches = recent
        )
    }

    /* ----------------------------------------------------------
       Ekspor Markdown
       ---------------------------------------------------------- */

    fun toMarkdown(notes: List<Note>, categories: List<Category>): String {
        val catName = categories.associate { it.id to it.name }
        return notes.joinToString("\n\n---\n\n") { note ->
            buildString {
                append("# ").append(note.title.ifBlank { "Tanpa judul" }).append('\n')
                val meta = buildList {
                    note.categoryId?.let { catName[it]?.let { n -> add("Folder: $n") } }
                    val tags = HtmlCodec.noteTags(note)
                    if (tags.isNotEmpty()) add("Tag: " + tags.joinToString(", ") { "#$it" })
                    add("Dibuat: " + com.catatan.app.util.fmtDate(note.createdAt))
                    add("Diperbarui: " + com.catatan.app.util.fmtDate(note.updatedAt))
                }
                append(meta.joinToString(" · ")).append('\n').append('\n')
                append(htmlToMarkdown(note.content))
            }
        }
    }

    fun htmlToMarkdown(html: String): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        val sb = StringBuilder()

        fun walk(node: Node, listDepth: Int = 0) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is org.jsoup.nodes.Element -> {
                    val tag = node.tagName().lowercase()
                    when (tag) {
                        "br" -> sb.append('\n')
                        "hr" -> sb.append("\n\n---\n\n")
                        "strong", "b" -> {
                            sb.append("**"); node.childNodes().forEach { walk(it, listDepth) }; sb.append("**")
                        }
                        "em", "i" -> {
                            sb.append('*'); node.childNodes().forEach { walk(it, listDepth) }; sb.append('*')
                        }
                        "u", "ins" -> {
                            sb.append("__"); node.childNodes().forEach { walk(it, listDepth) }; sb.append("__")
                        }
                        "s", "del", "strike" -> {
                            sb.append("~~"); node.childNodes().forEach { walk(it, listDepth) }; sb.append("~~")
                        }
                        "code" -> {
                            sb.append('`'); node.childNodes().forEach { walk(it, listDepth) }; sb.append('`')
                        }
                        "pre" -> {
                            sb.append("\n\n```\n").append(node.wholeText().trim()).append("\n```\n\n")
                        }
                        "a" -> {
                            val href = node.attr("href")
                            sb.append('[')
                            node.childNodes().forEach { walk(it, listDepth) }
                            sb.append("](").append(href).append(')')
                        }
                        "img" -> {
                            val alt = node.attr("alt").ifBlank { "gambar" }
                            val src = node.attr("src")
                            if (src.startsWith("data:")) sb.append("![$alt](gambar)")
                            else sb.append("![$alt]($src)")
                        }
                        "audio" -> sb.append("\n\n[rekaman audio]\n\n")
                        "h1", "h2", "h3", "h4", "h5", "h6" -> {
                            val level = tag[1].digitToInt().coerceAtMost(6)
                            sb.append("\n\n").append("#".repeat(level)).append(' ')
                            node.childNodes().forEach { walk(it, listDepth) }
                            sb.append("\n\n")
                        }
                        "blockquote" -> {
                            sb.append("\n\n")
                            node.childNodes().forEach { walk(it, listDepth) }
                            sb.append("\n\n")
                        }
                        "li" -> {
                            val input = node.selectFirst("input[type=checkbox]")
                            sb.append("\n").append(" ".repeat(listDepth * 2))
                            if (input != null) {
                                sb.append(if (input.hasAttr("checked")) "- [x] " else "- [ ] ")
                                val clone = node.clone()
                                clone.select("input").forEach { it.remove() }
                                clone.childNodes().forEach { walk(it, listDepth) }
                            } else {
                                sb.append("- ")
                                node.childNodes().forEach { walk(it, listDepth) }
                            }
                        }
                        "ol" -> {
                            var i = 1
                            for (child in node.children()) {
                                if (child.tagName() == "li") {
                                    sb.append("\n").append(" ".repeat(listDepth * 2))
                                    sb.append("$i. ")
                                    child.childNodes().forEach { walk(it, listDepth + 1) }
                                    i++
                                }
                            }
                        }
                        "ul" -> {
                            for (child in node.children()) {
                                if (child.tagName() == "li") walk(child, listDepth)
                            }
                        }
                        "table" -> {
                            sb.append("\n\n")
                            for (tr in node.select("tr")) {
                                val cells = tr.children().map { it.text().trim() }
                                sb.append("| ").append(cells.joinToString(" | ")).append(" |\n")
                            }
                            sb.append("\n\n")
                        }
                        "p", "div", "figure", "figcaption" -> {
                            node.childNodes().forEach { walk(it, listDepth) }
                            sb.append("\n\n")
                        }
                        "script", "style", "template" -> Unit
                        else -> node.childNodes().forEach { walk(it, listDepth) }
                    }
                }
            }
        }

        doc.body().childNodes().forEach { walk(it) }
        return sb.toString()
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
